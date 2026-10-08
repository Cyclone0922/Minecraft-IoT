package com.cyclone.sensordisplay.gui;

import com.cyclone.sensordisplay.item.ItemConnector;
import com.cyclone.sensordisplay.item.ItemSkill;
import com.cyclone.sensordisplay.tileentity.TileActuator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 执行器容器：2 个槽（绑定卡 + 技能插件）+ 玩家背包。
 */
public class ContainerActuator extends Container {

    public TileActuator tileActuator;

    public ContainerActuator(InventoryPlayer playerInventory, TileActuator tile) {
        this.tileActuator = tile;
        // 槽 0：绑定卡
        this.addSlotToContainer(new Slot(tile, 0, 8, 8) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ItemConnector;
            }
            @Override
            public int getSlotStackLimit() { return 1; }
        });
        // 槽 1：技能插件
        this.addSlotToContainer(new Slot(tile, 1, 8, 32) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ItemSkill;
            }
            @Override
            public int getSlotStackLimit() { return 1; }
        });
        // 玩家主背包 3x9（GUI 加高后下移）
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlotToContainer(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 156 + row * 18));
            }
        }
        // 快捷栏 1x9
        for (int col = 0; col < 9; ++col) {
            this.addSlotToContainer(new Slot(playerInventory, col, 8 + col * 18, 214));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) { return true; }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        ItemStack itemstack = null;
        Slot slot = (Slot) this.inventorySlots.get(slotIndex);
        if (slot != null && slot.getHasStack()) {
            ItemStack itemstack1 = slot.getStack();
            itemstack = itemstack1.copy();
            if (slotIndex < 2) {
                // 执行器槽 -> 玩家背包
                if (!this.mergeItemStack(itemstack1, 2, 38, true)) return null;
            } else {
                // 玩家背包 -> 执行器槽（按类型自动分配）
                if (itemstack1.getItem() instanceof ItemConnector) {
                    if (!this.mergeItemStack(itemstack1, 0, 1, false)) return null;
                } else if (itemstack1.getItem() instanceof ItemSkill) {
                    if (!this.mergeItemStack(itemstack1, 1, 2, false)) return null;
                } else {
                    return null;
                }
            }
            if (itemstack1.stackSize == 0) slot.putStack(null);
            else slot.onSlotChanged();
            if (itemstack1.stackSize == itemstack.stackSize) return null;
            slot.onPickupFromSlot(player, itemstack1);
        }
        return itemstack;
    }
}
