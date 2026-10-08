package com.cyclone.sensordisplay.util;

import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 格雷科技（GregTech 5.09）多方块机器适配器。
 *
 * GT 多方块机器（高炉/大型锅炉/蒸馏塔/聚变堆/大型涡轮等）与其他 mod 不同：
 * 1) 允许两台机器共用"墙壁"（机械外壳方块），外壳是【无 TileEntity 的普通方块】；
 * 2) 主控方块是显式的（GT_Block_Machines 的 meta，TileEntity=BaseMetaTileEntity，
 *    其 getMetaTileEntity() 返回 GT_MetaTileEntity_MultiBlockBase 子类）；
 * 3) 机器的物品在输入/输出总线（InputBus/OutputBus），液体在输入/输出仓（Hatch），
 *    主控自身只持有进度/电压/效率与部件列表。
 *
 * 因此适配规则：
 * - 传感器贴主控方块：直接识别为主控，聚合整机；
 * - 传感器贴外壳（公用墙壁）：按立方体范围反查候选主控，解析到【多个】主控时
 *   按下东南定律取"偏下东南"（y 最小，其次 x 最大，再次 z 最大）的那个为准，
 *   再以它为中心聚合整台机器 NBT。
 *
 * 全部反射访问 GT 类（类名+字段名识别），不产生编译期依赖；方法/字段按类缓存。
 */
public class GtMultiblockAggregator {

    private static final String GT_BLOCK_MACHINES = "gregtech.common.blocks.GT_Block_Machines";
    private static final String GT_CASING_PREFIX = "gregtech.common.blocks.GT_Block_Casings";
    private static final String GT_BASE_TE = "gregtech.api.metatileentity.BaseMetaTileEntity";
    private static final String GT_MULTI_BASE = "gregtech.api.metatileentity.implementations.GT_MetaTileEntity_MultiBlockBase";
    private static final String GT_HATCH_BASE = "gregtech.api.metatileentity.implementations.GT_MetaTileEntity_Hatch";

    /** 反查主控的立方体半径（GT 多方块最大尺寸通常不超过 7 格） */
    private static final int SEARCH_RADIUS = 7;

    /** 主控持有的部件列表字段（物品总线/液体仓/能源/维护/消音/动力仓） */
    private static final String[] HATCH_FIELDS = {
        "mInputBusses", "mOutputBusses", "mInputHatches", "mOutputHatches",
        "mEnergyHatches", "mDynamoHatches", "mMaintenanceHatches", "mMufflerHatches"
    };

    /** 聚合结果 */
    public static class GtData {
        public String machineName = "";
        public int progress;
        public int maxProgress;
        public int eUt;
        public int efficiency;
        public List<String> inputs = new ArrayList<String>();    // "名 xN"
        public List<String> outputs = new ArrayList<String>();
        public List<String> fluidsIn = new ArrayList<String>();  // "名 量/容量 mB"
        public List<String> fluidsOut = new ArrayList<String>();
    }

    /** 是否为 GT 机械外壳方块（无 TileEntity，需反查主控） */
    public static boolean isGtCasing(Block block) {
        if (block == null) return false;
        return block.getClass().getName().startsWith(GT_CASING_PREFIX);
    }

    /** 是否为 GT 多方块主控方块（BaseMetaTileEntity + MultiBlockBase meta） */
    public static boolean isGtMultiController(TileEntity te) {
        if (te == null) return false;
        if (!te.getClass().getName().equals(GT_BASE_TE)) return false;
        Object meta = invokeNoArg(te, "getMetaTileEntity");
        return meta != null && isMultiBlockBase(meta);
    }

    /** 是否为 GT 多方块部件方块（总线/仓：继承 GT_MetaTileEntity_Hatch 的 meta） */
    public static boolean isGtPart(TileEntity te) {
        if (te == null) return false;
        if (!te.getClass().getName().equals(GT_BASE_TE)) return false;
        Object meta = invokeNoArg(te, "getMetaTileEntity");
        if (meta == null) return false;
        Class<?> c = meta.getClass();
        while (c != null) {
            if (c.getName().equals(GT_HATCH_BASE)) return true;
            c = c.getSuperclass();
        }
        return false;
    }

