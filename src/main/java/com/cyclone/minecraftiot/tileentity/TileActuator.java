package com.cyclone.minecraftiot.tileentity;

import com.cyclone.minecraftiot.item.ItemConnector;
import com.cyclone.minecraftiot.item.ItemSkill;
import com.cyclone.minecraftiot.item.ItemSkillRedstone;
import com.cyclone.minecraftiot.util.MultiblockResolver;
import com.cyclone.minecraftiot.util.SignalExpr;
import com.cyclone.minecraftiot.util.SignalValue;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 普通执行器 TileEntity（P1）。
 *
 * 结构：
 * - 槽位 0：绑定卡（ItemConnector）—— 携带目标机器坐标+方向，执行器读取其 NBT
 * - 槽位 1：技能插件（ItemSkill）—— P1 只支持 1 种插件，普通执行器六面均为此技能
 * - 六面各自独立配置：条件表达式 + 红石强度（P1 只有红石技能）
 *
 * 工作流程（每 40 tick / 2s）：
 *   1. 若插了绑定卡，读取卡指向机器的当前 NBT
 *   2. 若插了红石技能插件，对每面求值条件表达式
 *   3. 条件为真的面输出对应红石强度，否则输出 0
 *   4. 通知相邻方块更新红石
 *
 * 红石信号操作不耗能（决策）。
 */
public class TileActuator extends TileEntity implements IInventory {

    private static final int SCAN_INTERVAL = 40;
    private static final int SIGNAL_INTERVAL = 2;
    private static final int SLOT_CONNECTOR = 0;
    private static final int SLOT_SKILL = 1;

    private ItemStack connectorStack;
    private ItemStack skillStack;

    // 六面配置：条件表达式 + 红石强度（0~15）
    private String[] conditions = new String[6];
    private int[] redstoneLevels = new int[6];
    // 技能0（信号域）：输出表达式（可空）——方向合一模型：输出永远从本面出，无独立输出方向
    private String[] outputExprs = new String[6];
    // 六面当前输出（服务端计算，用于红石查询）
    private int[] currentOutput = new int[6];

    // ===== 技能0 信号域运行时状态（服务端） =====
    private SignalValue[] outBuffer = new SignalValue[6];   // 每面最近输出值（邻居/总线读取）
    private SignalValue[] inBuffer = new SignalValue[6];    // 每面最近收到的输入值（快照，防振荡）
    private SignalValue remoteInputMain;                    // 绑卡远端（目标执行器）信号值，兜底输入
    private SignalValue signalOutCache;                     // 最近一次输出（写 NBT 主键 SignalOut）
    private NBTTagCompound targetNbtCache;                  // 绑卡目标 NBT 快照（每 40t 刷新）

    private boolean firstTick = true;

    public TileActuator() {
        for (int i = 0; i < 6; i++) {
            conditions[i] = "";
            redstoneLevels[i] = 15;
            outputExprs[i] = "";
        }
    }

    // ===== 配置访问（GUI 用） =====

    public String getCondition(int side) {
        if (side < 0 || side >= 6) return "";
        return conditions[side] == null ? "" : conditions[side];
    }

    public void setCondition(int side, String expr) {
        if (side < 0 || side >= 6) return;
        conditions[side] = expr == null ? "" : expr;
        markDirty();
        syncToClient();
    }

    public int getRedstoneLevel(int side) {
        if (side < 0 || side >= 6) return 0;
        return redstoneLevels[side];
    }

    public void setRedstoneLevel(int side, int level) {
        if (side < 0 || side >= 6) return;
        redstoneLevels[side] = Math.max(0, Math.min(15, level));
        markDirty();
        syncToClient();
    }

    // ===== 技能0（信号域）配置访问 =====

    public String getOutputExpr(int side) {
        if (side < 0 || side >= 6) return "";
        return outputExprs[side] == null ? "" : outputExprs[side];
    }

    public void setOutputExpr(int side, String expr) {
        if (side < 0 || side >= 6) return;
        outputExprs[side] = expr == null ? "" : expr;
        markDirty();
        syncToClient();
    }

    /** 某面最近输出值（供邻居执行器/总线读取） */
    public SignalValue getOutValue(int side) {
        if (side < 0 || side >= 6) return null;
        return outBuffer[side];
    }

