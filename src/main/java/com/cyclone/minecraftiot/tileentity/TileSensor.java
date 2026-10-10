package com.cyclone.minecraftiot.tileentity;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.item.ItemConnector;
import com.cyclone.minecraftiot.network.PacketSensorData;
import com.cyclone.minecraftiot.util.BoilerAggregator;
import com.cyclone.minecraftiot.util.BoilerAggregator.BoilerData;
import com.cyclone.minecraftiot.util.FluidHandlerAggregator;
import com.cyclone.minecraftiot.util.GtMultiblockAggregator;
import com.cyclone.minecraftiot.util.Ic2ReactorAggregator;
import com.cyclone.minecraftiot.util.Ic2Runtime;
import com.cyclone.minecraftiot.util.MultiblockResolver;
import com.cyclone.minecraftiot.util.NbtVariableUtil;
import com.cyclone.minecraftiot.util.TankAggregator;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.List;

public class TileSensor extends TileEntity implements IInventory {
    private static final int SCAN_INTERVAL = 40;
    private String[] scanResults = new String[6]; // 六个面的"预设模板"友好摘要（内置默认：自动组合变量 / 锅炉蓄水器聚合）
    private String[] rawResults = new String[6];  // 六个面的原始 NBT 摘要（供 GUI 切换查看）
    private BoilerData[] boilerData = new BoilerData[6]; // 六个面的 Railcraft 锅炉聚合数据（服务端算好下发）
    private NBTTagCompound[] rawNbt = new NBTTagCompound[6]; // 六个面服务端完整 NBT（供客户端编辑器拍平 / 显示器表达式求值）
    private boolean isBound = false;
    private boolean firstScan = true; // 放置后立即扫描一次，避免时序问题

    // connector 槽位（第 0 格，只允许放 ItemConnector）
    private ItemStack connectorStack;

    // 玩家自定义注释：按方向（VALID_DIRECTIONS 索引 0..5）各一条，
    // 因为传感器六个面分别指向不同机器，注释应跟随各自方向。
    private String[] labels = new String[6];

    public String getLabel(int dir) {
        if (dir < 0 || dir >= labels.length) return "";
        return labels[dir] == null ? "" : labels[dir];
    }

    public void setLabel(int dir, String label) {
        if (dir < 0 || dir >= labels.length) return;
        labels[dir] = label == null ? "" : label;
        markDirty();
    }

    @Override
    public void updateEntity() {
        if (!worldObj.isRemote && (firstScan || worldObj.getTotalWorldTime() % SCAN_INTERVAL == 0)) {
            firstScan = false;
            scanSurroundings();
            syncScanToClient();
        }
    }

    /** 把扫描结果 + 原始NBT + 每方向注释 + 锅炉聚合数据 + 完整NBT 推送给客户端 */
    private void syncScanToClient() {
        if (worldObj == null || worldObj.isRemote) return;
        MinecraftIotMod.network.sendToDimension(new PacketSensorData(xCoord, yCoord, zCoord, scanResults, rawResults, labels, boilerData, rawNbt), worldObj.provider.dimensionId);
    }

    /** 客户端侧：接收服务端推送的扫描结果、原始NBT、完整NBT、每方向注释与锅炉聚合数据（仅客户端调用） */
    public void setClientData(String[] arr, String[] raw, String[] lbls, BoilerData[] boiler, NBTTagCompound[] nbtArr) {
        this.scanResults = arr;
        if (raw != null) this.rawResults = raw;
        if (boiler != null) this.boilerData = boiler;
        if (nbtArr != null) this.rawNbt = nbtArr;
        if (lbls != null) {
            for (int i = 0; i < 6 && i < lbls.length; i++) {
                if (lbls[i] != null) labels[i] = lbls[i];
            }
        }
    }

    /** 客户端读取某方向的锅炉聚合数据（服务端下发），模板编辑器用于注入虚拟变量 */
    public BoilerData getBoilerData(int dir) {
        if (dir < 0 || dir >= boilerData.length) return null;
        return boilerData[dir];
    }

    /** 客户端读取某方向的完整 NBT（服务端下发），模板编辑器用于拍平变量列表 */
    public NBTTagCompound getRawNbt(int dir) {
        if (dir < 0 || dir >= rawNbt.length) return null;
        return rawNbt[dir];
    }

