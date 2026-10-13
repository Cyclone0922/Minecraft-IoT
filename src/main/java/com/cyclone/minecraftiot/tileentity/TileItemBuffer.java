package com.cyclone.minecraftiot.tileentity;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.gui.ContainerItemBuffer;
import com.cyclone.minecraftiot.network.PacketItemBufferData;
import com.cyclone.minecraftiot.util.ItemBufferCluster;
import com.cyclone.minecraftiot.util.ItemPool;
import com.cyclone.minecraftiot.util.RouterCluster;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品缓存器 TileEntity。
 *
 * 职责：
 *  - 入网判定：紧贴路由器组或经总线/缓存器链到达路由器组（ItemBufferCluster.isConnected）
 *  - 正常状态：自身不存物品，只贡献容量（router 的池总量/种类上限 + blockCount × 4096/64）
 *  - 灾备状态（drMode=true）：当路由器被全部破坏后，物品池转移到本缓存器组的下东南方块，
 *    GUI 可查看但一切交互禁用（执行器/漏斗/网络均无法访问）
 *
 * IInventory（仅供漏斗/其他 MOD 输入）：
 *  - 实现 IInventory，槽位=当前种类数+1（留空位）
 *  - setInventorySlotContents → 把物品写入网络池（路由器主控）或灾备本地池
 *  - decrStackSize → 恒 null（禁取出，仅输入）
 *  - isItemValidForSlot → 恒 true
 */
public class TileItemBuffer extends TileEntity implements IInventory {

    private boolean drMode = false;                    // 灾备状态
    private ItemPool localPool = new ItemPool();       // 灾备时本地持池（存下东南方块）

    // ===== 客户端显示缓存（由 PacketItemBufferData 写入） =====
    private boolean cDrMode;
    private int cKindCap, cItemCap, cTotal, cKind;
    private int[] cIds = new int[0], cMetas = new int[0], cCounts = new int[0];

    public TileItemBuffer() {}

    // ===== 客户端显示数据访问 =====

    public void applyClientData(PacketItemBufferData p) {
        cDrMode = p.getDrMode();
        cKindCap = p.getKindCap();
        cItemCap = p.getItemCap();
        cTotal = p.getTotalCount();
        cKind = p.getKindCount();
        cIds = p.getItemIds();
        cMetas = p.getMetas();
        cCounts = p.getCounts();
    }

    public boolean getCDrMode() { return cDrMode; }
    public int getCKindCap() { return cKindCap; }
    public int getCItemCap() { return cItemCap; }
    public int getCTotal() { return cTotal; }
    public int getCKind() { return cKind; }
    public int[] getCIds() { return cIds; }
    public int[] getCMetas() { return cMetas; }
    public int[] getCCounts() { return cCounts; }

    /** 当前有效池的物品容量上限（正常→路由器池容量；灾备→本组容量） */
    public int getActivePoolKindCapacity() {
        if (drMode) return getKindCapacity();
        TileRouter r = getRouterMaster();
        return r != null ? r.getPoolKindCapacity() : getKindCapacity();
    }

    // ===== 状态查询 =====

    public boolean isDrMode() { return drMode; }
    public void setDrMode(boolean m) { this.drMode = m; markDirty(); sync(); }

    /** 是否入网（正常状态判定） */
    public boolean isConnected() {
        if (worldObj == null || worldObj.isRemote) return false;
        return ItemBufferCluster.isConnected(worldObj, xCoord, yCoord, zCoord);
    }

    /** 本缓存器所属组的容量（总量/种类） */
    public int getItemCapacity() {
        ItemBufferCluster c = ItemBufferCluster.resolve(worldObj, xCoord, yCoord, zCoord);
        return c == null ? ItemPool.DEFAULT_ITEM_CAP : c.itemCapacity;
    }

    public int getKindCapacity() {
        ItemBufferCluster c = ItemBufferCluster.resolve(worldObj, xCoord, yCoord, zCoord);
        return c == null ? ItemPool.DEFAULT_KIND_CAP : c.kindCapacity;
    }

    // ===== 网络池访问（正常状态委托路由器主控） =====

    /** 找到本缓存器可达的路由器组主控 TileRouter；未入网/灾备返回 null */
    public TileRouter getRouterMaster() {
        if (drMode || worldObj == null || worldObj.isRemote) return null;
        RouterCluster rc = ItemBufferCluster.findRouter(worldObj, xCoord, yCoord, zCoord);
        if (rc == null) return null;
        TileEntity te = worldObj.getTileEntity(rc.cx, rc.cy, rc.cz);
        return te instanceof TileRouter ? (TileRouter) te : null;
    }