    /** 服务端改动后重发描述包到客户端，使 GUI 重开时读到最新条件 */
    private void syncToClient() {
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    /** 当前某面实际红石输出（供 Block 查询） */
    public int getCurrentOutput(int side) {
        if (side < 0 || side >= 6) return 0;
        return currentOutput[side];
    }

    /** 是否已安装红石技能 */
    public boolean hasRedstoneSkill() {
        return skillStack != null && skillStack.getItem() instanceof ItemSkillRedstone;
    }

    /** 是否已插绑定卡 */
    public boolean hasConnector() {
        return connectorStack != null && connectorStack.getItem() instanceof ItemConnector
                && connectorStack.hasTagCompound() && connectorStack.getTagCompound().hasKey("sensorX");
    }

    // ===== 主循环 =====

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) return;
        if (firstTick || worldObj.getTotalWorldTime() % SIGNAL_INTERVAL == 0) {
            firstTick = false;
            signalTick(); // 技能0 信号域：每 2t（红石刻）
        }
        if (worldObj.getTotalWorldTime() % SCAN_INTERVAL == 0) {
            refreshTargetAndRedstone(); // 绑卡远端 + 红石：每 2s
        }
    }

    /**
     * 技能0 信号域主逻辑（每 2t）：
     *   1. 收信：读取 6 个方向邻居执行器/总线上一周期写出的 outBuffer → 本机 inBuffer（快照）
     *   2. 对每面求值条件（引用机器 NBT 快照 + {in} 输入），成立则计算输出值
     *   3. 输出值写入 outBuffer[输出方向]，并更新 NBT 主键 SignalOut（供绑卡远端读取）
     * 值快照 + 一跳/2t 天然防同 tick 振荡；A→B→A 回路表现为交替输出。
     */
    private void signalTick() {
        collectInputs();
        SignalValue lastOut = null;
        for (int side = 0; side < 6; side++) {
            String cond = getCondition(side);
            if (cond.isEmpty()) continue; // 未配置该面：不输出，保持上次值
            SignalValue[] ins = buildInValues(side); // 6 面输入（当前面带绑卡远端兜底）
            boolean pass = SignalExpr.test(cond, targetNbtCache, ins, side);
            SignalValue out = null;
            if (pass) {
                String outExpr = getOutputExpr(side);
                if (!outExpr.isEmpty()) {
                    out = SignalExpr.evaluate(outExpr, targetNbtCache, ins, side);
                } else {
                    out = SignalExpr.evaluate(cond, targetNbtCache, ins, side); // 默认输出条件计算值（保留类型）
                }
            }
            // 方向合一：输出永远写到本面缓冲（邻居从对面读）
            if (!sameValue(outBuffer[side], out)) {
                outBuffer[side] = out;
                markDirty();
            }
            if (out != null) lastOut = out;
        }
        if (lastOut != null) {
            // 更新主键（最近一次输出），writeToNBT 时写入 SignalOut 供绑卡远端读取
            signalOutCache = lastOut;
            markDirty();
        }
    }

    /** 收信：读 6 方向邻居执行器的 outBuffer（对面），写入本机 inBuffer 快照 */
    private void collectInputs() {
        for (int side = 0; side < 6; side++) {
            ForgeDirection dir = ForgeDirection.VALID_DIRECTIONS[side];
            TileEntity neighbor = worldObj.getTileEntity(
                    xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
            SignalValue v = null;
            if (neighbor instanceof TileActuator) {
                v = ((TileActuator) neighbor).getOutValue(oppositeSide(side));
            } else if (neighbor instanceof TileSignalBus) {
                v = ((TileSignalBus) neighbor).getOutValue(oppositeSide(side));
            }
            inBuffer[side] = v;
        }
    }

    /** 某面求值用的 {in} 输入：优先紧贴/总线信号，其次绑卡远端兜底 */
    private SignalValue getInValue(int side) {
        if (inBuffer[side] != null) return inBuffer[side];
        return remoteInputMain;
    }

    /** 6 面输入快照（供 {inX} 综合引用）；当前面保持 getInValue 的绑卡远端兜底语义 */
    private SignalValue[] buildInValues(int side) {
        SignalValue[] arr = inBuffer.clone();
        if (arr[side] == null) arr[side] = remoteInputMain;
        return arr;
    }

    private static int oppositeSide(int side) {
        if (side == 0) return 1; // D <-> U
        if (side == 1) return 0;
        if (side == 2) return 3; // N <-> S
        if (side == 3) return 2;
        if (side == 4) return 5; // W <-> E
        return 4;
    }

    private static boolean sameValue(SignalValue a, SignalValue b) {
        if (a == null || b == null) return a == b;
        if (a.type != b.type) return false;
        if (a.type == SignalValue.TYPE_DOUBLE) return a.num == b.num;
        if (a.type == SignalValue.TYPE_BOOL) return a.bool == b.bool;
        return a.str.equals(b.str);
    }

    /** 绑卡远端 + 红石（每 40t）：刷新目标 NBT 快照、解析远端 SignalOut、红石输出 */
    private void refreshTargetAndRedstone() {
        NBTTagCompound nbt = readTargetNbt();
        targetNbtCache = nbt;
        remoteInputMain = nbt != null ? SignalValue.readFromNBT(nbt, "SignalOut") : null;
        // 红石分支（沿用原有逻辑，用同一份 NBT 快照）
        // 支持 {in}：条件可用 SignalExpr（引用紧贴/总线输入 + 机器 NBT），无需插卡（nbt 可为 null）
        // 方向合一：红石输出到条件面本面（无独立输出方向）
        boolean changed = false;
        boolean debug = worldObj.getTotalWorldTime() % 200 == 0;
        for (int side = 0; side < 6; side++) {
            int newOut = 0;
            boolean result = false;
            if (hasRedstoneSkill()) {
                String cond = getCondition(side);
                if (!cond.isEmpty()) {
                    result = SignalExpr.test(cond, nbt, buildInValues(side), side);
                    if (result) newOut = redstoneLevels[side];
                }
            }
            if (debug && !getCondition(side).isEmpty()) {
                System.out.println("[Actuator] t=" + worldObj.getTotalWorldTime()
                        + " side=" + side + " target=" + (nbt != null)
                        + " result=" + result
                        + " cookTime=" + (nbt != null ? nbt.getDouble("cookTime") : "n/a")
                        + " progress=" + (nbt != null ? nbt.getDouble("progress") : "n/a")
                        + " Progress=" + (nbt != null ? nbt.getDouble("Progress") : "n/a")
                        + " BurnTime=" + (nbt != null ? nbt.getDouble("BurnTime") : "n/a")
                        + " Energy=" + (nbt != null ? nbt.getDouble("Energy") : "n/a")
                        + " hasTags=" + (nbt != null ? !nbt.hasNoTags() : "n/a"));
            }
            // 方向合一：红石从条件面本面输出（无独立输出方向，天然无多面争抢冲突）
            if (currentOutput[side] != newOut) {
                currentOutput[side] = newOut;
                changed = true;
            }
        }
        if (changed) {
            markDirty();
            notifyNeighborsOfRedstoneChange();
        }
    }

    /** 读取绑定卡指向的目标机器 NBT（复用传感器的多方块解析逻辑） */
    private NBTTagCompound readTargetNbt() {
        if (!hasConnector()) return null;
        NBTTagCompound tag = connectorStack.getTagCompound();
        int sx = tag.getInteger("sensorX");
        int sy = tag.getInteger("sensorY");
        int sz = tag.getInteger("sensorZ");
        int dim = tag.getInteger("dimension");
        int dirIndex = tag.getInteger("dirIndex");
        if (worldObj.provider.dimensionId != dim) return null;

        // 传感器坐标 + 方向 = 目标机器坐标
        ForgeDirection dir = (dirIndex >= 0 && dirIndex < 6)
                ? ForgeDirection.VALID_DIRECTIONS[dirIndex] : ForgeDirection.UNKNOWN;
        if (dir == ForgeDirection.UNKNOWN) return null;

        int tx = sx + dir.offsetX;
        int ty = sy + dir.offsetY;
        int tz = sz + dir.offsetZ;
        TileEntity te = worldObj.getTileEntity(tx, ty, tz);
        if (te == null) return null;
        // 多方块解析到主控（Railcraft 锅炉等）
        TileEntity master = MultiblockResolver.resolve(te);
        if (master != null) te = master;

        NBTTagCompound nbt = new NBTTagCompound();
        try {
            te.writeToNBT(nbt);
        } catch (Exception e) {
            return null;
        }
        return nbt;
    }

    /** 通知相邻方块红石变化。
     *  关键：notifyBlocksOfNeighborChange(x,y,z,block) 通知的是 (x,y,z) 周围 6 个邻居，
     *  并不包含 (x,y,z) 自身。因此必须用【执行器自身坐标】调用，让红石灯/红石线等邻居收到
     *  onNeighborBlockChange 并重新检测 power。若用邻居坐标调用，通知的是邻居的邻居，红石灯收不到。
     */
    private void notifyNeighborsOfRedstoneChange() {
        worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord, getBlockType());
    }

    // ===== IInventory（2 槽：绑定卡 + 技能插件） =====

    @Override
    public int getSizeInventory() { return 2; }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot == SLOT_CONNECTOR) return connectorStack;
        if (slot == SLOT_SKILL) return skillStack;
        return null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        ItemStack[] refs = {connectorStack, skillStack};
        if (slot < 0 || slot >= refs.length) return null;
        ItemStack stack = refs[slot];
        if (stack == null) return null;
        if (stack.stackSize <= amount) {
            refs[slot] = null;
            if (slot == SLOT_CONNECTOR) connectorStack = null;
            else skillStack = null;
            markDirty();
            return stack;
        }
        ItemStack split = stack.splitStack(amount);
        if (stack.stackSize == 0) {
            if (slot == SLOT_CONNECTOR) connectorStack = null;
            else skillStack = null;
        }
        markDirty();
        return split;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        // 1.7.10 该回调不应清空槽位：置 null 会让卡/插件在部分容器回调路径下意外消失
        // （表现为打开 GUI 时物品"拿不出来"）。取走只应走 decrStackSize/setInventorySlotContents。
        return getStackInSlot(slot);
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot == SLOT_CONNECTOR) {
            connectorStack = stack;
        } else if (slot == SLOT_SKILL) {
            skillStack = stack;
        }
        if (stack != null && stack.stackSize > getInventoryStackLimit()) {
            stack.stackSize = getInventoryStackLimit();
        }
        markDirty();
    }

    @Override
    public String getInventoryName() { return "container.actuator"; }

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
    public void openInventory() {}
    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (stack == null) return false;
        if (slot == SLOT_CONNECTOR) return stack.getItem() instanceof ItemConnector;
        if (slot == SLOT_SKILL) return stack.getItem() instanceof ItemSkill;
        return false;
    }

    // ===== 客户端同步（描述包） =====
    // 只同步 GUI 需要的数据（条件表达式 + 红石强度），槽位由容器/物品同步处理

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeSyncData(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        readSyncData(pkt.func_148857_g());
    }

    private void writeSyncData(NBTTagCompound tag) {
        for (int i = 0; i < 6; i++) {
            if (conditions[i] != null && !conditions[i].isEmpty()) {
                tag.setString("Cond" + i, conditions[i]);
            }
            if (outputExprs[i] != null && !outputExprs[i].isEmpty()) {
                tag.setString("OutExpr" + i, outputExprs[i]);
            }
            tag.setInteger("RS" + i, redstoneLevels[i]);
        }
    }

    private void readSyncData(NBTTagCompound tag) {
        for (int i = 0; i < 6; i++) {
            conditions[i] = tag.hasKey("Cond" + i) ? tag.getString("Cond" + i) : "";
            outputExprs[i] = tag.hasKey("OutExpr" + i) ? tag.getString("OutExpr" + i) : "";
            redstoneLevels[i] = tag.hasKey("RS" + i) ? tag.getInteger("RS" + i) : 15;
        }
    }

    // ===== NBT 持久化 =====

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (connectorStack != null) {
            NBTTagCompound t = new NBTTagCompound();
            connectorStack.writeToNBT(t);
            tag.setTag("Connector", t);
        }
        if (skillStack != null) {
            NBTTagCompound t = new NBTTagCompound();
            skillStack.writeToNBT(t);
            tag.setTag("Skill", t);
        }
        for (int i = 0; i < 6; i++) {
            if (conditions[i] != null && !conditions[i].isEmpty()) {
                tag.setString("Cond" + i, conditions[i]);
            }
            if (outputExprs[i] != null && !outputExprs[i].isEmpty()) {
                tag.setString("OutExpr" + i, outputExprs[i]);
            }
            tag.setInteger("RS" + i, redstoneLevels[i]);
            tag.setInteger("Out" + i, currentOutput[i]);
        }
        // 信号主键：供绑卡远端执行器读取最近输出
        if (signalOutCache != null) {
            signalOutCache.writeToNBT(tag, "SignalOut");
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("Connector")) connectorStack = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("Connector"));
        if (tag.hasKey("Skill")) skillStack = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("Skill"));
        for (int i = 0; i < 6; i++) {
            conditions[i] = tag.hasKey("Cond" + i) ? tag.getString("Cond" + i) : "";
            outputExprs[i] = tag.hasKey("OutExpr" + i) ? tag.getString("OutExpr" + i) : "";
            redstoneLevels[i] = tag.hasKey("RS" + i) ? tag.getInteger("RS" + i) : 15;
            currentOutput[i] = tag.hasKey("Out" + i) ? tag.getInteger("Out" + i) : 0;
        }
        signalOutCache = SignalValue.readFromNBT(tag, "SignalOut");
    }
}
