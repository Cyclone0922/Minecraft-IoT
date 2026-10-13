package com.cyclone.minecraftiot.tileentity;

import com.cyclone.minecraftiot.util.EnergyDualTrack;
import com.cyclone.minecraftiot.util.ItemBufferCluster;
import com.cyclone.minecraftiot.util.ItemPool;
import com.cyclone.minecraftiot.util.MacAddress;
import com.cyclone.minecraftiot.util.RouterCluster;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import java.util.List;

/**
 * 路由器 TileEntity（0.5.0 扩展为网络物品池核心）。
 *
 * 职责：
 * - 网络根节点，持有 IE 能量缓存（EnergyDualTrack）
 * - 0.5.0：持有网络物品池（ItemPool），缓存器只贡献容量
 * - 出厂生成固定 MAC 地址，可配置别名
 *
 * 物品池规则：
 * - 池集中存在路由器组【下东南角】主控 TileRouter 里
 * - 容量 = 在线缓存器 blockCount × (4096/64)；路由器自身不存储（容量 0，仅转运）
 * - 路由器组被拆成非法（非立方体）→ 组不可组网（执行器解绑=网络瘫痪），但池数据
 *   仍保存在旧主控 TileRouter 的 NBT 里不丢，补块恢复后自动迁移到新主控
 *
 * 灾备（drMode）：
 * - 容量爆掉（缓存器被拆光导致池超过容量）→ drMode=true，断开所有设备连接，
 *   物品仍留在路由器池；容量恢复（加回缓存器）后自动恢复
 */
public class TileRouter extends TileEntity {

    private static final int SCAN_INTERVAL = 40;
    private static final double IE_CAP_PER_BLOCK = 10000.0; // 每块路由器贡献 10000 IE 容量

    private final EnergyDualTrack energy = new EnergyDualTrack(IE_CAP_PER_BLOCK);
    private String mac = null;       // 出厂生成，固定
    private String alias = "";        // 玩家可配置别名
    private int level = 1;            // 路由器等级（P1 固定 1）

    // ===== 0.5.0 物品池 =====
    private ItemPool pool = new ItemPool();
    private boolean drMode = false;  // 容量爆掉灾备
    private boolean firstTick = true;

    public TileRouter() {}

    /**
     * 返回本组主控 TileRouter（下东南角 = maxX, minY, maxZ）。
     * 能量/物品池集中存在主控上；任意方块调用此方法转发到主控。
     * 组非法（null）时返回自身兜底。
     */
    public TileRouter getMaster() {
        RouterCluster c = RouterCluster.resolve(worldObj, xCoord, yCoord, zCoord);
        if (c == null) return this;
        TileEntity te = worldObj.getTileEntity(c.cx, c.cy, c.cz);
        if (te instanceof TileRouter) return (TileRouter) te;
        return this;
    }

    /** 是否是本组主控（下东南角） */
    public boolean isMaster() {
        RouterCluster c = RouterCluster.resolve(worldObj, xCoord, yCoord, zCoord);
        if (c == null) return true; // 非法组：自身即主控
        return xCoord == c.cx && yCoord == c.cy && zCoord == c.cz;
    }

    /** 本组块数（主控用它算能量容量） */
    public int getBlockCount() {
        RouterCluster c = RouterCluster.resolve(worldObj, xCoord, yCoord, zCoord);
        return c == null ? 1 : c.blockCount;
    }

    /** 懒生成 MAC：第一次读取时如果还没有就生成一个 */
    public String getMac() {
        if (mac == null) {
            mac = MacAddress.generate();
            markDirty();
        }
        return mac;
    }

    public String getAlias() { return alias == null ? "" : alias; }

    public void setAlias(String a) {
        this.alias = a == null ? "" : a;
        markDirty();
    }

    public int getLevel() { return level; }

    /** 返回主控的能量字段（任意方块都转发） */
    public EnergyDualTrack getEnergy() { return getMaster().energy; }

    /** 供外部能量输入调用：amount 为外部能量单位，1:1 转 IE，返回实际转入量 */
    public double acceptEnergy(double amount) {
        TileRouter m = getMaster();
        // 同步容量 = 块数 × 每块容量
        m.energy.setIeCapacity(m.getBlockCount() * IE_CAP_PER_BLOCK);
        double got = m.energy.addIe(amount);
        if (got > 0) {
            m.markDirty();
            if (m.worldObj != null) m.worldObj.markBlockForUpdate(m.xCoord, m.yCoord, m.zCoord);
        }
        return got;
    }

