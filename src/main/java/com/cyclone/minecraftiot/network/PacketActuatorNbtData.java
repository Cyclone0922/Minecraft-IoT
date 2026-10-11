package com.cyclone.minecraftiot.network;

import com.cyclone.minecraftiot.gui.GuiActuatorExprEditor;
import com.cyclone.minecraftiot.tileentity.TileActuator;
import com.cyclone.minecraftiot.util.SignalValue;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 服务端 -> 客户端：执行器全屏表达式编辑器的数据快照。
 *  - 绑定卡目标机器 NBT（压缩）：供拍平可用变量；
 *  - 6 面信号输入 {inX}（按 buildInValues(side) 语义）：实时值，供编辑器列出"有输入的面"。
 * hasNbt=false 表示无卡或目标无数据。
 */
public class PacketActuatorNbtData implements IMessage {
    private int x, y, z;
    private boolean hasNbt;
    private byte[] nbtBytes;
    private boolean[] inHas = new boolean[6];
    private byte[] inType = new byte[6];
    private double[] inNum = new double[6];
    private boolean[] inBool = new boolean[6];
    private String[] inStr = new String[6];

    public PacketActuatorNbtData() {}

    /** 服务端：从执行器组装（读绑卡目标 NBT + 6 面输入快照） */
    public static PacketActuatorNbtData build(TileActuator act, int side) {
        PacketActuatorNbtData p = new PacketActuatorNbtData();
        p.x = act.xCoord;
        p.y = act.yCoord;
        p.z = act.zCoord;
        NBTTagCompound nbt = act.readTargetNbt();
        if (nbt != null) {
            try {
                p.nbtBytes = CompressedStreamTools.compress(nbt);
                p.hasNbt = true;
            } catch (Exception ignore) {
                p.hasNbt = false;
            }
        }
        SignalValue[] ins = act.getInputSnapshotFor(side);
        if (ins != null) {
            for (int i = 0; i < 6; i++) {
                SignalValue v = ins[i];
                if (v == null) continue;
                p.inHas[i] = true;
                p.inType[i] = (byte) v.type;
                if (v.type == SignalValue.TYPE_DOUBLE) p.inNum[i] = v.num;
                else if (v.type == SignalValue.TYPE_BOOL) p.inBool[i] = v.bool;
                else p.inStr[i] = v.str == null ? "" : v.str;
            }
        }
        return p;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        hasNbt = buf.readBoolean();
        if (hasNbt) {
            int len = buf.readInt();
            nbtBytes = new byte[len];
            buf.readBytes(nbtBytes);
        }
        for (int i = 0; i < 6; i++) {
            inHas[i] = buf.readBoolean();
            if (inHas[i]) {
                inType[i] = buf.readByte();
                if (inType[i] == SignalValue.TYPE_DOUBLE) inNum[i] = buf.readDouble();
                else if (inType[i] == SignalValue.TYPE_BOOL) inBool[i] = buf.readBoolean();
                else inStr[i] = ByteBufUtils.readUTF8String(buf);
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeBoolean(hasNbt);
        if (hasNbt && nbtBytes != null) {
            buf.writeInt(nbtBytes.length);
            buf.writeBytes(nbtBytes);
        }
        for (int i = 0; i < 6; i++) {
            buf.writeBoolean(inHas[i]);
            if (inHas[i]) {
                buf.writeByte(inType[i]);
                if (inType[i] == SignalValue.TYPE_DOUBLE) buf.writeDouble(inNum[i]);
                else if (inType[i] == SignalValue.TYPE_BOOL) buf.writeBoolean(inBool[i]);
                else ByteBufUtils.writeUTF8String(buf, inStr[i] == null ? "" : inStr[i]);
            }
        }
    }

    public static class Handler implements IMessageHandler<PacketActuatorNbtData, IMessage> {
        @Override
        public IMessage onMessage(PacketActuatorNbtData message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                // 若全屏表达式编辑器正打开，转发最新快照刷新变量列表（保留单选与滚动）
                GuiActuatorExprEditor.onData(message);
            }
            return null;
        }
    }

    // ===== 客户端读取 =====

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public boolean getHasNbt() { return hasNbt; }
    public byte[] getNbtBytes() { return nbtBytes; }
    public boolean getInHas(int i) { return i >= 0 && i < 6 && inHas[i]; }
    public byte getInType(int i) { return i >= 0 && i < 6 ? inType[i] : 0; }
    public double getInNum(int i) { return i >= 0 && i < 6 ? inNum[i] : 0; }
    public boolean getInBool(int i) { return i >= 0 && i < 6 && inBool[i]; }
    public String getInStr(int i) { return i >= 0 && i < 6 && inStr[i] != null ? inStr[i] : ""; }
}
