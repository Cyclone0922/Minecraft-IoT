package com.cyclone.minecraftiot.network;

import com.cyclone.minecraftiot.tileentity.TileActuator;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 客户端 -> 服务端：请求执行器绑定卡指向的目标机器 NBT 快照。
 * 供全屏表达式编辑器列出可用变量（每 20t 刷新一次，观察动态值）。
 * 服务端回 PacketActuatorNbtData。
 */
public class PacketActuatorNbtRequest implements IMessage {
    private int x, y, z;
    private int side; // 当前编辑面（服务端按 buildInValues(side) 语义生成 6 面输入快照）

    public PacketActuatorNbtRequest() {}

    public PacketActuatorNbtRequest(int x, int y, int z, int side) {
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

    public static class Handler implements IMessageHandler<PacketActuatorNbtRequest, IMessage> {
        @Override
        public IMessage onMessage(PacketActuatorNbtRequest message, MessageContext ctx) {
            if (ctx.side == Side.SERVER) {
                World world = ctx.getServerHandler().playerEntity.worldObj;
                TileEntity te = world.getTileEntity(message.x, message.y, message.z);
                if (te instanceof TileActuator) {
                    return PacketActuatorNbtData.build((TileActuator) te, message.side);
                }
            }
            return null;
        }
    }
}