    /** 设备请求消耗 IE（执行器等调用），返回是否成功 */
    public boolean requestEnergy(double amount) {
        TileRouter m = getMaster();
        m.energy.setIeCapacity(m.getBlockCount() * IE_CAP_PER_BLOCK);
        if (m.energy.consumeIe(amount)) {
            m.markDirty();
            if (m.worldObj != null) m.worldObj.markBlockForUpdate(m.xCoord, m.yCoord, m.zCoord);
            return true;
        }
        return false;
    }

    // ===== 0.5.1 物品转移耗能 =====
    /** 基础耗能（IE/物品） */
    public static final double BASE_COST_PER_ITEM = 5.0;
    /** 每多 16 格距离追加耗能（IE/物品） */
    public static final double DIST_COST_PER_16 = 2.0;
    /** 速度：每 8 tick 转移 1 个物品（=2.5 物品/秒） */
    public static final int TRANSFER_INTERVAL_TICKS = 8;

    /** 计算执行器到路由器主控 distBlock 格时，单件物品转移耗能 */
    public static double costPerItem(int distBlock) {
        return BASE_COST_PER_ITEM + DIST_COST_PER_16 * Math.floor(distBlock / 16.0);
    }

    /** 测试用：直接给路由器充 IE（GUI 输入框调用，转发到主控） */
    public double chargeIe(double amount) {
        TileRouter m = getMaster();
        m.energy.setIeCapacity(m.getBlockCount() * IE_CAP_PER_BLOCK);
        double got = m.energy.addIe(amount);
        if (got > 0) {
            m.markDirty();
            if (m.worldObj != null) m.worldObj.markBlockForUpdate(m.xCoord, m.yCoord, m.zCoord);
        }
        return got;
    }

    // ===== 0.5.0 物品池访问 =====

    public ItemPool getPool() { return pool; }
    public boolean isDrMode() { return drMode; }

    /** 网络物品总量上限：路由器自身不存储（容量 0），仅由在线缓存器 blockCount×4096 提供 */
    public int getPoolItemCapacity() {
        return onlineBufferItemCap();
    }

    /** 网络物品种类上限：路由器自身不存储（容量 0），仅由在线缓存器 blockCount×64 提供 */
    public int getPoolKindCapacity() {
        return onlineBufferKindCap();
    }

    private int onlineBufferItemCap() {
        int n = 0;
        if (worldObj == null || worldObj.isRemote) return 0;
        for (Object o : worldObj.loadedTileEntityList) {
            if (o instanceof TileItemBuffer) {
                TileItemBuffer b = (TileItemBuffer) o;
                if (b.isDrMode()) continue; // 灾备缓存器不计入正常容量
                RouterCluster rc = ItemBufferCluster.findRouter(worldObj, b.xCoord, b.yCoord, b.zCoord);
                if (rc != null && rc.cx == xCoord && rc.cy == yCoord && rc.cz == zCoord) {
                    n += ItemBufferCluster.PER_BLOCK_ITEM_CAP;
                }
            }
        }
        return n;
    }

    private int onlineBufferKindCap() {
        int n = 0;
        if (worldObj == null || worldObj.isRemote) return 0;
        for (Object o : worldObj.loadedTileEntityList) {
            if (o instanceof TileItemBuffer) {
                TileItemBuffer b = (TileItemBuffer) o;
                if (b.isDrMode()) continue;
                RouterCluster rc = ItemBufferCluster.findRouter(worldObj, b.xCoord, b.yCoord, b.zCoord);
                if (rc != null && rc.cx == xCoord && rc.cy == yCoord && rc.cz == zCoord) {
                    n += ItemBufferCluster.PER_BLOCK_KIND_CAP;
                }
            }
        }
        return n;
    }

    /** 放入物品到网络池；返回实际放入数（0=容量满/灾备/无缓存器）。
     *  路由器自身不存储：当网络中没有在线缓存器时（容量=0）直接拒绝，take() 无存储目的地。 */
    public int addItemStack(ItemStack stack, int amount) {
        if (drMode) return 0;
        if (getPoolItemCapacity() <= 0 || getPoolKindCapacity() <= 0) return 0;
        int put = pool.addItemStack(stack, amount, getPoolKindCapacity(), getPoolItemCapacity());
        if (put > 0) markDirty();
        return put;
    }

