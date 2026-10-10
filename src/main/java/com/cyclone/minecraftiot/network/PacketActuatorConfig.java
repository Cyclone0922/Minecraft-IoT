package com.cyclone.minecraftiot.network;

import com.cyclone.minecraftiot.tileentity.TileActuator;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.nio.charset.StandardCharsets;

/**
 * 客户端 -> 服务端：在执行器 GUI 保存某面的条件表达式、输出表达式和红石强度。
 * 方向合一模型：输出永远从本面出，无输出方向字段。
 */
public class PacketActuatorConfig implements IMessage {

    private int x, y, z;
    private int side;       // 0..5
    private String condition;
    private int redstoneLevel;
    private String outputExpr;

    public PacketActuatorConfig() {}

    public PacketActuatorConfig(int x, int y, int z, int side, String condition, int redstoneLevel,
                                String outputExpr) {
        this.x = x; this.y = y; this.z = z;
        this.side = side;
        this.condition = condition == null ? "" : condition;
        this.redstoneLevel = redstoneLevel;
        this.outputExpr = outputExpr == null ? "" : outputExpr;
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
                }
            }
            return null;
        }
    }
}
