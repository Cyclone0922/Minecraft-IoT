package com.cyclone.minecraftiot.network;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.tileentity.TileActuator;
import com.cyclone.minecraftiot.util.RouterCluster;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

/**
 * 客户端 -> 服务端：玩家在路由器选择界面点选了某个路由器组，
 * 服务端现场解析该组（合法性 + 容量校验），绑定到执行器（落存档），
 * 随后直接打开执行器主 GUI。
 */
public class PacketRouterBind implements IMessage {

    private int ax, ay, az;
    private int rx, ry, rz;

    public PacketRouterBind() {}

    public PacketRouterBind(int ax, int ay, int az, int rx, int ry, int rz) {
        this.ax = ax;
        this.ay = ay;
        this.az = az;
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        ax = buf.readInt();
        ay = buf.readInt();
        az = buf.readInt();
        rx = buf.readInt();
        ry = buf.readInt();
        rz = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(ax);
        buf.writeInt(ay);
        buf.writeInt(az);
        buf.writeInt(rx);
        buf.writeInt(ry);
        buf.writeInt(rz);
    }

    public static class Handler implements IMessageHandler<PacketRouterBind, IMessage> {
        @Override
        public IMessage onMessage(PacketRouterBind message, MessageContext ctx) {
            if (ctx.side != Side.SERVER) return null;
            EntityPlayer player = ctx.getServerHandler().playerEntity;
            World world = player.worldObj;
            TileEntity te = world.getTileEntity(message.ax, message.ay, message.az);
            if (!(te instanceof TileActuator)) return null;
            TileActuator act = (TileActuator) te;
            // 现场解析目标组：非法组（有缺口/超长/非路由器）拒绝绑定
            RouterCluster c = RouterCluster.resolve(world, message.rx, message.ry, message.rz);
            if (c == null) {
                if (player instanceof EntityPlayerMP) {
                    ((EntityPlayerMP) player).addChatComponentMessage(new ChatComponentText(
                            "\u00a7c[\u8def\u7531\u5668] \u7ec4\u4e0d\u5408\u6cd5\uff08\u975e\u7acb\u65b9\u4f53/\u6709\u7f3a\u53e3/\u8d85\u957f\uff09\uff0c\u65e0\u6cd5\u7ed1\u5b9a"));
                }
                return null;
            }
            // 容量校验：已用 >= 容量 拒绝
            if (c.countBoundActuators(world) >= c.capacity) {
                if (player instanceof EntityPlayerMP) {
                    ((EntityPlayerMP) player).addChatComponentMessage(new ChatComponentText(
                            "\u00a7c[\u8def\u7531\u5668] \u8be5\u7ec4\u5df2\u6ee1\uff08" + c.capacity + "/"
                                    + c.capacity + "\uff09\uff0c\u8bf7\u589e\u52a0\u8def\u7531\u5668\u65b9\u5757\u6269\u5bb9"));
                }
                return null;
            }
            act.setRouter(c.cx, c.cy, c.cz, c.blockCount);
            player.openGui(MinecraftIotMod.instance, 3, world, message.ax, message.ay, message.az);
            return null;
        }
    }
}
