package com.cyclone.minecraftiot.network;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.tileentity.TileActuator;
import com.cyclone.minecraftiot.util.SignalExpr;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.nio.charset.Charset;

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
        condition = buf.readBytes(Math.min(len, 5000)).toString(Charset.forName("UTF-8"));
        int len2 = buf.readInt();
        outputExpr = buf.readBytes(Math.min(len2, 5000)).toString(Charset.forName("UTF-8"));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
        buf.writeInt(side);
        buf.writeInt(redstoneLevel);
        byte[] b = condition.getBytes(Charset.forName("UTF-8"));
        buf.writeInt(b.length);
        buf.writeBytes(b);
        byte[] b2 = outputExpr.getBytes(Charset.forName("UTF-8"));
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
                    // 语法校验：非法表达式拒绝保存（不落存档、不回成功确认，客户端静默）
                    if (!SignalExpr.isValid(msg.condition) || !SignalExpr.isValid(msg.outputExpr)) {
                        MinecraftIotMod.log.warn("Actuator save rejected: invalid expr side=" + msg.side
                                + " cond=[" + msg.condition + "] out=[" + msg.outputExpr + "]");
                        return null;
                    }
                    // 空格标准化：去掉多余空白（空格/TAB/换行），变量与运算符之间统一一个空格；
                    // 字符串内部内容（含 \n 换行转义）原样保留。
                    String cond = SignalExpr.normalize(msg.condition);
                    String out = SignalExpr.normalize(msg.outputExpr);
                    act.setCondition(msg.side, cond == null ? msg.condition : cond);
                    act.setRedstoneLevel(msg.side, msg.redstoneLevel);
                    act.setOutputExpr(msg.side, out == null ? msg.outputExpr : out);
                    // 保存成功确认：客户端收到后才显示"保存成功"
                    return new PacketActuatorSaveAck(msg.x, msg.y, msg.z, msg.side);
                }
            }
            return null;
        }
    }
}