    /** 灾备时本组下东南方块 TileItemBuffer（主控持池）；非灾备返回 null。
     *  若下东南主控 TE 尚未加载/不存在，fallback 到组内任意已加载且 localPool 非空的缓存器，
     *  避免重连/TE 加载时序导致 GUI 读不到池。 */
    public TileItemBuffer getDrMaster() {
        if (!drMode || worldObj == null) return null;
        ItemBufferCluster c = ItemBufferCluster.resolve(worldObj, xCoord, yCoord, zCoord);
        if (c != null) {
            TileEntity te = worldObj.getTileEntity(c.cx, c.cy, c.cz);
            if (te instanceof TileItemBuffer) return (TileItemBuffer) te;
            // 下东南主控 TE 不在：遍历组内已加载块，找持有池的那个
            for (int[] b : c.blocks) {
                TileEntity t2 = worldObj.getTileEntity(b[0], b[1], b[2]);
                if (t2 instanceof TileItemBuffer) {
                    TileItemBuffer buf = (TileItemBuffer) t2;
                    if (!buf.localPool.isEmpty()) return buf;
                }
            }
        }
        return this;
    }

    /** 当前池内容（正常→路由器；灾备→本地主控），只读展示用 */
    public ItemPool getActivePool() {
        if (drMode) {
            TileItemBuffer m = getDrMaster();
            return m != null ? m.localPool : null;
        }
        TileRouter r = getRouterMaster();
        return r != null ? r.getPool() : null;
    }

    /** 漏斗输入入口：把物品写入有效池；返回放入数（0=灾备/未入网/满） */
    public int receiveFromFunnel(ItemStack stack, int amount) {
        if (drMode) return 0; // 灾备禁止输入
        ItemPool pool = getActivePool();
        if (pool == null) return 0;
        ItemBufferCluster c = ItemBufferCluster.resolve(worldObj, xCoord, yCoord, zCoord);
        int kindCap = ItemPool.DEFAULT_KIND_CAP, itemCap = ItemPool.DEFAULT_ITEM_CAP;
        TileRouter r = getRouterMaster();
        if (r != null) {
            kindCap = r.getPoolKindCapacity();
            itemCap = r.getPoolItemCapacity();
        } else if (c != null) {
            kindCap = c.kindCapacity;
            itemCap = c.itemCapacity;
        }
        return pool.addItemStack(stack, amount, kindCap, itemCap);
    }

    // ===== IInventory（仅供输入） =====

    @Override
    public int getSizeInventory() {
        ItemPool p = getActivePool();
        if (p == null) return 1; // 空（灾备/未入网）：给 1 空位，漏斗判定"放不进"靠 isItemValid + set 返回
        int kinds = p.getKindCount();
        int cap = 0;
        if (drMode) cap = getKindCapacity();
        else {
            TileRouter r = getRouterMaster();
            cap = r != null ? r.getPoolKindCapacity() : getKindCapacity();
        }
        return Math.min(64, Math.max(1, kinds + 1)); // 当前种类 + 1 空位
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        ItemPool p = getActivePool();
        if (p == null) return null;
        List<Object[]> pairs = p.snapshotPairs();
        if (slot >= 0 && slot < pairs.size()) {
            ItemStack s = (ItemStack) pairs.get(slot)[0];
            return s;
        }
        return null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        return null; // 禁止取出：仅允许输入
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return;
        // 漏斗放入：只写入池，不真正占用槽
        receiveFromFunnel(stack, stack.stackSize);
        if (worldObj != null) markDirty();
    }