    /** 供"写入绑定卡"按钮（服务端）读取槽位内的 connector */
    public ItemStack getConnectorStack() {
        return connectorStack;
    }

    /** 取某方向指向的机器（带物品栏的 TileEntity），供模板编辑器解析 NBT；多方块解析到主控 */
    public TileEntity getTargetTileEntity(int dir) {
        if (worldObj == null || dir < 0 || dir >= 6) return null;
        ForgeDirection d = ForgeDirection.VALID_DIRECTIONS[dir];
        TileEntity te = worldObj.getTileEntity(xCoord + d.offsetX, yCoord + d.offsetY, zCoord + d.offsetZ);
        if (te == null) return null;
        // 多方块（Railcraft 锅炉等）：从属方块解析到主控。锅炉方块本身不是 IInventory，
        // 但解析出的主控燃烧室是，因此先解析再判断
        TileEntity master = MultiblockResolver.resolve(te);
        if (master instanceof IInventory) return master;
        if (te instanceof IInventory) return te;
        if (isRuntimeScannable(te)) return te;
        return null;
    }

    /**
     * 运行时方块（非物品栏，但 NBT 里含可展示数据，值得纳入扫描）：
     * - BuildCraft 管道 TileGenericPipe：运输中的物品/速度写进 NBT 的 travelingEntities 列表
     * - IC2 导线 TileEntityCable：cableType/color/foamed 等配置写进 NBT（电流在 EnergyNet，NBT 无）
     * 按类名识别，避免对未安装 mod 产生硬依赖。
     */
    private static boolean isRuntimeScannable(TileEntity te) {
        if (te == null) return false;
        String n = te.getClass().getName();
        return n.equals("buildcraft.transport.TileGenericPipe")
                || n.startsWith("ic2.core.block.wiring.");
    }

