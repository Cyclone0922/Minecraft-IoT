package com.cyclone.sensordisplay.gui;

import com.cyclone.sensordisplay.item.ItemConnector;
import com.cyclone.sensordisplay.tileentity.TileConnectorWorkbench;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 连接器工作台容器：槽0 = 主卡（编辑对象），槽1 = 拷贝目标。
 */
public class ContainerConnectorWorkbench extends Container {
    public TileConnectorWorkbench workbench;

    public ContainerConnectorWorkbench(InventoryPlayer playerInventory, TileConnectorWorkbench workbench) {
        this.workbench = workbench;
        // 主卡槽（左上 8,8）与拷贝目标槽（8,26）
        this.addSlotToContainer(new Slot(workbench, 0, 8, 8) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ItemConnector;
            }
        });
        this.addSlotToContainer(new Slot(workbench, 1, 8, 26) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ItemConnector;
            }
        });
        // 玩家主背包 3x9
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlotToContainer(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        // 快捷栏 1x9
        for (int col = 0; col < 9; ++col) {
            this.addSlotToContainer(new Slot(playerInventory, col, 8 + col * 18, 198));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return workbench.isUseableByPlayer(player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        ItemStack itemstack = null;
        Slot slot = (Slot) this.inventorySlots.get(slotIndex);
        if (slot != null && slot.getHasStack()) {
            ItemStack itemstack1 = slot.getStack();
            itemstack = itemstack1.copy();
            if (slotIndex <= 1) {
                if (!this.mergeItemStack(itemstack1, 2, 38, true)) {
                    return null;
                }
            } else {
                if (!this.mergeItemStack(itemstack1, 0, 2, false)) {
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