    @Override
    public String getInventoryName() { return "container.item_buffer"; }
    @Override
    public boolean hasCustomInventoryName() { return false; }
    @Override
    public int getInventoryStackLimit() { return 4096; }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
                && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64.0;
    }

    @Override
    public void openInventory() {}
    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        // 灾备禁输入；正常允许（漏斗放得进来）
        return !drMode;
    }

    // ===== 灾备转移 =====

    /**
     * 路由器被全部破坏时调用：把本缓存器组置为灾备。
     * 【冗余存储】组内每个缓存器都存一份池到 localPool，确保重连/TE 加载时序下
     * 任意块打开 GUI 都能读到物品，回迁后统一清空所有块的 localPool。
     */
    public void enterDisasterModeFrom(ItemPool sourcePool) {
        ItemBufferCluster c = ItemBufferCluster.resolve(worldObj, xCoord, yCoord, zCoord);
        if (sourcePool != null && c != null) {
            List<ItemStack> items = sourcePool.drainAll();
            for (int[] b : c.blocks) {
                TileEntity te = worldObj.getTileEntity(b[0], b[1], b[2]);
                if (te instanceof TileItemBuffer) {
                    TileItemBuffer buf = (TileItemBuffer) te;
                    for (ItemStack s : items) {
                        // 灾备冗余存储不受容量限制（物品本来就在网络里，灾备只是换地方存）
                        buf.localPool.addItemStack(s, s.stackSize, Integer.MAX_VALUE, Integer.MAX_VALUE);
                    }
                }
            }
        }
        if (c != null) {
            for (int[] b : c.blocks) {
                TileEntity te = worldObj.getTileEntity(b[0], b[1], b[2]);
                if (te instanceof TileItemBuffer) ((TileItemBuffer) te).setDrMode(true);
            }
        } else {
            setDrMode(true);
        }
    }

    /** 灾备状态下被破坏：溢出部分掉落（由 Block 处理剩余） */
    public List<ItemStack> drainDrPoolExcess(int capacity) {
        if (!drMode) return new ArrayList<ItemStack>();
        TileItemBuffer master = getDrMaster();
        if (master == null) return new ArrayList<ItemStack>();
        ItemPool p = master.localPool;
        if (p.getTotal() <= capacity) return new ArrayList<ItemStack>(); // 塞得下，不溢出
        int overflow = p.getTotal() - capacity;
        List<ItemStack> out = new ArrayList<ItemStack>();
        // 从池末尾逐种溢出，直到不超过容量
        while (overflow > 0 && !p.isEmpty()) {
            // 简化：每次丢 1 种物品全部，直到不溢出
            List<Object[]> pairs = p.snapshotPairs();
            ItemStack s = (ItemStack) pairs.get(pairs.size() - 1)[0];
            List<ItemStack> got = p.extractItemStack(s, s.stackSize);
            for (ItemStack g : got) { out.add(g); overflow -= g.stackSize; }
        }
        return out;
    }

    /** 灾备池当前总量（供 GUI/掉落判断） */
    public int getDrPoolTotal() {
        TileItemBuffer m = getDrMaster();
        return m != null ? m.localPool.getTotal() : 0;
    }

    /** 灾备池全量物品（最后一个灾备缓存器被拆时全部掉落） */
    public List<ItemStack> getDrPoolItems() {
        TileItemBuffer m = getDrMaster();
        return m != null ? m.localPool.drainAll() : new ArrayList<ItemStack>();
    }

    /** 当前有效池的物品总量（正常→路由器池；灾备→本地池） */
    public int getActivePoolTotal() {
        ItemPool p = getActivePool();
        return p != null ? p.getTotal() : 0;
    }

    /** 当前有效池的物品容量上限（正常→路由器池容量；灾备→本组容量） */
    public int getActivePoolItemCapacity() {
        if (drMode) return getItemCapacity();
        TileRouter r = getRouterMaster();
        return r != null ? r.getPoolItemCapacity() : getItemCapacity();
    }

    // ===== 同步/NBT =====

    /** 主循环：灾备时检测是否补回了路由器，是则回迁池并退出灾备；否则 40t 推 GUI 快照 */
    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;
        // 灾备恢复：每 40t 尝试回迁到新路由器
        if (drMode && worldObj.getTotalWorldTime() % 40 == 0) {
            tryRecoverToRouter();
        }
        if (worldObj.getTotalWorldTime() % 40 == 0) {
            for (Object o : worldObj.playerEntities) {
                if (o instanceof EntityPlayerMP) {
                    EntityPlayerMP p = (EntityPlayerMP) o;
                    if (p.openContainer instanceof ContainerItemBuffer) {
                        ContainerItemBuffer c = (ContainerItemBuffer) p.openContainer;
                        if (c.te == this) {
                            MinecraftIotMod.network.sendTo(PacketItemBufferData.build(this), p);
                        }
                    }
                }
            }
        }
    }

    /**
     * 灾备恢复：BFS 找正常路由器组，把 localPool 回迁到路由器 master，本组退出灾备。
     * 安全：
     *  - 池空时先从组内其他块合并（冗余存储下可能池在非主控块）；
     *  - 组内全空才退出灾备，避免误清；
     *  - 容量校验用组物理容量（不受 drMode 影响）。
     */
    private void tryRecoverToRouter() {
        RouterCluster rc = ItemBufferCluster.findRouter(worldObj, xCoord, yCoord, zCoord);
        if (rc == null) return;
        TileEntity te = worldObj.getTileEntity(rc.cx, rc.cy, rc.cz);
        if (!(te instanceof TileRouter)) return;
        TileRouter master = (TileRouter) te;
        if (master.isDrMode()) return;
        // 只有灾备主控块执行回迁，避免组内每个块重复回迁
        TileItemBuffer drMaster = getDrMaster();
        if (drMaster == null || drMaster != this) return;
        // 冗余存储：若当前主控 localPool 空，从组内其他有池块合并过来
        if (this.localPool.isEmpty()) {
            ItemBufferCluster c = ItemBufferCluster.resolve(worldObj, xCoord, yCoord, zCoord);
            if (c != null) {
                for (int[] b : c.blocks) {
                    if (b[0] == xCoord && b[1] == yCoord && b[2] == zCoord) continue;
                    TileEntity t2 = worldObj.getTileEntity(b[0], b[1], b[2]);
                    if (t2 instanceof TileItemBuffer) {
                        TileItemBuffer buf = (TileItemBuffer) t2;
                        if (!buf.localPool.isEmpty()) {
                            for (ItemStack s : buf.localPool.drainAll()) {
                                this.localPool.addItemStack(s, s.stackSize,
                                        Integer.MAX_VALUE, Integer.MAX_VALUE);
                            }
                            break;
                        }
                    }
                }
            }
        }
        ItemPool drPool = this.localPool;
        if (drPool.isEmpty()) {
            // 组内全空：真正空灾备，退出灾备
            exitDisasterMode();
            return;
        }
        // 容量校验：用缓存器组物理容量（blockCount×4096），不使用路由器在线容量
        int expectCap = this.getItemCapacity();
        if (expectCap <= 0 || expectCap < drPool.getTotal()) return;
        // 先取出灾备池物品
        List<ItemStack> drained = drPool.drainAll();
        // 【关键】先退出灾备：所有缓存器 setDrMode(false) + clear localPool。
        // 退出后缓存器变正常，路由器在线容量（=本组 blockCount×4096）恢复，
        // 否则 master.addItemStack 内部检查 getPoolItemCapacity()=0（灾备缓存器被排除），
        // 导致所有物品回迁失败进 leftover，再被 exitDisasterMode 清空 → 物品丢失。
        exitDisasterMode();
        // 再回迁到路由器（此时在线容量已恢复）
        List<ItemStack> leftover = new ArrayList<ItemStack>();
        for (ItemStack s : drained) {
            int put = master.addItemStack(s, s.stackSize);
            if (put < s.stackSize) {
                ItemStack rest = s.copy();
                rest.stackSize = s.stackSize - put;
                leftover.add(rest);
            }
        }
        // 仍放不下的（理论上不会发生）掉落为物品实体，不留在任何缓存器
        if (!leftover.isEmpty()) {
            for (ItemStack s : leftover) {
                dropItem(s);
            }
        }
    }

    /** 在本块位置掉落物品实体 */
    private void dropItem(ItemStack s) {
        if (worldObj == null || s == null || s.stackSize <= 0) return;
        net.minecraft.entity.item.EntityItem ei = new net.minecraft.entity.item.EntityItem(
                worldObj, xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, s);
        ei.delayBeforeCanPickup = 10;
        worldObj.spawnEntityInWorld(ei);
    }

    /** 本组所有缓存器退出灾备状态，并清空所有块的冗余 localPool */
    private void exitDisasterMode() {
        ItemBufferCluster c = ItemBufferCluster.resolve(worldObj, xCoord, yCoord, zCoord);
        if (c == null) { setDrMode(false); localPool.clear(); return; }
        for (int[] b : c.blocks) {
            TileEntity te = worldObj.getTileEntity(b[0], b[1], b[2]);
            if (te instanceof TileItemBuffer) {
                TileItemBuffer buf = (TileItemBuffer) te;
                buf.localPool.clear();
                buf.setDrMode(false);
            }
        }
    }

    private void sync() {
        if (worldObj != null && !worldObj.isRemote) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("DrMode", drMode);
        localPool.writeToNBT(tag, "Local");
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        drMode = tag.getBoolean("DrMode");
        localPool.readFromNBT(tag, "Local");
    }
}