    private void scanSurroundings() {
        ForgeDirection[] dirs = ForgeDirection.VALID_DIRECTIONS;
        for (int i = 0; i < 6; i++) {
            scanResults[i] = null;
            int targetX = xCoord + dirs[i].offsetX;
            int targetY = yCoord + dirs[i].offsetY;
            int targetZ = zCoord + dirs[i].offsetZ;
            Block block = worldObj.getBlock(targetX, targetY, targetZ);
            TileEntity te = worldObj.getTileEntity(targetX, targetY, targetZ);
            // 多方块：Railcraft 锅炉等从属方块解析到主控（不限于 IInventory——锅炉方块不是物品栏方块）
            if (te != null) {
                TileEntity master = MultiblockResolver.resolve(te);
                if (master != null) te = master;
            }
            // 格雷科技多方块：主控显式（BaseMetaTileEntity+MultiBlockBase）；外壳无 TileEntity，
            // 从外壳反查候选主控（多个时距离近者优先、等距按下东南定律，见 GtMultiblockAggregator）；
            // 总线/仓等部件方块通过主控 hatch 列表精确归属所属机器
            boolean gtCasing = GtMultiblockAggregator.isGtCasing(block);
            TileEntity gtMaster = null;
            if (GtMultiblockAggregator.isGtMultiController(te)) {
                gtMaster = te;
            } else if (te != null && GtMultiblockAggregator.isGtPart(te)) {
                gtMaster = GtMultiblockAggregator.findMasterForPart(worldObj, targetX, targetY, targetZ, te);
            } else if (gtCasing && te == null) {
                gtMaster = GtMultiblockAggregator.findMaster(worldObj, targetX, targetY, targetZ, targetX, targetY, targetZ);
            }
            boolean gt = gtMaster != null;
            // IC2 核反应堆：核心自身或反应仓（反应仓经 getReactor() 解析到核心）
            TileEntity reactorCore = Ic2ReactorAggregator.resolveCore(te);
            boolean reactor = reactorCore != null;
            // 只展示"有 GUI"的方块（带物品栏的 TileEntity，如箱子/熔炉/漏斗等）
            // 以及 Railcraft 锅炉（多方块流体聚合展示）和运行时方块（BC 管道 / IC2 导线，
            // 管道物品/速度、导线类型等写进 NBT 可读）
            boolean iinv = te instanceof IInventory;
            boolean boiler = BoilerAggregator.isBoilerTile(te);
            boolean tank = TankAggregator.isTankTile(te);
            boolean fluid = FluidHandlerAggregator.isFluidHandler(te); // 无物品格但带水箱：蒸汽引擎/涡轮/通用储罐
            boolean special = isRuntimeScannable(te);
            if (reactor || gt || iinv || boiler || tank || fluid || special) {
                boilerData[i] = BoilerAggregator.aggregate(te); // 仅锅炉非 null，供客户端编辑器/显示使用
                NBTTagCompound nbt = null; // 服务端完整 NBT（含 IC2 合成键），供格式化/编辑器解析
                boolean hasRaw = iinv || special || tank || fluid; // 蓄水器阀门 writeToNBT 也含 tanks 数据，可作原始 NBT
                if (reactor) {
                    // IC2 核反应堆：核心 NBT + 虚拟键（Heat/EUOutput/Rod{i}_* 等）
                    nbt = Ic2ReactorAggregator.buildNbt(reactorCore);
                    hasRaw = true;
                    rawNbt[i] = nbt;
                    rawResults[i] = "[" + dirs[i].name() + "] " + block.getLocalizedName() + " " + extractKeyInfo(nbt);
                } else if (gt) {
                    // 格雷多方块：主控自身 NBT + 聚合虚拟键（Progress/EUt/Input*/FluidIn* 等）
                    nbt = GtMultiblockAggregator.buildNbt(gtMaster);
                    hasRaw = true;
                    rawNbt[i] = nbt;
                    rawResults[i] = "[" + dirs[i].name() + "] " + block.getLocalizedName() + " " + extractKeyInfo(nbt);
                } else if (hasRaw) {
                    nbt = new NBTTagCompound();
                    te.writeToNBT(nbt);
                    if (tank) {
                        TankAggregator.injectVirtualData(te, nbt); // 蓄水器注入 Tank 虚拟键
                    } else if (fluid) {
                        FluidHandlerAggregator.injectVirtualData(te, nbt); // 蒸汽引擎等通用液体容器注入 Tank 虚拟键
                    }
                    rawNbt[i] = nbt; // 服务端完整 NBT 随包下发，客户端模板编辑器用它拍平变量
                    rawResults[i] = "[" + dirs[i].name() + "] " + block.getLocalizedName() + " " + extractKeyInfo(nbt);
                    // IC2 运行时能量（导线电流/机器进出电量）：由 EnergyNet 每 tick 求解，不在 NBT 里，
                    // 复用官方电表读取方式 EnergyNet.instance.getNodeStats(tile) 取当 tick 已算好的统计。
                    if (Ic2Runtime.isIc2EnergyTile(te)) {
                        double[] eu = Ic2Runtime.readStats(te); // [EUIn, EUOut, Voltage]
                        Ic2Runtime.injectIntoNbt(nbt, eu);       // 合成键 EUIn/EUOut/Voltage 供模板/表达式引用
                        if (eu != null) {
                            rawResults[i] = rawResults[i] + " | EUIn=" + fmtNum(eu[0])
                                    + " EUOut=" + fmtNum(eu[1]) + " Voltage=" + fmtNum(eu[2]);
                        }
                    }
                } else {
                    rawNbt[i] = null;
                    rawResults[i] = null; // 非物品栏方块（如锅炉方块）无原始 NBT 展示
                }
                // 预设模板（内置默认友好方案，不再依赖 minecraftiot.json 配置文件）：
                // 锅炉/蓄水器/通用液体容器 → 结构聚合展示（温度/水/蒸汽、液体量/容量）；
                // 格雷多方块 → 整机聚合（进度/电压/效率/输入输出）；
                // 其余方块 → 按 NBT 自动组合变量为 "路径: 值" 多行文本。
                // 传感器自身不再提供用户自定义表达式；自定义配置由连接器工作台写入卡、显示器端套用。
                String tankSum = tank ? TankAggregator.summarize(te) : null;
                if (tankSum == null && fluid) tankSum = FluidHandlerAggregator.summarize(te); // 引擎/涡轮等
                BoilerData bd = boilerData[i];
                String def = buildDefaultLines(nbt);
                scanResults[i] = reactor ? Ic2ReactorAggregator.summarize(reactorCore)
                        : (gt ? GtMultiblockAggregator.summarize(gtMaster)
                        : (tankSum != null ? tankSum
                        : (bd != null ? BoilerAggregator.summarize(bd)
                        : (hasRaw ? def : null))));
            }
        }
        // 诊断日志：输出本次扫描结果
        MinecraftIotMod.log.info("[TileSensor@" + xCoord + "," + yCoord + "," + zCoord + "] scan=" + joinNonNull(scanResults));
    }

