package com.cyclone.minecraftiot.util;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Railcraft 铁质/钢质蓄水器（Tank）数据聚合器。
 *
 * 蓄水器（Iron/Steel Tank）是多方块结构：阀门（TileTank*Valve）为主控，罐壁（Wall）/观察窗（Gauge）为从属。
 * 与锅炉不同，蓄水器的全部液体都保存在【主控阀门】的 getTankManager()（List&lt;StandardTank&gt;）里，
 * 罐壁/观察窗本身不含液体数据。因此单读任意从属方块都拿不到数据——这正是不适配时"贴着测不到任何数据"的原因。
 *
 * 处理流程：
 *   1) 把从属方块经 MultiblockResolver 解析到阀门主控；
 *   2) 反射调用主控 getTankManager() 拿到 List&lt;StandardTank&gt;；
 *   3) 每个 StandardTank 反射读 getFluid()/getCapacity()，产出液体名、当前量、容量。
 *
 * 两种消费方式（与 BoilerAggregator 一致）：
 *   1) summarize()：内置友好多行文本（无模板时展示）。
 *   2) injectVirtualData()：把结果以虚拟 NBT 键 Tank{i}_FluidName / Tank{i}_Amount / Tank{i}_Capacity 注入，
 *      供模板编辑器作为变量勾选、也供 DisplayFormatter 按 var. 路径解析。
 * 仅依赖 Forge 流体 API，不依赖 Railcraft 类（全程反射）。
 */
public class TankAggregator {

    /** 单个储罐的数据 */
    public static class TankSlot {
        public String fluidName;   // 液体标识（如 water / steam / lava），空罐为 null
        public long amount;        // 当前量（mB）
        public long capacity;      // 容量（mB）
    }

    /** 聚合结果 */
    public static class TankData {
        public List<TankSlot> tanks = new ArrayList<TankSlot>();
    }

    /**
     * 判断是否为 Railcraft 蓄水器方块（含阀门/罐壁/观察窗/水罐）。
     * 类名须在 mods.railcraft 包、含 "TileTank" 且不含 "Boiler"（排除 TileBoilerTank）。
     */
    public static boolean isTankTile(TileEntity t) {
        if (t == null) return false;
        String n = t.getClass().getName();
        return n.startsWith("mods.railcraft.") && n.contains("TileTank") && !n.contains("Boiler");
    }

    /** 聚合蓄水器数据：从属方块解析到阀门主控，读其 tankManager；目标不是蓄水器返回 null */
    public static TankData aggregate(TileEntity te) {
        if (te == null || te.getWorldObj() == null) return null;
        if (!isTankTile(te)) return null;
        TileEntity master = MultiblockResolver.resolve(te);
        if (master == null) master = te;
        return readTankData(master);
    }

    /** 反射读主控 tile 的 getTankManager()（List<StandardTank>），逐罐取液体名/量/容量 */
    private static TankData readTankData(TileEntity tile) {
        Object tm = invokeNoArg(tile, "getTankManager");
        if (!(tm instanceof List)) return null;
        TankData d = new TankData();
        try {
            for (Object tank : (List<?>) tm) {
                if (tank == null) continue;
                TankSlot s = new TankSlot();
                Object fluid = invokeNoArg(tank, "getFluid");
                if (fluid instanceof FluidStack) {
                    FluidStack fs = (FluidStack) fluid;
                    if (fs.getFluid() != null) s.fluidName = fs.getFluid().getName();
                    s.amount = fs.amount;
                } else {
                    Object ft = invokeNoArg(tank, "getFluidType");
                    if (ft instanceof net.minecraftforge.fluids.Fluid) {
                        s.fluidName = ((net.minecraftforge.fluids.Fluid) ft).getName();
                    }
                }
                Object cap = invokeNoArg(tank, "getCapacity");
                if (cap instanceof Number) s.capacity = ((Number) cap).longValue();
                d.tanks.add(s);
            }
        } catch (Exception ignore) {}
        return d;
    }

    /** 输出"液体N: 名 量/容量 mB"多行友好文本；非蓄水器返回 null */
    public static String summarize(TileEntity te) {
        TankData d = aggregate(te);
        return d == null ? null : summarize(d);
    }

    /** 由聚合结果生成友好文本 */
    public static String summarize(TankData d) {
        if (d == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.tanks.size(); i++) {
            TankSlot s = d.tanks.get(i);
            if (s == null) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append("\u6db2\u4f53").append(i + 1).append(": ");
            if (s.fluidName != null && !s.fluidName.isEmpty()) sb.append(s.fluidName).append(' ');
            sb.append(s.amount).append('/').append(s.capacity).append(" mB");
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * 把聚合结果以虚拟 NBT 键注入（仅当目标是蓄水器）：
     * Tank{i}_FluidName / Tank{i}_Amount / Tank{i}_Capacity（i 从 0 起）。
     * 模板编辑器据此列出可勾选变量；DisplayFormatter 按 var. 路径解析。
     */
    public static void injectVirtualData(TileEntity te, NBTTagCompound nbt) {
        TankData d = aggregate(te);
        if (d == null || nbt == null) return;
        for (int i = 0; i < d.tanks.size(); i++) {
            TankSlot s = d.tanks.get(i);
            if (s == null) continue;
            String p = "Tank" + i;
            if (s.fluidName != null) nbt.setString(p + "_FluidName", s.fluidName);
            nbt.setInteger(p + "_Amount", (int) s.amount);
            nbt.setInteger(p + "_Capacity", (int) s.capacity);
        }
    }

    // ===== 反射工具（按类+方法名缓存，避免每刻重复反射） =====
    // 注意：ConcurrentHashMap 不允许 null 值，反射失败用占位标记缓存，避免 NPE

    private static final Object NO_METHOD = new Object();
    private static final Map<Class<?>, Map<String, Object>> METHOD_CACHE = new ConcurrentHashMap<Class<?>, Map<String, Object>>();

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
}
