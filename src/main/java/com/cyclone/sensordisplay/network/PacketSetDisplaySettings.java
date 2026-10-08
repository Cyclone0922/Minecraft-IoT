package com.cyclone.sensordisplay.network;

import com.cyclone.sensordisplay.tileentity.TileDisplay;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 客户端 -> 服务端：在显示器 GUI"设置"卡调整显示参数。
 * 参数作用于主控方块，服务端保存（持久化）并把多页数据 + 新设置回推给客户端。
 */
public class PacketSetDisplaySettings implements IMessage {
    private int x, y, z;
    private float fontSize;
    private int alignMode;
    private int textRot;
    private int displayMode;
    private int overflowMode;
    private int columns;

    public PacketSetDisplaySettings() {}

    public PacketSetDisplaySettings(int x, int y, int z, float fontSize, int alignMode,
                                    int textRot, int displayMode, int overflowMode, int columns) {
        this.x = x; this.y = y; this.z = z;
        this.fontSize = fontSize;
        this.alignMode = alignMode;
        this.textRot = textRot;
        this.displayMode = displayMode;
        this.overflowMode = overflowMode;
        this.columns = columns;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt(); y = buf.readInt(); z = buf.readInt();
        fontSize = buf.readFloat();
        alignMode = buf.readInt();
        textRot = buf.readInt();
        displayMode = buf.readInt();
        overflowMode = buf.readInt();
        columns = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
        buf.writeFloat(fontSize);
        buf.writeInt(alignMode);
        buf.writeInt(textRot);
        buf.writeInt(displayMode);
        buf.writeInt(overflowMode);
        buf.writeInt(columns);
    }

    public static class Handler implements IMessageHandler<PacketSetDisplaySettings, IMessage> {
        @Override
        public IMessage onMessage(PacketSetDisplaySettings message, MessageContext ctx) {
            if (ctx.side == Side.SERVER) {
                World world = ctx.getServerHandler().playerEntity.worldObj;
                TileEntity te = world.getTileEntity(message.x, message.y, message.z);
                if (te instanceof TileDisplay) {
                    TileDisplay display = (TileDisplay) te;
                    display.applyDisplaySettingsAll(message.fontSize, message.alignMode,
                            message.textRot, message.displayMode, message.overflowMode, message.columns);
                    display.recomputePagesIfServer();
                    display.resyncDisplay();
                }
            }
            return null;
        }
    }
}
