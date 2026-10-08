package com.cyclone.minecraftiot.network;

import com.cyclone.minecraftiot.tileentity.TileSensor;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 客户端 -> 服务端：用户在传感器 GUI 点击"写入绑定卡"按钮，
 * 让服务端把槽位内的 connector 绑定到传感器坐标 + 当前选中的方向索引。
 * dirIndex 为 -1 表示绑定"全部方向"；name 为玩家在弹出的命名框中输入的自定义卡名（可为空）。
 */
public class PacketBindConnector implements IMessage {
    private int x, y, z;
    private int dirIndex = -1;
    private String name = "";

    public PacketBindConnector() {}

    public PacketBindConnector(int x, int y, int z, int dirIndex, String name) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dirIndex = dirIndex;
        this.name = name == null ? "" : name;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        dirIndex = buf.readInt();
        name = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(dirIndex);
        ByteBufUtils.writeUTF8String(buf, name);
    }

    public static class Handler implements IMessageHandler<PacketBindConnector, IMessage> {
        @Override
        public IMessage onMessage(PacketBindConnector message, MessageContext ctx) {
            if (ctx.side == Side.SERVER) {
                World world = ctx.getServerHandler().playerEntity.worldObj;
                TileEntity te = world.getTileEntity(message.x, message.y, message.z);
                if (te instanceof TileSensor) {
                    TileSensor sensor = (TileSensor) te;
                    sensor.bindConnector(sensor.getConnectorStack(), message.dirIndex, message.name);
                }
            }
            return null;
        }
    }
}
