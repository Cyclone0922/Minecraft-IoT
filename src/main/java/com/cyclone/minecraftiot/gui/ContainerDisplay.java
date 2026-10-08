package com.cyclone.minecraftiot.gui;

import com.cyclone.minecraftiot.tileentity.TileDisplay;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class ContainerDisplay extends Container {
    private TileDisplay tileDisplay;

    public ContainerDisplay(InventoryPlayer playerInventory, TileDisplay tileDisplay) {
        this.tileDisplay = tileDisplay;
        // 绑定卡槽位（6 个，左上角区域；放入即读取绑定并重新生成数据页）
        for (int i = 0; i < TileDisplay.MAX_CARDS; i++) {
            this.addSlotToContainer(new SlotConnector(tileDisplay, i, 8 + i * 18, 22, tileDisplay));
        }
        // 玩家主背包 3x9（下移到 GUI 底部，给上方设置/数据区留出更大空间）
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlotToContainer(new Slot(playerInventory, col + row * 9 + 9, 23 + col * 18, 154 + row * 18));
            }
        }
        // 快捷栏 1x9
        for (int col = 0; col < 9; ++col) {
            this.addSlotToContainer(new Slot(playerInventory, col, 23 + col * 18, 216));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        // 标准 vanilla 容器模式：必须真正移动物品，否则 1.7.10 的 slotClick/retrySlotClick 无限递归崩溃
        ItemStack itemstack = null;
        Slot slot = (Slot) this.inventorySlots.get(slotIndex);
        if (slot != null && slot.getHasStack()) {
            ItemStack itemstack1 = slot.getStack();
            itemstack = itemstack1.copy();
            if (slotIndex < TileDisplay.MAX_CARDS) {
                // 绑定卡槽 -> 玩家背包(6..41)
                if (!this.mergeItemStack(itemstack1, TileDisplay.MAX_CARDS, 42, true)) {
                    return null;
                }
            } else {
                // 玩家背包 -> 绑定卡槽；mergeItemStack 会检查 Slot.isItemValid，非 connector 进不去
                if (!this.mergeItemStack(itemstack1, 0, TileDisplay.MAX_CARDS, false)) {
                    return null;
                }
            }
            if (itemstack1.stackSize == 0) {
                slot.putStack((ItemStack) null);
            } else {
                slot.onSlotChanged();
            }
            if (itemstack1.stackSize == itemstack.stackSize) {
                return null;
            }
            slot.onPickupFromSlot(player, itemstack1);
        }
        return itemstack;
    }
}
