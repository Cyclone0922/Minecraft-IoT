package com.cyclone.minecraftiot.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import com.cyclone.minecraftiot.tileentity.TileActuator;

/**
 * 客户端 -> 服务端：把请求方执行器绑定到目标执行器（或解绑）。
 * 目标坐标传 (Integer.MIN_VALUE,0,0,0) 表示解绑。
 * 服务端校验：目标必须是 TileActuator、且与请求方同路由器组（同网络）。
 */
public class PacketActuatorBind implements IMessage {

    public int ax, ay, az;    // 请求方执行器坐标
    public int tx, ty, tz;    // 目标执行器坐标（tx=Integer.MIN_VALUE 表示解绑）

    public PacketActuatorBind() {}

    public PacketActuatorBind(int ax, int ay, int az, int tx, int ty, int tz) {
        this.ax = ax;
        this.ay = ay;
        this.az = az;
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        ax = buf.readInt();
        ay = buf.readInt();
        az = buf.readInt();
        tx = buf.readInt();
        ty = buf.readInt();
        tz = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(ax);
        buf.writeInt(ay);
        buf.writeInt(az);
        buf.writeInt(tx);
        buf.writeInt(ty);
        buf.writeInt(tz);
    }

    public static class Handler implements IMessageHandler<PacketActuatorBind, IMessage> {
        @Override
        public IMessage onMessage(final PacketActuatorBind msg, MessageContext ctx) {
            if (ctx.side != Side.SERVER) return null;
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            World world = player.worldObj;
            TileEntity te = world.getTileEntity(msg.ax, msg.ay, msg.az);
            if (!(te instanceof TileActuator)) return null;
            TileActuator self = (TileActuator) te;
            // 解绑
            if (msg.tx == Integer.MIN_VALUE) {
                self.clearRemoteAct();
                return null;
            }
            // 绑定：校验目标存在且同网络
            TileEntity tte = world.getTileEntity(msg.tx, msg.ty, msg.tz);
            if (!(tte instanceof TileActuator)) return null;
            TileActuator target = (TileActuator) tte;
            if (!self.isRouterBound() || !target.isRouterBound()) return null;
            if (self.getRouterDim() != target.getRouterDim()) return null;
            if (self.getRouterX() != target.getRouterX()
                    || self.getRouterY() != target.getRouterY()
                    || self.getRouterZ() != target.getRouterZ()) return null;
            // 不能绑定自己
            if (msg.tx == msg.ax && msg.ty == msg.ay && msg.tz == msg.az) return null;
            self.setRemoteAct(msg.tx, msg.ty, msg.tz, world.provider.dimensionId);
            return null;
        }
    }
}
