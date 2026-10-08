package com.cyclone.sensordisplay.tileentity;

import com.cyclone.sensordisplay.item.ItemConnector;
import com.cyclone.sensordisplay.util.ConnectorConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 连接器工作台 TileEntity。
 * 两个卡槽：槽0 = 主卡（所有编辑/重置操作作用于此），槽1 = 拷贝目标。
 * 全部动作在服务端执行（由 PacketWorkbenchAction 触发），完成后同步槽位变化。
 */
public class TileConnectorWorkbench extends TileEntity implements IInventory {

    // 动作码（与客户端 PacketWorkbenchAction 一致）
    public static final int ACTION_CLEAR_CONFIG = 0;    // 清空为原始NBT
    public static final int ACTION_SET_DEFAULT = 1;     // 设置为默认友好显示方案
    public static final int ACTION_SAVE_EXPR = 2;       // 保存自定义表达式（text=表达式）
    public static final int ACTION_CLEAR_BINDING = 3;   // 清空绑定信息（恢复出厂）
    public static final int ACTION_RENAME = 4;          // 重命名（text=新名字）
    public static final int ACTION_COPY_CONFIG = 5;     // 拷贝配置 A→B
    public static final int ACTION_COPY_ALL = 6;        // 拷贝全部 A→B

    private ItemStack[] slots = new ItemStack[2];

    // ===== 客户端缓存（由 PacketWorkbenchData 应用） =====
    private boolean clientHasCard;
    private boolean clientHasBinding;
    private int clientMode;
    private String clientExpr = "";
    private String clientName = "";
    private String clientTargetClass = "";
    private NBTTagCompound clientNbt;
    private boolean clientSameMachine;

    /** 客户端：应用服务端推送的状态快照 */
    public void applyClientData(com.cyclone.sensordisplay.network.PacketWorkbenchData p) {
        this.clientHasCard = p.getHasCardA();
        this.clientHasBinding = p.getHasBinding();
        this.clientMode = p.getMode();
        this.clientExpr = p.getExpr();
        this.clientName = p.getName();
        this.clientTargetClass = p.getTargetClass();
        this.clientSameMachine = p.getSameMachine();
        this.clientNbt = null;
        if (p.getHasNbt() && p.getNbtBytes() != null) {
            try {
                this.clientNbt = CompressedStreamTools.func_152457_a(p.getNbtBytes(), NBTSizeTracker.field_152451_a);
            } catch (Exception ignore) {}
        }
    }

    public boolean clientHasCard() { return clientHasCard; }
    public boolean clientHasBinding() { return clientHasBinding; }
    public int clientMode() { return clientMode; }
    public String clientExpr() { return clientExpr; }
    public String clientName() { return clientName; }
    public String clientTargetClass() { return clientTargetClass; }
    public NBTTagCompound clientNbt() { return clientNbt; }
    public boolean clientSameMachine() { return clientSameMachine; }

    // ============ 动作处理（服务端） ============

    /** 执行一个动作；返回给客户端的提示文本（可为空） */
    public String applyAction(int action, String text) {
        ItemStack a = slots[0];
        switch (action) {
            case ACTION_CLEAR_CONFIG:
                if (a != null && a.getItem() instanceof ItemConnector) {
                    ConnectorConfig.clearConfig(a);
                    markDirty();
                    return "\u5df2\u6e05\u7a7a\u914d\u7f6e\uff0c\u53ea\u663e\u793a\u539f\u59cb NBT";
                }
                return "\u8bf7\u5148\u63d2\u5165\u8fde\u63a5\u5668";
            case ACTION_SET_DEFAULT:
                if (a != null && a.getItem() instanceof ItemConnector) {
                    ConnectorConfig.setMode(a, ConnectorConfig.MODE_DEFAULT);
                    markDirty();
                    return "\u5df2\u8bbe\u4e3a\u9ed8\u8ba4\u53cb\u597d\u663e\u793a\u65b9\u6848";
                }
                return "\u8bf7\u5148\u63d2\u5165\u8fde\u63a5\u5668";
            case ACTION_SAVE_EXPR:
                if (a != null && a.getItem() instanceof ItemConnector) {
                    ConnectorConfig.setMode(a, ConnectorConfig.MODE_CUSTOM);
                    ConnectorConfig.setExpr(a, text == null ? "" : text);
                    markDirty();
                    return "\u5df2\u4fdd\u5b58\u81ea\u5b9a\u4e49\u8868\u8fbe\u5f0f";
                }
                return "\u8bf7\u5148\u63d2\u5165\u8fde\u63a5\u5668";
            case ACTION_CLEAR_BINDING:
                if (a != null && a.getItem() instanceof ItemConnector) {
                    ConnectorConfig.clearBinding(a);
                    markDirty();
                    return "\u5df2\u6e05\u7a7a\u7ed1\u5b9a\u4fe1\u606f\uff0c\u6062\u590d\u51fa\u5382\u8bbe\u7f6e";
                }
                return "\u8bf7\u5148\u63d2\u5165\u8fde\u63a5\u5668";
            case ACTION_RENAME:
                if (a != null && a.getItem() instanceof ItemConnector) {
                    ConnectorConfig.setName(a, text == null ? "" : text.trim());
                    markDirty();
                    return "\u5df2\u91cd\u547d\u540d";
                }
                return "\u8bf7\u5148\u63d2\u5165\u8fde\u63a5\u5668";
            case ACTION_COPY_CONFIG:
                return copyToB(false);
            case ACTION_COPY_ALL:
                return copyToB(true);
            default:
                return "";
        }
    }

