package com.cyclone.minecraftiot.gui;

import com.cyclone.minecraftiot.item.ItemConnector;
import com.cyclone.minecraftiot.tileentity.TileDisplay;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

/**
 * 只允许放置 ItemConnector 的槽位。
 * 行为：
 * - 显示屏 GUI：放入即读取 connector 上的绑定信息，关联到对应传感器
 * - 传感器 GUI：不再"放入即绑定"，改为点击 GUI 内"写入绑定卡"按钮触发（PacketBindConnector）
 */
public class SlotConnector extends Slot {
    private final TileEntity tile;

    public SlotConnector(IInventory inventory, int index, int x, int y, TileEntity tile) {
        super(inventory, index, x, y);
        this.tile = tile;
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemConnector;
    }

    @Override
    public void onSlotChanged() {
        super.onSlotChanged();
        // 显示屏：放入/拔出绑定卡即触发重算与推送（主控持有 6 个卡槽）
        if (tile instanceof TileDisplay) {
            ((TileDisplay) tile).onCardSlotChanged(getSlotIndex());
        }
        // 传感器：绑定由按钮触发，这里不自动绑定
    }
}