    /** 从网络池取出物品；返回取出的 ItemStack 列表 */
    public List<ItemStack> extractItemStack(ItemStack want, int amount) {
        if (drMode) return new java.util.ArrayList<ItemStack>();
        List<ItemStack> out = pool.extractItemStack(want, amount);
        if (!out.isEmpty()) markDirty();
        return out;
    }

    /**
     * 取本组主控 TileRouter（下东南角），并把组内其他路由器上的池合并到主控。
     * 组非法（非立方体/被拆散）返回 null（网络瘫痪，池暂留旧主控）。
     */
    public static TileRouter masterOf(com.cyclone.minecraftiot.util.RouterCluster rc, net.minecraft.world.World world) {
        if (rc == null) return null;
        TileEntity te = world.getTileEntity(rc.cx, rc.cy, rc.cz);
        if (!(te instanceof TileRouter)) return null;
        TileRouter master = (TileRouter) te;
        // 合并组内其他路由器遗留池（迁移场景）
        for (int[] b : rc.blocks) {
            if (b[0] == rc.cx && b[1] == rc.cy && b[2] == rc.cz) continue;
            TileEntity o = world.getTileEntity(b[0], b[1], b[2]);
            if (o instanceof TileRouter) {
                TileRouter other = (TileRouter) o;
                if (!other.pool.isEmpty()) {
                    for (ItemStack s : other.pool.drainAll()) {
                        master.pool.addItemStack(s, s.stackSize,
                                master.getPoolKindCapacity(), master.getPoolItemCapacity());
                    }
                    other.markDirty();
                    master.markDirty();
                }
            }
        }
        return master;
    }

    // ===== 主循环：容量溢出灾备门控 =====

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) return;
        if (firstTick || worldObj.getTotalWorldTime() % SCAN_INTERVAL == 0) {
            firstTick = false;
            checkOverflow();
        }
    }

    /** 每 40t：若池总量/种类超过当前容量 → 进入灾备并断开所有设备；恢复 → 恢复 */
    private void checkOverflow() {
        boolean overflow = pool.getTotal() > getPoolItemCapacity() || pool.getKindCount() > getPoolKindCapacity();
        if (overflow && !drMode) {
            drMode = true;
            disconnectAllDevices();
            markDirty();
        } else if (!overflow && drMode) {
            drMode = false;
            markDirty();
        }
    }

    /** 广播本组所有执行器解绑（灾备门控） */
    public void disconnectAllDevices() {
        if (worldObj == null || worldObj.isRemote) return;
        for (Object o : worldObj.loadedTileEntityList) {
            if (o instanceof TileActuator) {
                TileActuator a = (TileActuator) o;
                if (a.isRouterBound() && a.getRouterX() == xCoord && a.getRouterY() == yCoord && a.getRouterZ() == zCoord) {
                    a.clearRouter();
                }
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        energy.writeToNBT(tag, "Router");
        if (mac != null) tag.setString("Mac", mac);
        if (alias != null && !alias.isEmpty()) tag.setString("Alias", alias);
        tag.setInteger("Level", level);
        pool.writeToNBT(tag, "Net");
        tag.setBoolean("DrMode", drMode);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        energy.readFromNBT(tag, "Router");
        if (tag.hasKey("Mac")) mac = tag.getString("Mac");
        if (tag.hasKey("Alias")) alias = tag.getString("Alias");
        if (tag.hasKey("Level")) level = tag.getInteger("Level");
        pool.readFromNBT(tag, "Net");
        drMode = tag.getBoolean("DrMode");
    }

    // ===== 0.5.1 客户端同步（IE 显示） =====
    @Override
    public net.minecraft.network.Packet getDescriptionPacket() {
        TileRouter m = getMaster();
        NBTTagCompound tag = new NBTTagCompound();
        m.energy.writeToNBT(tag, "Router");
        return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(m.xCoord, m.yCoord, m.zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
        TileRouter m = getMaster();
        m.energy.readFromNBT(pkt.func_148857_g(), "Router");
    }
}

