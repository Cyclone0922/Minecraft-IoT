package com.cyclone.minecraftiot.gui;

import com.cyclone.minecraftiot.tileentity.TileSensor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class ContainerSensor extends Container {
    public TileSensor tileSensor;

    public ContainerSensor(InventoryPlayer playerInventory, TileSensor tileSensor) {
        this.tileSensor = tileSensor;
        // connector 槽位（左上角 8,8；绑定改由 GUI 内"写入绑定卡"按钮触发）
        this.addSlotToContainer(new SlotConnector(tileSensor, 0, 8, 8, tileSensor));
        // 玩家主背包 3x9（下移到底部，给上方数据区留出空间）
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
        return true;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        // 标准 vanilla 容器模式：真正移动物品，否则 1.7.10 的 slotClick/retrySlotClick 会无限递归崩溃
        ItemStack itemstack = null;
        Slot slot = (Slot) this.inventorySlots.get(slotIndex);
        if (slot != null && slot.getHasStack()) {
            ItemStack itemstack1 = slot.getStack();
            itemstack = itemstack1.copy();
            if (slotIndex == 0) {
                // connector 槽 -> 玩家背包 (1-36)
                if (!this.mergeItemStack(itemstack1, 1, 37, true)) {
                    return null;
                }
            } else {
                // 玩家背包 -> connector 槽 (0)；mergeItemStack 会检查 Slot.isItemValid，非 connector 进不去
                if (!this.mergeItemStack(itemstack1, 0, 1, false)) {
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