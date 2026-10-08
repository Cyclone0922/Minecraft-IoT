package com.cyclone.minecraftiot.network;

import com.cyclone.minecraftiot.SensorDisplayMod;
import com.cyclone.minecraftiot.tileentity.TileConnectorWorkbench;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 客户端 -> 服务端：请求工作台当前卡 A 的完整状态（绑定/模式/表达式/名字/目标 NBT/同机判断），
 * 服务端回 PacketWorkbenchData。GUI 打开、槽位变化、编辑保存后调用。
 */
public class PacketWorkbenchRequest implements IMessage {
    private int x, y, z;

    public PacketWorkbenchRequest() {}

    public PacketWorkbenchRequest(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

    public static class Handler implements IMessageHandler<PacketWorkbenchRequest, IMessage> {
        @Override
        public IMessage onMessage(PacketWorkbenchRequest message, MessageContext ctx) {
            if (ctx.side == Side.SERVER) {
                EntityPlayer player = ctx.getServerHandler().playerEntity;
                World world = player.worldObj;
                TileEntity te = world.getTileEntity(message.x, message.y, message.z);
                if (te instanceof TileConnectorWorkbench) {
                    return PacketWorkbenchData.build((TileConnectorWorkbench) te);
                }
            }
            return null;
        }
    }
}
