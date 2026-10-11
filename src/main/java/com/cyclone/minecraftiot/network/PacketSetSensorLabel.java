package com.cyclone.minecraftiot.network;

import com.cyclone.minecraftiot.tileentity.TileSensor;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 客户端 -> 服务端：用户在传感器 GUI 点击"保存注释"按钮，
 * 把输入框里的自定义注释写到传感器方块上（持久化到 NBT）。
 */
public class PacketSetSensorLabel implements IMessage {
    private int x, y, z;
    private int dirIndex; // 该注释对应的方向（VALID_DIRECTIONS 索引 0..5）
    private String label;

    public PacketSetSensorLabel() {}

    public PacketSetSensorLabel(int x, int y, int z, int dirIndex, String label) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dirIndex = dirIndex;
        this.label = label == null ? "" : label;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        dirIndex = buf.readInt();
        int len = buf.readInt();
        label = buf.readBytes(Math.min(len, 300)).toString(java.nio.charset.Charset.forName("UTF-8"));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(dirIndex);
        String s = label == null ? "" : label;
        byte[] b = s.getBytes(java.nio.charset.Charset.forName("UTF-8"));
        buf.writeInt(b.length);
        buf.writeBytes(b);
    }

    public static class Handler implements IMessageHandler<PacketSetSensorLabel, IMessage> {
        @Override
        public IMessage onMessage(PacketSetSensorLabel message, MessageContext ctx) {
            if (ctx.side == Side.SERVER) {
                World world = ctx.getServerHandler().playerEntity.worldObj;
                TileEntity te = world.getTileEntity(message.x, message.y, message.z);
                if (te instanceof TileSensor) {
                    ((TileSensor) te).setLabel(message.dirIndex, message.label);
                }
            }
            return null;
        }
    }
}
