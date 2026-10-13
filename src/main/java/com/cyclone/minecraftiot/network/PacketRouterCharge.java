package com.cyclone.minecraftiot.network;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.gui.ContainerRouter;
import com.cyclone.minecraftiot.tileentity.TileRouter;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 路由器充电包（0.5.1 测试用）：
 * 客户端在路由器 GUI 输入框输入数字，点"充电"按钮，发 amount 到服务端。
 * 服务端从玩家当前打开的 ContainerRouter 取路由器 TE，调用 chargeIe(amount)。
 */
public class PacketRouterCharge implements IMessage {
    private double amount;

    public PacketRouterCharge() {}
    public PacketRouterCharge(double amount) { this.amount = amount; }

    @Override
    public void fromBytes(ByteBuf buf) {
        amount = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(amount);
    }

    public static class Handler implements IMessageHandler<PacketRouterCharge, IMessage> {
        @Override
        public IMessage onMessage(PacketRouterCharge msg, MessageContext ctx) {
            EntityPlayer player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            if (player.openContainer instanceof ContainerRouter) {
                TileRouter r = ((ContainerRouter) player.openContainer).router;
                if (r != null && !r.isInvalid()) {
                    double got = r.chargeIe(msg.amount);
                    MinecraftIotMod.log.info("[RouterCharge] +" + got + " IE, total=" + r.getEnergy().getIe());
                }
            }
            return null;
        }
    }
}
