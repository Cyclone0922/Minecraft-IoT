package com.cyclone.minecraftiot.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工业2（IC2 exp）核反应堆适配器。
 *
 * 反应堆是"核心 + 反应仓"结构：核心方块（TileEntityNuclearReactorElectric）存全部数据
 * （热量/EU 输出/铀棒），反应仓（TileEntityReactorChamberElectric）只是外壳，
 * 但官方接口提供 getReactor() 直接返回所属核心。传感器贴反应仓时解析到核心再聚合。
 *
 * 数据经 IC2 官方 API IReactor 读取（getHeat/getMaxHeat/getReactorEUEnergyOutput/
 * getTickRate/isFluidCooled/getItemAt(x,y) 3x3 铀棒），反射调用，不产生编译期依赖。
 *
 * 消费方式与其他聚合器一致：
 *   summarize()：默认友好多行文本；
 *   buildNbt()：合成 NBT + 虚拟键 Heat/MaxHeat/EUOutput/TickRate/FluidCooled/Rod{i}_Name|Count。
 */
public class Ic2ReactorAggregator {

    private static final String IC2_REACTOR_CORE = "ic2.core.block.reactor.tileentity.TileEntityNuclearReactorElectric";
    private static final String IC2_REACTOR_CHAMBER = "ic2.core.block.reactor.tileentity.TileEntityReactorChamberElectric";

    /** 聚合结果 */
    public static class ReactorData {
        public int heat;
        public int maxHeat;
        public double euOutput;
        public int tickRate;
        public boolean fluidCooled;
        public String[] rods = new String[9]; // 3x3，索引=y*3+x，"铀燃料 x1"
    }

    /**
     * 解析反应堆核心：传入核心方块 → 自身；传入反应仓 → getReactor()；
     * 目标不是反应堆/仓返回 null。
     */
    public static TileEntity resolveCore(TileEntity te) {
        if (te == null) return null;
        String n = te.getClass().getName();
        if (n.equals(IC2_REACTOR_CORE)) return te;
        if (!n.equals(IC2_REACTOR_CHAMBER)) return null;
        Object core = invokeNoArg(te, "getReactor");
        return core instanceof TileEntity ? (TileEntity) core : null;
    }

    public static ReactorData aggregate(TileEntity core) {
        if (core == null) return null;
        String n = core.getClass().getName();
        if (!n.equals(IC2_REACTOR_CORE)) return null;
        ReactorData d = new ReactorData();
        d.heat = intOf(core, "getHeat");
        d.maxHeat = intOf(core, "getMaxHeat");
        d.euOutput = doubleOf(core, "getReactorEUEnergyOutput");
        d.tickRate = intOf(core, "getTickRate");
        Boolean fc = boolOf(core, "isFluidCooled");
        d.fluidCooled = Boolean.TRUE.equals(fc);
        // 3x3 铀棒
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                Object s = invoke(core, "getItemAt", x, y);
                if (s instanceof ItemStack) {
                    ItemStack stack = (ItemStack) s;
                    if (stack == null || stack.stackSize <= 0) continue;
                    String nm = stack.getDisplayName();
                    if (nm == null || nm.isEmpty()) nm = stack.getItem().getUnlocalizedName();
                    d.rods[y * 3 + x] = stack.stackSize > 1 ? nm + " x" + stack.stackSize : nm;
                }
            }
        }
        return d;
    }

    /** 默认友好多行文本；非核心返回 null */
    public static String summarize(TileEntity core) {
        ReactorData d = aggregate(core);
        return d == null ? null : summarize(d);
    }

    public static String summarize(ReactorData d) {
        StringBuilder sb = new StringBuilder();
        sb.append("[IC2] \u6838\u53cd\u5e94\u5806");
        sb.append('\n').append("\u70ed\u91cf: ").append(d.heat).append('/').append(d.maxHeat);
        sb.append('\n').append("EU\u8f93\u51fa: ").append(trimNum(d.euOutput)).append(" EU/t");
        if (d.tickRate > 0) sb.append('\n').append("\u5468\u671f: ").append(d.tickRate).append(" t");
        if (d.fluidCooled) sb.append('\n').append("\u51b7\u5374: \u6d41\u4f53\u51b7\u5374");
        boolean any = false;
        for (int i = 0; i < 9; i++) {
            if (d.rods[i] == null) continue;
            if (!any) {
                sb.append('\n').append("\u94c0\u68d2: ");
                any = true;
            } else {
                sb.append("; ");
            }
            sb.append((i % 3) + "," + (i / 3) + " ").append(d.rods[i]);
        }
        return sb.toString();
    }

    /** 合成 NBT：核心完整 NBT + 虚拟键（Heat/MaxHeat/EUOutput/TickRate/FluidCooled/Rod{i}_*） */
    public static NBTTagCompound buildNbt(TileEntity core) {
        NBTTagCompound nbt = new NBTTagCompound();
        if (core == null) return nbt;
        try {
            core.writeToNBT(nbt);
        } catch (Throwable ignore) {}
        ReactorData d = aggregate(core);
        if (d == null) return nbt;
        nbt.setInteger("Heat", d.heat);
        nbt.setInteger("MaxHeat", d.maxHeat);
        nbt.setInteger("EUOutput", (int) d.euOutput);
        nbt.setInteger("TickRate", d.tickRate);
        nbt.setBoolean("FluidCooled", d.fluidCooled);
        for (int i = 0; i < 9; i++) {
            if (d.rods[i] != null) nbt.setString("Rod" + i + "_Name", d.rods[i]);
        }
        return nbt;
    }

    private static String trimNum(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    // ===== 反射工具（按类缓存，失败用占位标记，ConcurrentHashMap 不允许 null 值） =====

    private static final Object NO_METHOD = new Object();
    private static final Map<Class<?>, Map<String, Object>> METHOD_CACHE = new ConcurrentHashMap<Class<?>, Map<String, Object>>();

    private static Object invoke(Object obj, String name, Object... args) {
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
            return ((Method) method).invoke(obj, args);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object invokeNoArg(Object obj, String name) {
        return invoke(obj, name);
    }

    private static int intOf(Object obj, String name) {
        Object v = invoke(obj, name);
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    private static double doubleOf(Object obj, String name) {
        Object v = invoke(obj, name);
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0D;
    }

    private static Boolean boolOf(Object obj, String name) {
        Object v = invoke(obj, name);
        return v instanceof Boolean ? (Boolean) v : null;
    }
}
