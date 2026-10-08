package com.cyclone.sensordisplay.network;

import com.cyclone.sensordisplay.tileentity.TileConnectorWorkbench;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 服务端 -> 客户端：连接器工作台卡 A 的状态快照。
 * 供 GUI 显示绑定状态/模式/名字/目标机器，以及给表达式编辑器提供拍平用的目标 NBT。
 */
public class PacketWorkbenchData implements IMessage {
    private int x, y, z;
    private boolean hasCardA;
    private boolean hasBinding;
    private int mode;
    private String expr = "";
    private String name = "";
    private String targetClass = "";
    private boolean hasNbt;
    private byte[] nbtBytes;
    private boolean sameMachine;

    public PacketWorkbenchData() {}

    /** 服务端：从工作台 tile 组装状态包 */
    public static PacketWorkbenchData build(TileConnectorWorkbench wb) {
        PacketWorkbenchData p = new PacketWorkbenchData();
        p.x = wb.xCoord;
        p.y = wb.yCoord;
        p.z = wb.zCoord;
        net.minecraft.item.ItemStack a = wb.getStackInSlot(0);
        p.hasCardA = (a != null);
        if (p.hasCardA) {
            p.hasBinding = com.cyclone.sensordisplay.util.ConnectorConfig.hasBinding(a);
            p.mode = com.cyclone.sensordisplay.util.ConnectorConfig.getMode(a);
            p.expr = com.cyclone.sensordisplay.util.ConnectorConfig.getExpr(a);
            p.name = com.cyclone.sensordisplay.util.ConnectorConfig.getName(a);
            String cls = wb.targetClassName();
            p.targetClass = cls == null ? "" : cls;
            // 目标机器 NBT：优先用传感器扫描缓存（含蓄水器虚拟键 / IC2 运行时合成键），
            // 供客户端表达式编辑器拍平变量；无缓存回退目标 tile 直写。
            NBTTagCompound nbt = wb.targetNbtOf(a);
            if (nbt != null) {
                try {
                    p.nbtBytes = CompressedStreamTools.compress(nbt);
                    p.hasNbt = true;
                } catch (Exception ignore) {
                    p.hasNbt = false;
                }
            }
            p.sameMachine = wb.sameMachineType();
        }
        return p;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        hasCardA = buf.readBoolean();
        if (hasCardA) {
            hasBinding = buf.readBoolean();
            mode = buf.readByte();
            expr = ByteBufUtils.readUTF8String(buf);
            name = ByteBufUtils.readUTF8String(buf);
            targetClass = ByteBufUtils.readUTF8String(buf);
            hasNbt = buf.readBoolean();
            if (hasNbt) {
                int len = buf.readInt();
                nbtBytes = new byte[len];
                buf.readBytes(nbtBytes);
            }
            sameMachine = buf.readBoolean();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeBoolean(hasCardA);
        if (hasCardA) {
            buf.writeBoolean(hasBinding);
            buf.writeByte(mode);
            ByteBufUtils.writeUTF8String(buf, expr == null ? "" : expr);
            ByteBufUtils.writeUTF8String(buf, name == null ? "" : name);
            ByteBufUtils.writeUTF8String(buf, targetClass == null ? "" : targetClass);
            buf.writeBoolean(hasNbt);
            if (hasNbt && nbtBytes != null) {
                buf.writeInt(nbtBytes.length);
                buf.writeBytes(nbtBytes);
            }
            buf.writeBoolean(sameMachine);
        }
    }

    public static class Handler implements IMessageHandler<PacketWorkbenchData, IMessage> {
        @Override
        public IMessage onMessage(PacketWorkbenchData message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                World world = Minecraft.getMinecraft().theWorld;
                if (world == null) return null;
                TileEntity te = world.getTileEntity(message.x, message.y, message.z);
                if (te instanceof TileConnectorWorkbench) {
                    TileConnectorWorkbench wb = (TileConnectorWorkbench) te;
                    wb.applyClientData(message);
                }
                // 若表达式编辑器正打开，把最新 NBT 转发给它刷新变量列表（保留勾选）
                com.cyclone.sensordisplay.gui.GuiCardConfigEditor.onData(message);
            }
            return null;
        }
    }

    // ===== 客户端 tile 应用 =====

    public boolean getHasCardA() { return hasCardA; }
    public boolean getHasBinding() { return hasBinding; }
    public int getMode() { return mode; }
    public String getExpr() { return expr; }
    public String getName() { return name; }
    public String getTargetClass() { return targetClass; }
    public boolean getHasNbt() { return hasNbt; }
    public byte[] getNbtBytes() { return nbtBytes; }
    public boolean getSameMachine() { return sameMachine; }
}
