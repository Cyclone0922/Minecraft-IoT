package com.cyclone.minecraftiot.network;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.gui.GuiHandler;
import com.cyclone.minecraftiot.tileentity.TileDisplay;
import com.cyclone.minecraftiot.tileentity.TileSensor;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 客户端 -> 服务端：在显示器"卡片"选项卡点击某张卡的"打开"按钮，
 * 打开**我们自己的远程终端 GUI**（目标机器的物品槽 + 实时友好信息）。
 * 玩家不移动、无距离校验，完全安全。
 * 关闭远程 GUI（Esc）时自动回到显示器 GUI。
 */
public class PacketOpenRemoteGui implements IMessage {
    private int x, y, z; // 显示器主控坐标
    private int slot;

    public PacketOpenRemoteGui() {}

    public PacketOpenRemoteGui(int x, int y, int z, int slot) {
        this.x = x; this.y = y; this.z = z; this.slot = slot;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt(); y = buf.readInt(); z = buf.readInt(); slot = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z); buf.writeInt(slot);
    }

    public static class Handler implements IMessageHandler<PacketOpenRemoteGui, IMessage> {
        @Override
        public IMessage onMessage(PacketOpenRemoteGui message, MessageContext ctx) {
            if (ctx.side == Side.SERVER) {
                World world = ctx.getServerHandler().playerEntity.worldObj;
                TileEntity te = world.getTileEntity(message.x, message.y, message.z);
                if (!(te instanceof TileDisplay) || message.slot < 0 || message.slot >= TileDisplay.MAX_CARDS) return null;
                TileDisplay display = (TileDisplay) te;
                ItemStack card = display.getStackInSlot(message.slot);
                if (card == null || card.getTagCompound() == null) return null;
                NBTTagCompound tag = card.getTagCompound();
                if (!tag.hasKey("sensorX")) return null;
                int sx = tag.getInteger("sensorX"), sy = tag.getInteger("sensorY"), sz = tag.getInteger("sensorZ");
                int dim = tag.getInteger("dimension");
                int dir = tag.hasKey("dirIndex") ? tag.getInteger("dirIndex") : -1;
                if (dir < 0 || dir > 5) return null; // 绑"全部"没有单一机器可打开
                World tw = (dim == world.provider.dimensionId)
                        ? world : MinecraftServer.getServer().worldServerForDimension(dim);
                if (tw == null) return null;
                TileEntity st = tw.getTileEntity(sx, sy, sz);
                if (!(st instanceof TileSensor)) return null;
                TileEntity target = ((TileSensor) st).getTargetTileEntity(dir);
                if (!(target instanceof IInventory)) return null; // 远程终端仅支持物品栏机器
                EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                Container before = player.openContainer;
                // 打开我们自己的远程终端 GUI（不移动玩家，无距离校验）
                player.openGui(MinecraftIotMod.MODID, GuiHandler.GUI_REMOTE,
                        target.getWorldObj(), target.xCoord, target.yCoord, target.zCoord);
                Container after = player.openContainer;
                if (after != before) {
                    // 确实打开了 -> 武装返回标记，Esc 时回到显示器
                    RemoteGuiReturn.arm(player, message.x, message.y, message.z, after);
                }
            }
            return null;
        }
    }
}