    /** 拷贝配置/全部：要求两卡均为连接器，且（拷贝全部或判断配置时）目标可接受；同机器类型检查由客户端提示，此处宽松执行 */
    private String copyToB(boolean all) {
        ItemStack a = slots[0];
        ItemStack b = slots[1];
        if (a == null || b == null || !(a.getItem() instanceof ItemConnector) || !(b.getItem() instanceof ItemConnector)) {
            return "\u8bf7\u5728\u4e24\u4e2a\u69fd\u4f4d\u5404\u63d2\u5165\u8fde\u63a5\u5668";
        }
        if (all) {
            ConnectorConfig.copyAll(a, b);
        } else {
            ConnectorConfig.copyConfig(a, b);
        }
        markDirty();
        return all ? "\u5df2\u5c06\u914d\u7f6e+\u7ed1\u5b9a+\u540d\u5b57\u5168\u90e8\u62f7\u8d1d\u5230\u65b0\u5361" : "\u5df2\u62f7\u8d1d\u914d\u7f6e\u5230\u65b0\u5361";
    }

    /** 判断两卡绑定的目标机器是否为同一类型（供客户端"拷贝"按钮可用性判断） */
    public boolean sameMachineType() {
        ItemStack a = slots[0];
        ItemStack b = slots[1];
        if (a == null || b == null) return false;
        if (!ConnectorConfig.hasBinding(a) || !ConnectorConfig.hasBinding(b)) return false;
        TileEntity ta = targetOf(a);
        TileEntity tb = targetOf(b);
        if (ta == null || tb == null) return false;
        return ta.getClass().getName().equals(tb.getClass().getName());
    }

    /** 解析一张卡的绑定目标（传感器所指方向的机器）；绑定无效返回 null */
    public TileEntity targetOf(ItemStack card) {
        if (card == null || !ConnectorConfig.hasBinding(card)) return null;
        NBTTagCompound tag = card.getTagCompound();
        int sx = tag.getInteger("sensorX"), sy = tag.getInteger("sensorY"), sz = tag.getInteger("sensorZ");
        int dim = tag.getInteger("dimension");
        int dir = tag.hasKey("dirIndex") ? tag.getInteger("dirIndex") : -1;
        World tw = worldForDim(dim);
        if (tw == null) return null;
        TileEntity te = tw.getTileEntity(sx, sy, sz);
        if (!(te instanceof TileSensor) || dir < 0) return null;
        TileSensor sensor = (TileSensor) te;
        TileEntity target = sensor.getTargetTileEntity(dir);
        if (target != null) return target;
        // 非物品栏方块（如 RC 蓄水器阀门）：按"可扫描目标"再解析一次
        return sensor.getScanTarget(dir);
    }

