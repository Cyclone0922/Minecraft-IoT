package com.cyclone.minecraftiot.network;

import com.cyclone.minecraftiot.gui.GuiActuator;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
/**
 * 服务端 -> 客户端：执行器面配置保存成功确认。
 * 客户端仅在收到确认后显示"保存成功"（失败则静默，符合"失败不显示"语义）。
 * 同时清除对应面的未保存草稿（pending）。
 */
public class PacketActuatorSaveAck implements IMessage {
    private int x, y, z;
    private int side;

    public PacketActuatorSaveAck() {}

    public PacketActuatorSaveAck(int x, int y, int z, int side) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.side = side;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        side = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(side);
    }

    public static class Handler implements IMessageHandler<PacketActuatorSaveAck, IMessage> {
        @Override
        public IMessage onMessage(PacketActuatorSaveAck message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                GuiActuator.onSaveAck(message.x, message.y, message.z, message.side);
            }
            return null;
        }
    }
}