    /** meta 是否为 GT_MetaTileEntity_MultiBlockBase 子类（沿继承链） */
    private static boolean isMultiBlockBase(Object meta) {
        Class<?> c = meta.getClass();
        while (c != null) {
            if (c.getName().equals(GT_MULTI_BASE)) return true;
            c = c.getSuperclass();
        }
        return false;
    }

    /**
     * 从传感器/外壳位置反查 GT 多方块主控（外壳/线圈等无 TE 方块用）。
     * 收集 SEARCH_RADIUS 立方体内全部候选主控；要求外壳与候选主控【通过 GT 方块链连通】
     * （排除"孤立外壳 + 远处主控"的误读——没有贴着机器就读不到）；
     * 多个连通候选时【距离参照点近者优先】，距离相同时按下东南定律取偏下东南者。
     */
    public static TileEntity findMaster(World world, int sx, int sy, int sz, int rx, int ry, int rz) {
        List<TileEntity> candidates = collectCandidates(world, sx, sy, sz);
        if (candidates.isEmpty()) return null;
        List<TileEntity> connected = new ArrayList<TileEntity>();
        for (TileEntity c : candidates) {
            if (isConnectedToMaster(world, rx, ry, rz, c)) connected.add(c);
        }
        if (connected.isEmpty()) return null;
        TileEntity best = connected.get(0);
        for (int i = 1; i < connected.size(); i++) {
            TileEntity c = connected.get(i);
            int db = dist(c, rx, ry, rz), dc = dist(best, rx, ry, rz);
            // 候选 c 更近 → 替换；等距 → 更"下东南"者胜出（定律兜底）
            if (db < dc || (db == dc && moreSouthEastLower(c, best))) best = c;
        }
        return best;
    }

    private static final int[][] DIRS = {
        { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 }
    };

    /**
     * BFS 连通性验证：从外壳位置出发，只沿 GT 方块（GT_Block_Machines / GT_Block_Casings）扩展，
     * 若能到达候选主控说明外壳属于该机器的结构；孤立外壳（周围无机器方块链）到不了 → false。
     */
    private static boolean isConnectedToMaster(World world, int sx, int sy, int sz, TileEntity master) {
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<int[]>();
        java.util.HashSet<Long> visited = new java.util.HashSet<Long>();
        queue.add(new int[] { sx, sy, sz });
        visited.add(key(sx, sy, sz));
        int steps = 0;
        while (!queue.isEmpty() && steps++ < 512) {
            int[] p = queue.poll();
            if (p[0] == master.xCoord && p[1] == master.yCoord && p[2] == master.zCoord) return true;
            for (int[] d : DIRS) {
                int x = p[0] + d[0], y = p[1] + d[1], z = p[2] + d[2];
                long k = key(x, y, z);
                if (visited.contains(k)) continue;
                Block b = world.getBlock(x, y, z);
                if (b == null) continue;
                String n = b.getClass().getName();
                if (!n.equals(GT_BLOCK_MACHINES) && !n.startsWith(GT_CASING_PREFIX)) continue;
                visited.add(k);
                queue.add(new int[] { x, y, z });
            }
        }
        return false;
    }

    private static long key(int x, int y, int z) {
        return (long) (x & 0x3FFFFFF) << 38 | (long) (y & 0x3FFFFFF) << 12 | (z & 0xFFF);
    }

    /**
     * 从 GT 部件方块（输入/输出总线、液体/能源/维护/消音仓）反查【精确所属】主控：
     * 候选主控的 hatch 列表中必须包含该部件（GT 一台 hatch 只属于一台机器，归属唯一）。
     * 找不到归属返回 null（调用方按普通机器方块显示自身）。
     */
    public static TileEntity findMasterForPart(World world, int px, int py, int pz, TileEntity part) {
        List<TileEntity> candidates = collectCandidates(world, px, py, pz);
        for (TileEntity c : candidates) {
            if (ownsPart(c, px, py, pz)) return c;
        }
        return null;
    }

