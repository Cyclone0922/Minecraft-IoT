package com.cyclone.minecraftiot.network;

import com.cyclone.minecraftiot.util.DisplayGroupUtil;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.world.World;

import java.util.List;

/**
 * 客户端 -> 服务端：在显示器 GUI 点击方向按钮，把整个拼接组的显示面设置为指定面。
 * face 为原版面索引：0=下 1=上 2=北 3=南 4=西 5=东。
 * 整组统一改朝向，避免单块改朝向脱离拼接组。
 */
public class PacketSetDisplayFace implements IMessage {
    private int x, y, z;
    private int face;

    public PacketSetDisplayFace() {}

    public PacketSetDisplayFace(int x, int y, int z, int face) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.face = face;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        face = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(face);
    }

    public static class Handler implements IMessageHandler<PacketSetDisplayFace, IMessage> {
        @Override
        public IMessage onMessage(PacketSetDisplayFace message, MessageContext ctx) {
            if (ctx.side == Side.SERVER) {
                World world = ctx.getServerHandler().playerEntity.worldObj;
                if (world != null && message.face >= 0 && message.face <= 5) {
                    int meta = world.getBlockMetadata(message.x, message.y, message.z);
                    // 先按旧朝向取整组，再统一改朝向
                    List<DisplayGroupUtil.Pos> members = DisplayGroupUtil.group(world, message.x, message.y, message.z, meta);
                    for (DisplayGroupUtil.Pos p : members) {
                        world.setBlockMetadataWithNotify(p.x, p.y, p.z, message.face, 3);
                    }
                }
            }
            return null;
        }
    }
}
