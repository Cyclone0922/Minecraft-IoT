package com.cyclone.minecraftiot.gui;

import com.cyclone.minecraftiot.tileentity.TileItemBuffer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;

/**
 * 物品缓存器只读 GUI 容器（AE2 面板风格）。
 * 不含任何可交互槽位：无法用鼠标放入/取出物品。
 * 数据展示通过 PacketItemBufferData 实时推送到客户端 TileItemBuffer 显示缓存。
 */
public class ContainerItemBuffer extends Container {

    public final TileItemBuffer te;

    public ContainerItemBuffer(InventoryPlayer inv, TileItemBuffer te) {
        this.te = te;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return te.isUseableByPlayer(player);
    }
}
