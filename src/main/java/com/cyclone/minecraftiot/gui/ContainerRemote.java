package com.cyclone.minecraftiot.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 远程终端容器：把目标机器的物品栏槽位原样映射进我们自己的容器。
 * 无距离/可达校验（远程终端本就应随处可用），槽位规则沿用机器的 isItemValidForSlot。
 * 玩家不会移动，完全安全。
 */
public class ContainerRemote extends Container {
    public static final int MACHINE_TOP = 60;   // 机器槽起始 Y（上方留给标题/进度条/能量/简要信息）
    public static final int COLS = 9;           // 每行最多 9 格
    public static final int SLOT = 18;
    public static final int MAX_SLOTS = 54;     // 最多展示 54 个槽，避免 GUI 过高

    private final IInventory machine;
    private final int machineRows;

    public ContainerRemote(InventoryPlayer inv, IInventory machine) {
        this.machine = machine;
        int size = Math.min(machine.getSizeInventory(), MAX_SLOTS);
        this.machineRows = (size + COLS - 1) / COLS;

        for (int i = 0; i < size; i++) {
            int row = i / COLS, col = i % COLS;
            addSlotToContainer(new Slot(machine, i, 8 + col * SLOT, MACHINE_TOP + row * SLOT));
        }
        int playerInvY = playerInvY();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlotToContainer(new Slot(inv, c + r * 9 + 9, 8 + c * SLOT, playerInvY + r * SLOT));
            }
        }
        int hotbarY = hotbarY();
        for (int c = 0; c < 9; c++) {
            addSlotToContainer(new Slot(inv, c, 8 + c * SLOT, hotbarY));
        }
    }

    public int getMachineRows() { return machineRows; }
    public IInventory getMachine() { return machine; }

    public int playerInvY() {
        return MACHINE_TOP + machineRows * SLOT + 4;
    }

    public int hotbarY() {
        return playerInvY() + 58;
    }

    public int guiHeight() {
        return hotbarY() + SLOT + 7;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true; // 远程终端无距离限制
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        ItemStack original = null;
        Slot slot = (Slot) this.inventorySlots.get(index);
        if (slot != null && slot.getHasStack()) {
            ItemStack stack = slot.getStack();
            original = stack.copy();
            int machineCount = Math.min(machine.getSizeInventory(), MAX_SLOTS);
            if (index < machineCount) {
                if (!mergeItemStack(stack, machineCount, inventorySlots.size(), true)) return null;
            } else {
                if (!mergeItemStack(stack, 0, machineCount, false)) return null;
            }
            if (stack.stackSize == 0) slot.putStack(null);
            else slot.onSlotChanged();
            if (stack.stackSize == original.stackSize) return null;
            slot.onPickupFromSlot(player, stack);
        }
        return original;
    }
}
