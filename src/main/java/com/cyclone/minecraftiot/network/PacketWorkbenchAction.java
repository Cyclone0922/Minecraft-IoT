package com.cyclone.minecraftiot.network;

import com.cyclone.minecraftiot.tileentity.TileConnectorWorkbench;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 客户端 -> 服务端：连接器工作台动作。
 * action 对应 TileConnectorWorkbench.ACTION_*；text 为表达式/新名字等负载。
 * 服务端执行后把结果提示文本发回给玩家（聊天栏）。
 */
public class PacketWorkbenchAction implements IMessage {
    private int x, y, z;
    private int action;
    private String text = "";

    public PacketWorkbenchAction() {}

    public PacketWorkbenchAction(int x, int y, int z, int action, String text) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.action = action;
        this.text = text == null ? "" : text;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        action = buf.readInt();
        text = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(action);
        ByteBufUtils.writeUTF8String(buf, text);
    }

    public static class Handler implements IMessageHandler<PacketWorkbenchAction, IMessage> {
        @Override
        public IMessage onMessage(PacketWorkbenchAction message, MessageContext ctx) {
            if (ctx.side == Side.SERVER) {
                EntityPlayer player = ctx.getServerHandler().playerEntity;
                World world = player.worldObj;
                TileEntity te = world.getTileEntity(message.x, message.y, message.z);
                if (te instanceof TileConnectorWorkbench) {
                    TileConnectorWorkbench wb = (TileConnectorWorkbench) te;
                    String msg = wb.applyAction(message.action, message.text);
                    if (msg != null && !msg.isEmpty()) {
                        player.addChatMessage(new net.minecraft.util.ChatComponentText("\u00a7e[\u5de5\u4f5c\u53f0] " + msg));
                    }
                }
            }
            return null;
        }
    }
}