    /** 收集搜索范围内全部 GT 多方块主控 */
    private static List<TileEntity> collectCandidates(World world, int sx, int sy, int sz) {
        List<TileEntity> candidates = new ArrayList<TileEntity>();
        if (world == null) return candidates;
        for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    int x = sx + dx, y = sy + dy, z = sz + dz;
                    Block b = world.getBlock(x, y, z);
                    if (b == null || !b.getClass().getName().equals(GT_BLOCK_MACHINES)) continue;
                    TileEntity te = world.getTileEntity(x, y, z);
                    if (te != null && isGtMultiController(te)) candidates.add(te);
                }
            }
        }
        return candidates;
    }

    /** 曼哈顿距离（到参照点，格子距离） */
    private static int dist(TileEntity t, int rx, int ry, int rz) {
        return Math.abs(t.xCoord - rx) + Math.abs(t.yCoord - ry) + Math.abs(t.zCoord - rz);
    }

    /** 候选主控是否包含指定部件（hatch 列表任一坐标匹配） */
    private static boolean ownsPart(TileEntity master, int px, int py, int pz) {
        Object meta = invokeNoArg(master, "getMetaTileEntity");
        if (meta == null) return false;
        for (String f : HATCH_FIELDS) {
            for (Object h : listField(meta, f)) {
                TileEntity te = baseTeOf(h);
                if (te != null && te.xCoord == px && te.yCoord == py && te.zCoord == pz) return true;
            }
        }
        return false;
    }

    /** a 是否比 b 更"下东南"：y 更小（下）优先；同 y 时 x 更大（东）；再同则 z 更大（南） */
    private static boolean moreSouthEastLower(TileEntity a, TileEntity b) {
        if (a.yCoord != b.yCoord) return a.yCoord < b.yCoord;
        if (a.xCoord != b.xCoord) return a.xCoord > b.xCoord;
        return a.zCoord > b.zCoord;
    }

    /** 聚合整机数据；目标不是 GT 多方块主控返回 null */
    public static GtData aggregate(TileEntity master) {
        if (!isGtMultiController(master)) return null;
        Object meta = invokeNoArg(master, "getMetaTileEntity");
        if (meta == null) return null;
        GtData d = new GtData();
        Object name = invokeNoArg(meta, "getLocalizedName");
        if (name instanceof String && !((String) name).isEmpty()) d.machineName = (String) name;
        if (d.machineName.isEmpty()) d.machineName = meta.getClass().getSimpleName();

        d.progress = getIntField(meta, "mProgresstime", 0);
        d.maxProgress = getIntField(meta, "mMaxProgresstime", 0);
        d.eUt = getIntField(meta, "mEUt", 0);
        d.efficiency = getIntField(meta, "mEfficiency", 0);

        // 物品总线：输入/输出
        Map<String, Integer> in = new LinkedHashMap<String, Integer>();
        Map<String, Integer> out = new LinkedHashMap<String, Integer>();
        for (Object bus : listField(meta, "mInputBusses")) collectInventory(bus, in);
        for (Object bus : listField(meta, "mOutputBusses")) collectInventory(bus, out);
        d.inputs.addAll(formatCounts(in));
        d.outputs.addAll(formatCounts(out));

        // 液体仓：输入/输出
        Map<String, long[]> fin = new LinkedHashMap<String, long[]>();  // 名 -> [amount, capacity]
        Map<String, long[]> fout = new LinkedHashMap<String, long[]>();
        for (Object h : listField(meta, "mInputHatches")) collectFluids(h, fin);
        for (Object h : listField(meta, "mOutputHatches")) collectFluids(h, fout);
        d.fluidsIn.addAll(formatFluids(fin));
        d.fluidsOut.addAll(formatFluids(fout));
        return d;
    }

    /** 汇总一个 bus（hatch meta）的物品到 map */
    private static void collectInventory(Object hatchMeta, Map<String, Integer> map) {
        TileEntity te = baseTeOf(hatchMeta);
        if (!(te instanceof IInventory)) return;
        try {
            IInventory inv = (IInventory) te;
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (s == null || s.stackSize <= 0) continue;
                String n = s.getDisplayName();
                if (n == null || n.isEmpty()) n = s.getItem().getUnlocalizedName();
                Integer cur = map.get(n);
                map.put(n, (cur == null ? 0 : cur) + s.stackSize);
            }
        } catch (Throwable ignore) {}
    }

    /** 汇总一个液体仓到 map（amount 相加、capacity 求和） */
    private static void collectFluids(Object hatchMeta, Map<String, long[]> map) {
        TileEntity te = baseTeOf(hatchMeta);
        if (!(te instanceof IFluidHandler)) return;
        try {
            FluidTankInfo[] infos = ((IFluidHandler) te).getTankInfo(ForgeDirection.UNKNOWN);
            if (infos == null) return;
            for (FluidTankInfo info : infos) {
                if (info == null) continue;
                FluidStack fs = info.fluid;
                String name = (fs != null && fs.getFluid() != null) ? fs.getFluid().getName() : "";
                long[] cur = map.get(name);
                if (cur == null) {
                    cur = new long[] { 0L, 0L };
                    map.put(name, cur);
                }
                cur[0] += (fs == null ? 0 : fs.amount);
                cur[1] += info.capacity;
            }
        } catch (Throwable ignore) {}
    }

    /** hatch meta → 其 BaseMetaTileEntity */
    private static TileEntity baseTeOf(Object hatchMeta) {
        if (hatchMeta == null) return null;
        Object te = invokeNoArg(hatchMeta, "getBaseMetaTileEntity");
        return te instanceof TileEntity ? (TileEntity) te : null;
    }

    private static List<String> formatCounts(Map<String, Integer> map) {
        List<String> list = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            list.add(e.getValue() > 1 ? e.getKey() + " x" + e.getValue() : e.getKey());
        }
        return list;
    }

    private static List<String> formatFluids(Map<String, long[]> map) {
        List<String> list = new ArrayList<String>();
        for (Map.Entry<String, long[]> e : map.entrySet()) {
            String n = e.getKey();
            long[] v = e.getValue();
            list.add((n.isEmpty() ? "\u7a7a" : n) + " " + v[0] + "/" + v[1] + " mB");
        }
        return list;
    }

    /** 默认友好多行文本；非主控返回 null */
    public static String summarize(TileEntity master) {
        GtData d = aggregate(master);
        return d == null ? null : summarize(d);
    }

    public static String summarize(GtData d) {
        StringBuilder sb = new StringBuilder();
        sb.append("[GT] ").append(d.machineName);
        if (d.maxProgress > 0) {
            sb.append('\n').append("\u8fdb\u5ea6: ").append(d.progress).append('/').append(d.maxProgress).append(" t");
        }
        if (d.eUt != 0) {
            sb.append('\n').append("\u7535\u538b: ").append(d.eUt).append(" EU/t");
        }
        if (d.efficiency > 0) {
            sb.append('\n').append("\u6548\u7387: ").append(d.efficiency).append('%');
        }
        if (!d.inputs.isEmpty()) {
            sb.append('\n').append("\u8f93\u5165: ");
            for (int i = 0; i < d.inputs.size(); i++) sb.append(d.inputs.get(i)).append(i < d.inputs.size() - 1 ? "; " : "");
        }
        if (!d.outputs.isEmpty()) {
            sb.append('\n').append("\u8f93\u51fa: ");
            for (int i = 0; i < d.outputs.size(); i++) sb.append(d.outputs.get(i)).append(i < d.outputs.size() - 1 ? "; " : "");
        }
        if (!d.fluidsIn.isEmpty()) {
            sb.append('\n').append("\u6db2\u4f53\u5165: ");
            for (int i = 0; i < d.fluidsIn.size(); i++) sb.append(d.fluidsIn.get(i)).append(i < d.fluidsIn.size() - 1 ? "; " : "");
        }
        if (!d.fluidsOut.isEmpty()) {
            sb.append('\n').append("\u6db2\u4f53\u51fa: ");
            for (int i = 0; i < d.fluidsOut.size(); i++) sb.append(d.fluidsOut.get(i)).append(i < d.fluidsOut.size() - 1 ? "; " : "");
        }
        return sb.toString();
    }

    /**
     * 合成整机 NBT：主控自身完整 NBT + 聚合虚拟键（Progress/MaxProgress/EUt/Efficiency/Machine，
     * Input{i}/Output{i}/FluidIn{i}_Name|Amount|Capacity/FluidOut{i}_*），供表达式编辑器拍平变量。
     */
    public static NBTTagCompound buildNbt(TileEntity master) {
        NBTTagCompound nbt = new NBTTagCompound();
        if (!isGtMultiController(master)) return nbt;
        try {
            master.writeToNBT(nbt);
        } catch (Throwable ignore) {}
        GtData d = aggregate(master);
        if (d == null) return nbt;
        nbt.setString("Machine", d.machineName);
        nbt.setInteger("Progress", d.progress);
        nbt.setInteger("MaxProgress", d.maxProgress);
        nbt.setInteger("EUt", d.eUt);
        nbt.setInteger("Efficiency", d.efficiency);
        for (int i = 0; i < d.inputs.size(); i++) nbt.setString("Input" + i, d.inputs.get(i));
        for (int i = 0; i < d.outputs.size(); i++) nbt.setString("Output" + i, d.outputs.get(i));
        for (int i = 0; i < d.fluidsIn.size(); i++) {
            String[] parts = splitFluid(d.fluidsIn.get(i));
            nbt.setString("FluidIn" + i + "_Name", parts[0]);
            nbt.setInteger("FluidIn" + i + "_Amount", (int) safeLong(parts[1]));
            nbt.setInteger("FluidIn" + i + "_Capacity", (int) safeLong(parts[2]));
        }
        for (int i = 0; i < d.fluidsOut.size(); i++) {
            String[] parts = splitFluid(d.fluidsOut.get(i));
            nbt.setString("FluidOut" + i + "_Name", parts[0]);
            nbt.setInteger("FluidOut" + i + "_Amount", (int) safeLong(parts[1]));
            nbt.setInteger("FluidOut" + i + "_Capacity", (int) safeLong(parts[2]));
        }
        return nbt;
    }

    /** "名 量/容量 mB" → [名, 量, 容量] */
    private static String[] splitFluid(String line) {
        String[] r = new String[] { "", "0", "0" };
        if (line == null) return r;
        int sp = line.indexOf(' ');
        if (sp < 0) return r;
        r[0] = line.substring(0, sp);
        String rest = line.substring(sp + 1);
        int slash = rest.indexOf('/');
        if (slash > 0) {
            r[1] = rest.substring(0, slash);
            int sp2 = rest.indexOf(' ', slash);
            r[2] = sp2 > slash ? rest.substring(slash + 1, sp2) : rest.substring(slash + 1);
        }
        return r;
    }

    private static long safeLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Throwable t) {
            return 0L;
        }
    }

    // ===== 反射工具（按类缓存） =====
    // 注意：ConcurrentHashMap 不允许 null 值，反射失败用占位标记缓存，避免 NPE

    private static final Object NO_METHOD = new Object();
    private static final Object NO_FIELD = new Object();
    private static final Map<Class<?>, Map<String, Object>> METHOD_CACHE = new ConcurrentHashMap<Class<?>, Map<String, Object>>();
    private static final Map<Class<?>, Map<String, Object>> FIELD_CACHE = new ConcurrentHashMap<Class<?>, Map<String, Object>>();

    private static Object invokeNoArg(Object obj, String name) {
        if (obj == null) return null;
        Class<?> c = obj.getClass();
        Map<String, Object> m = METHOD_CACHE.get(c);
        if (m == null) {
            m = new ConcurrentHashMap<String, Object>();
            METHOD_CACHE.put(c, m);
        }
        Object method = m.get(name);
        if (method == null && !m.containsKey(name)) {
            try {
                method = c.getMethod(name);
            } catch (NoSuchMethodException ignore) {
                method = NO_METHOD;
            }
            m.put(name, method);
        }
        if (method == NO_METHOD) return null;
        try {
            return ((Method) method).invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }

    /** 反射读 public 字段值（含父类 public 字段），失败返回默认 */
    private static Object getFieldValue(Object obj, String name) {
        if (obj == null) return null;
        Class<?> c = obj.getClass();
        Map<String, Object> fm = FIELD_CACHE.get(c);
        if (fm == null) {
            fm = new ConcurrentHashMap<String, Object>();
            FIELD_CACHE.put(c, fm);
        }
        Object f = fm.get(name);
        if (f == null && !fm.containsKey(name)) {
            try {
                f = c.getField(name);
            } catch (NoSuchFieldException ignore) {
                f = NO_FIELD;
            }
            fm.put(name, f);
        }
        if (f == NO_FIELD) return null;
        try {
            return ((Field) f).get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private static int getIntField(Object obj, String name, int def) {
        Object v = getFieldValue(obj, name);
        return v instanceof Number ? ((Number) v).intValue() : def;
    }

    /** 读 List 字段（hatch 列表） */
    private static List<?> listField(Object obj, String name) {
        Object v = getFieldValue(obj, name);
        return v instanceof List ? (List<?>) v : new ArrayList<Object>(0);
    }
}