    private String joinNonNull(String[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] != null) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append(arr[i]);
            }
        }
        return sb.length() == 0 ? "(empty)" : sb.toString();
    }

    /** 预设模板：把 NBT 拍平的变量自动组合成 "路径: 值" 多行友好文本（无变量返回 null） */
    private String buildDefaultLines(NBTTagCompound nbt) {
        if (nbt == null) return null;
        List<NbtVariableUtil.Var> vars = NbtVariableUtil.flatten(nbt);
        if (vars == null || vars.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (NbtVariableUtil.Var v : vars) {
            if (v == null || v.path == null || v.path.isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(v.path).append(": ").append(v.value == null ? "" : v.value);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * 取某方向指向的"可扫描"目标（供工作台解析非物品栏方块，如 RC 蓄水器阀门）。
     * getTargetTileEntity 只认 IInventory/运行时方块；蓄水器等液体容器类补充在这里。
     */
    public TileEntity getScanTarget(int dir) {
        if (worldObj == null || dir < 0 || dir >= 6) return null;
        ForgeDirection d = ForgeDirection.VALID_DIRECTIONS[dir];
        int tx = xCoord + d.offsetX, ty = yCoord + d.offsetY, tz = zCoord + d.offsetZ;
        TileEntity te = worldObj.getTileEntity(tx, ty, tz);
        if (te == null) {
            // GT 机械外壳（无 TileEntity）：反查多方块主控（多候选距离近者优先、等距按定律）
            Block b = worldObj.getBlock(tx, ty, tz);
            if (GtMultiblockAggregator.isGtCasing(b)) {
                return GtMultiblockAggregator.findMaster(worldObj, tx, ty, tz, tx, ty, tz);
            }
            return null;
        }
        // GT 多方块主控（显式）
        if (GtMultiblockAggregator.isGtMultiController(te)) return te;
        // GT 部件方块（总线/仓）：按 hatch 列表精确归属
        if (GtMultiblockAggregator.isGtPart(te)) {
            return GtMultiblockAggregator.findMasterForPart(worldObj, tx, ty, tz, te);
        }
        // IC2 核反应堆：核心自身或反应仓（解析到核心）
        TileEntity core = Ic2ReactorAggregator.resolveCore(te);
        if (core != null) return core;
        TileEntity master = MultiblockResolver.resolve(te);
        if (master != null && master != te && TankAggregator.isTankTile(master)) return master;
        if (TankAggregator.isTankTile(te)) return te;
        // 通用液体容器（蒸汽引擎/涡轮/储罐等）：主控或自身任一为 IFluidHandler 即可
        if (master != null && master != te && FluidHandlerAggregator.isFluidHandler(master)) return master;
        if (FluidHandlerAggregator.isFluidHandler(te)) return te;
        return null;
    }

    /** 取某方向的"预设模板"友好摘要（服务端显示器/工作台用） */
    public String getScanResult(int dir) {
        if (dir < 0 || dir >= scanResults.length) return null;
        return scanResults[dir];
    }

    /** 取某方向的原始 NBT 摘要（显示器"原始NBT"模式用） */
    public String getRawResult(int dir) {
        if (dir < 0 || dir >= rawResults.length) return null;
        return rawResults[dir];
    }

    private String extractKeyInfo(NBTTagCompound nbt) {
        // 针对不同方块提取关键信息，例如箱子物品数量，机器能量等
        // 简单返回NBT的toString，截断上限设为 1000 字符（原为 300，内容太少）
        String s = nbt.toString();
        return s.length() > 1000 ? s.substring(0, 1000) + "..." : s;
    }

    private static String fmtNum(double v) {
        if (Math.abs(v) < 1e-4) return "0";
        return String.format("%.1f", v);
    }

    public String[] getScanResults() {
        return scanResults;
    }

    public String[] getRawResults() {
        return rawResults;
    }

    // 绑定方法：把放入槽位的 connector 绑定到本传感器坐标 + 选中的方向（只在服务端执行）
    // dirIndex 对应 VALID_DIRECTIONS 索引 0..5。连接器只支持绑定单一方向，非法值（<0 或 >5）直接拒绝。
    // name 为玩家自定义卡名（可为空），非空时写入 display.Name，便于在背包中区分不同绑定卡
    public void bindConnector(ItemStack stack, int dirIndex, String name) {
        if (worldObj == null || worldObj.isRemote) return;
        if (dirIndex < 0 || dirIndex > 5) {
            MinecraftIotMod.log.warn("[TileSensor@" + xCoord + "," + yCoord + "," + zCoord + "] bindConnector rejected: dirIndex=" + dirIndex + " (must be 0..5, single direction only)");
            return;
        }
        if (stack != null && stack.getItem() instanceof ItemConnector) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null) tag = new NBTTagCompound();
            tag.setInteger("sensorX", xCoord);
            tag.setInteger("sensorY", yCoord);
            tag.setInteger("sensorZ", zCoord);
            tag.setInteger("dimension", worldObj.provider.dimensionId);
            tag.setInteger("dirIndex", dirIndex); // 记录绑定的方向索引
            if (name != null && !name.isEmpty()) {
                NBTTagCompound display = tag.hasKey("display") ? tag.getCompoundTag("display") : new NBTTagCompound();
                display.setString("Name", name); // 自定义卡名，物品栏 tooltip 会显示
                tag.setTag("display", display);
            }
            stack.setTagCompound(tag);
            MinecraftIotMod.log.info("[TileSensor@" + xCoord + "," + yCoord + "," + zCoord + "] bindConnector -> dim=" + worldObj.provider.dimensionId + " dirIndex=" + dirIndex + " name=" + name);
        }
    }

    // ============ IInventory（connector 槽位）============

    @Override
    public int getSizeInventory() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot == 0 ? connectorStack : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slot == 0 && connectorStack != null) {
            ItemStack stack;
            if (connectorStack.stackSize <= amount) {
                stack = connectorStack;
                connectorStack = null;
                markDirty();
                return stack;
            } else {
                stack = connectorStack.splitStack(amount);
                if (connectorStack.stackSize == 0) {
                    connectorStack = null;
                }
                markDirty();
                return stack;
            }
        }
        return null;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        // 1.7.10 该回调不应清空槽位：置 null 会让卡在部分容器回调路径下意外消失
        // （表现为打开 GUI 时卡"拿不出来"）。取走卡只应走 decrStackSize/setInventorySlotContents。
        return slot == 0 ? connectorStack : null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot == 0) {
            connectorStack = stack;
            if (stack != null && stack.stackSize > getInventoryStackLimit()) {
                stack.stackSize = getInventoryStackLimit();
            }
            markDirty();
        }
    }

    @Override
    public String getInventoryName() {
        return "container.sensor";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1; // connector 槽位每次放一个，绑定更清晰
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
                && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64.0;
    }

    @Override
    public void openInventory() {
    }

    @Override
    public void closeInventory() {
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return slot == 0 && stack != null && stack.getItem() instanceof ItemConnector;
    }

    // 槽位内容持久化
    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (connectorStack != null) {
            NBTTagCompound itemTag = new NBTTagCompound();
            connectorStack.writeToNBT(itemTag);
            tag.setTag("ConnectorStack", itemTag);
        }
        // 每方向注释：非空才写入
        for (int i = 0; i < 6; i++) {
            if (labels[i] != null && !labels[i].isEmpty()) tag.setString("Label" + i, labels[i]);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("ConnectorStack")) {
            connectorStack = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("ConnectorStack"));
        }
        for (int i = 0; i < 6; i++) {
            if (tag.hasKey("Label" + i)) labels[i] = tag.getString("Label" + i);
        }
        // 旧版单一 "Label" 迁移：老存档的整传感器注释保留到全部方向，用户可逐面清除
        if (tag.hasKey("Label")) {
            String legacy = tag.getString("Label");
            for (int i = 0; i < 6; i++) {
                if (labels[i] == null || labels[i].isEmpty()) labels[i] = legacy;
            }
        }
    }
}