    /**
     * 取一张卡绑定目标的服务端完整 NBT，供表达式编辑器拍平变量。
     * 优先用传感器扫描缓存（含蓄水器虚拟键 / IC2 运行时合成键）；
     * 无缓存（如锅炉方块）回退目标 TileEntity 直写。
     */
    public NBTTagCompound targetNbtOf(ItemStack card) {
        if (card == null || !ConnectorConfig.hasBinding(card)) return null;
        NBTTagCompound tag = card.getTagCompound();
        int sx = tag.getInteger("sensorX"), sy = tag.getInteger("sensorY"), sz = tag.getInteger("sensorZ");
        int dim = tag.getInteger("dimension");
        int dir = tag.hasKey("dirIndex") ? tag.getInteger("dirIndex") : -1;
        World tw = worldForDim(dim);
        if (tw == null) return null;
        TileEntity te = tw.getTileEntity(sx, sy, sz);
        if (te instanceof TileSensor) {
            TileSensor sensor = (TileSensor) te;
            if (dir >= 0) {
                NBTTagCompound n = sensor.getRawNbt(dir);
                if (n != null) return n;
            } else {
                for (int i = 0; i < 6; i++) {
                    NBTTagCompound n = sensor.getRawNbt(i);
                    if (n != null) return n;
                }
            }
        }
        // 回退：目标 tile 直写
        TileEntity t = targetOf(card);
        if (t != null) {
            NBTTagCompound nbt = new NBTTagCompound();
            try {
                t.writeToNBT(nbt);
                return nbt;
            } catch (Throwable ignore) {
                return null;
            }
        }
        return null;
    }

    /** 卡 A 绑定的目标机器类名（客户端显示"目标机器"用）；未绑定返回 null */
    public String targetClassName() {
        TileEntity t = targetOf(slots[0]);
        return t == null ? null : t.getClass().getName();
    }

    private World worldForDim(int dim) {
        if (dim == worldObj.provider.dimensionId) return worldObj;
        if (MinecraftServer.getServer() == null) return null;
        return MinecraftServer.getServer().worldServerForDimension(dim);
    }

    /** 默认命名方案：已绑定按方向给"绑定卡-<字母>"，未绑定给"连接器-N"（N=槽0物品序号占位，用"连接器"即可） */
    public String defaultName() {
        ItemStack a = slots[0];
        if (a == null || !(a.getItem() instanceof ItemConnector)) return "\u8fde\u63a5\u5668";
        if (ConnectorConfig.hasBinding(a)) {
            int dir = a.getTagCompound().hasKey("dirIndex") ? a.getTagCompound().getInteger("dirIndex") : -1;
            return "\u7ed1\u5b9a\u5361-" + dirLetter(dir);
        }
        return "\u8fde\u63a5\u5668";
    }

    private String dirLetter(int dir) {
        switch (dir) {
            case 0: return "D";
            case 1: return "U";
            case 2: return "N";
            case 3: return "S";
            case 4: return "W";
            case 5: return "E";
            default: return "\u5168\u90e8";
        }
    }

    // ============ IInventory（2 个连接器槽位） ============

    @Override
    public int getSizeInventory() { return 2; }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= slots.length) return null;
        return slots[slot];
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slot < 0 || slot >= slots.length || slots[slot] == null) return null;
        ItemStack stack;
        if (slots[slot].stackSize <= amount) {
            stack = slots[slot];
            slots[slot] = null;
            markDirty();
            return stack;
        } else {
            stack = slots[slot].splitStack(amount);
            if (slots[slot].stackSize == 0) slots[slot] = null;
            markDirty();
            return stack;
        }
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        if (slot < 0 || slot >= slots.length) return null;
        ItemStack stack = slots[slot];
        slots[slot] = null;
        return stack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot < 0 || slot >= slots.length) return;
        slots[slot] = stack;
        if (stack != null && stack.stackSize > getInventoryStackLimit()) {
            stack.stackSize = getInventoryStackLimit();
        }
        markDirty();
    }

    @Override
    public String getInventoryName() { return "container.connector_workbench"; }

    @Override
    public boolean hasCustomInventoryName() { return false; }

    @Override
    public int getInventoryStackLimit() { return 1; }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
                && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64.0;
    }

    @Override
    public void openInventory() { }

    @Override
    public void closeInventory() { }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return slot >= 0 && slot < slots.length && stack != null && stack.getItem() instanceof ItemConnector;
    }

    // ============ 持久化 ============

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) {
                NBTTagCompound itemTag = new NBTTagCompound();
                slots[i].writeToNBT(itemTag);
                tag.setTag("Slot" + i, itemTag);
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        for (int i = 0; i < slots.length; i++) {
            if (tag.hasKey("Slot" + i)) {
                slots[i] = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("Slot" + i));
            }
        }
    }
}
