package com.cyclone.minecraftiot.network;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.tileentity.TileActuator;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端 -> 服务端：请求列出同网络（同路由器组）内全部执行器，供玩家选择绑定哪个作为物品源。
 * 服务端处理：扫描与本执行器同 routerX/Y/Z/Dim 的全部 TileActuator，
 * 组装 PacketActuatorListData 下发给客户端弹窗。
 */
public class PacketActuatorListRequest implements IMessage {

    public int ax, ay, az; // 请求方执行器坐标

    public PacketActuatorListRequest() {}

    public PacketActuatorListRequest(int ax, int ay, int az) {
        this.ax = ax;
        this.ay = ay;
        this.az = az;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        ax = buf.readInt();
        ay = buf.readInt();
        az = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(ax);
        buf.writeInt(ay);
        buf.writeInt(az);
    }

    public static class Handler implements IMessageHandler<PacketActuatorListRequest, IMessage> {
        @Override
        public IMessage onMessage(final PacketActuatorListRequest msg, MessageContext ctx) {
            if (ctx.side != Side.SERVER) return null;
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            World world = player.worldObj;
            TileEntity te = world.getTileEntity(msg.ax, msg.ay, msg.az);
            if (!(te instanceof TileActuator)) return null;
            TileActuator self = (TileActuator) te;
            if (!self.isRouterBound()) return null;

            // 遍历当前世界已加载的全部 TileEntity，过滤同网络执行器（同路由器组中心+维度）。
            // 1.7.10 World.loadedTileEntityList 是已加载 TileEntity 的实时列表。
            List<TileActuator> list = new ArrayList<TileActuator>();
            for (Object o : world.loadedTileEntityList) {
                if (!(o instanceof TileActuator)) continue;
                TileActuator a = (TileActuator) o;
                if (a == self) continue;
                if (!a.isRouterBound()) continue;
                if (a.getRouterDim() != self.getRouterDim()) continue;
                if (a.getRouterX() != self.getRouterX()
                        || a.getRouterY() != self.getRouterY()
                        || a.getRouterZ() != self.getRouterZ()) continue;
                list.add(a);
            }
            MinecraftIotMod.network.sendTo(new PacketActuatorListData(msg.ax, msg.ay, msg.az, list), player);
            return null;
        }
    }
}
