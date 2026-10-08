package com.cyclone.sensordisplay.network;

import com.cyclone.sensordisplay.tileentity.TileActuator;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.nio.charset.StandardCharsets;

/**
 * 客户端 -> 服务端：在执行器 GUI 保存某面的条件表达式、输出表达式、红石强度和输出方向。
 */
public class PacketActuatorConfig implements IMessage {

    private int x, y, z;
    private int side;       // 0..5
    private String condition;
    private int redstoneLevel;
    private String outputExpr;
    private int signalDir;

    public PacketActuatorConfig() {}

    public PacketActuatorConfig(int x, int y, int z, int side, String condition, int redstoneLevel,
                                String outputExpr, int signalDir) {
        this.x = x; this.y = y; this.z = z;
        this.side = side;
        this.condition = condition == null ? "" : condition;
        this.redstoneLevel = redstoneLevel;
        this.outputExpr = outputExpr == null ? "" : outputExpr;
        this.signalDir = signalDir;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        side = buf.readInt();
        redstoneLevel = buf.readInt();
        int len = buf.readInt();
        condition = buf.readBytes(Math.min(len, 500)).toString(StandardCharsets.UTF_8);
        signalDir = buf.readInt();
        int len2 = buf.readInt();
        outputExpr = buf.readBytes(Math.min(len2, 500)).toString(StandardCharsets.UTF_8);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
        buf.writeInt(side);
        buf.writeInt(redstoneLevel);
        byte[] b = condition.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(b.length);
        buf.writeBytes(b);
        buf.writeInt(signalDir);
        byte[] b2 = outputExpr.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(b2.length);
        buf.writeBytes(b2);
    }

    public static class Handler implements IMessageHandler<PacketActuatorConfig, IMessage> {
        @Override
        public IMessage onMessage(PacketActuatorConfig msg, MessageContext ctx) {
            if (ctx.side == Side.SERVER) {
                World world = ctx.getServerHandler().playerEntity.worldObj;
                TileEntity te = world.getTileEntity(msg.x, msg.y, msg.z);
                if (te instanceof TileActuator) {
                    TileActuator act = (TileActuator) te;
                    act.setCondition(msg.side, msg.condition);
                    act.setRedstoneLevel(msg.side, msg.redstoneLevel);
                    act.setOutputExpr(msg.side, msg.outputExpr);
                    act.setSignalDir(msg.side, msg.signalDir);
                }
            }
            return null;
        }
    }
}
