package com.cyclone.minecraftiot.util;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Railcraft 锅炉数据聚合器。
 * 锅炉的多方块结构里：热量在主控燃烧室（getTemperature），而水/蒸汽分散在每一个锅炉方块
 * （每个 TileBoiler 各自持有 tankWater/tankSteam）。单读一块都拿不到总量。
 * 这里从传感器指向的锅炉方块出发，沿结构 BFS 聚合所有锅炉方块的水/蒸汽总量，
 * 并读取主控燃烧室的温度。
 *
 * 两种消费方式：
 *  1) summarize()：直接产出一段友好多行文本（无模板时的内置展示）。
 *  2) injectVirtualData()：把聚合结果以虚拟 NBT 键（Temperature/Water/WaterCapacity/Steam/SteamCapacity）
 *     注入 NBT，供模板编辑器作为变量勾选/重命名，也供 DisplayFormatter 按 var. 路径解析。
 * 仅依赖 Forge 流体 API，不依赖 Railcraft 类。
 */
public class BoilerAggregator {

    private static final int MAX_BLOCKS = 256;
    private static final Map<Class<?>, Method> TEMP_CACHE = new HashMap<Class<?>, Method>();

    /** 聚合结果 */
    public static class BoilerData {
        public Float temperature;              // 主控燃烧室温度（°C），无则 null
        public long waterAmt, waterCap;        // 水 amount/capacity（mB）
        public long steamAmt, steamCap;        // 蒸汽 amount/capacity（mB）
    }

    /** 判断是否为 Railcraft 锅炉方块（IFluidHandler 且类名在 mods.railcraft 包且含 Boiler） */
    public static boolean isBoilerTile(TileEntity t) {
        if (!(t instanceof IFluidHandler)) return false;
        String n = t.getClass().getName();
        return n.startsWith("mods.railcraft.") && n.contains("Boiler");
    }

    /** 聚合整台锅炉的数据；目标不是锅炉方块时返回 null */
    public static BoilerData aggregate(TileEntity te) {
        if (te == null || te.getWorldObj() == null) return null;
        if (!isBoilerTile(te)) return null;

        BoilerData d = new BoilerData();
        d.temperature = readTemperature(te);

        // 每个锅炉方块（燃烧室+锅炉方块）各自持有 tankWater/tankSteam，
        // 且都把自己的槽序列化进 NBT 的 "tanks" 列表（结构：{FluidName:'water',Amount:27477,tank:0b}）。
        // 数量从各块 NBT 逐块累加（与原始 NBT 同源、可靠）。
        // 容量不能累加：Railcraft 整机共用主控 tankManager，任意方块的 getTankInfo 都返回同一份全结构容量
        // （= 锅炉方块数 × 1000 × 单方块基数，见 onPatternLock），逐块求和会乘上全机方块总数。
        for (TileEntity b : collectBoilerBlocks(te)) {
            NBTTagCompound nbt = new NBTTagCompound();
            try { b.writeToNBT(nbt); } catch (Exception ignore) { continue; }
            addTankAmounts(nbt, d);
        }
        // 容量：只从单个方块读一次（getTankInfo 即主控全结构容量）
        addTankCapacities(te, d);
        return d;
    }

    /** 解析一个方块 NBT 的 "tanks" 列表，按 FluidName 把 water/steam 的 amount 累加 */
    private static void addTankAmounts(NBTTagCompound nbt, BoilerData d) {
        if (nbt == null || !nbt.hasKey("tanks")) return;
        try {
            NBTTagList list = nbt.getTagList("tanks", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound tank = list.getCompoundTagAt(i);
                String name = tank.getString("FluidName");
                int amount = tank.getInteger("Amount");
                if ("water".equals(name)) {
                    d.waterAmt += amount;
                } else if ("steam".equals(name)) {
                    d.steamAmt += amount;
                }
            }
        } catch (Exception ignore) {}
    }

    /**
     * 从单个方块读容量（不累加）：Railcraft 任意锅炉方块的 getTankInfo 都返回主控 tankManager 的同一份全结构容量，
     * 下标 0=水、1=蒸汽。仅单次读取，避免乘上全机方块总数。
     */
    private static void addTankCapacities(TileEntity b, BoilerData d) {
        if (!(b instanceof IFluidHandler)) return;
        FluidTankInfo[] infos = getTankInfo((IFluidHandler) b);
        if (infos == null) return;
        for (int i = 0; i < infos.length; i++) {
            if (infos[i] == null) continue;
            if (i == 0) d.waterCap = infos[i].capacity;
            else if (i == 1) d.steamCap = infos[i].capacity;
        }
    }

    /** 输出"温度/水/蒸汽"多行友好文本；非锅炉返回 null */
    public static String summarize(TileEntity te) {
        BoilerData d = aggregate(te);
        return d == null ? null : summarize(d);
    }

    /** 由聚合结果生成"温度/水/蒸汽"多行友好文本 */
    public static String summarize(BoilerData d) {
        if (d == null) return null;
        StringBuilder sb = new StringBuilder();
        if (d.temperature != null) sb.append("\u6e29\u5ea6: ").append(fmtNum(d.temperature)).append('\u00b0' + "C\n");
        sb.append("\u6c34: ").append(d.waterAmt).append('/').append(d.waterCap).append(" mB\n");
        sb.append("\u84b8\u6c14: ").append(d.steamAmt).append('/').append(d.steamCap).append(" mB");
        return sb.toString();
    }

    /**
     * 把聚合结果以虚拟 NBT 键注入（仅当目标是锅炉方块）：
     * Temperature(float) / Water(int) / WaterCapacity(int) / Steam(int) / SteamCapacity(int)。
     * 模板编辑器据此列出可勾选变量；DisplayFormatter 按 var. 路径解析。
     */
    public static void injectVirtualData(TileEntity te, NBTTagCompound nbt) {
        BoilerData d = aggregate(te);
        if (d == null || nbt == null) return;
        injectBoilerData(d, nbt);
    }

    /** 把一份聚合结果以虚拟 NBT 键注入（供客户端编辑器使用服务端下发的数据） */
    public static void injectBoilerData(BoilerData d, NBTTagCompound nbt) {
        if (d == null || nbt == null) return;
        if (d.temperature != null) nbt.setFloat("Temperature", d.temperature);
        nbt.setInteger("Water", (int) d.waterAmt);
        nbt.setInteger("WaterCapacity", (int) d.waterCap);
        nbt.setInteger("Steam", (int) d.steamAmt);
        nbt.setInteger("SteamCapacity", (int) d.steamCap);
    }

    /** 从目标方块沿 6 邻域 BFS 收集同一锅炉结构的全部方块（数量上限防失控） */
    private static List<TileEntity> collectBoilerBlocks(TileEntity start) {
        World w = start.getWorldObj();
        List<TileEntity> out = new ArrayList<TileEntity>();
        Set<TileEntity> visited = new HashSet<TileEntity>();
        Deque<TileEntity> queue = new ArrayDeque<TileEntity>();
        queue.add(start);
        while (!queue.isEmpty() && out.size() < MAX_BLOCKS) {
            TileEntity t = queue.poll();
            if (!visited.add(t)) continue;
            if (isBoilerTile(t)) out.add(t);
            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                TileEntity n = w.getTileEntity(t.xCoord + d.offsetX, t.yCoord + d.offsetY, t.zCoord + d.offsetZ);
                if (n != null && isBoilerTile(n) && !visited.contains(n)) queue.add(n);
            }
        }
        return out;
    }

    /** 读取锅炉流体槽（仅取容量）：优先 UP，失败则逐面尝试，取第一个非空结果 */
    private static FluidTankInfo[] getTankInfo(IFluidHandler fh) {
        try {
            FluidTankInfo[] infos = fh.getTankInfo(ForgeDirection.UP);
            if (infos != null && infos.length > 0) return infos;
            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                infos = fh.getTankInfo(d);
                if (infos != null && infos.length > 0) return infos;
            }
        } catch (Exception ignore) {}
        return null;
    }

    /** 读取主控燃烧室温度 getTemperature()（反射，无则返回 null） */
    private static Float readTemperature(TileEntity te) {
        Class<?> c = te.getClass();
        Method m = TEMP_CACHE.get(c);
        if (m == null && !TEMP_CACHE.containsKey(c)) {
            try {
                m = c.getMethod("getTemperature");
            } catch (NoSuchMethodException ignore) {
                m = null;
            }
            TEMP_CACHE.put(c, m);
        }
        if (m == null) return null;
        try {
            Object v = m.invoke(te);
            return (v instanceof Number) ? ((Number) v).floatValue() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String fmtNum(float v) {
        long r = Math.round(v * 10);
        long whole = r / 10, dec = r % 10;
        return String.valueOf(whole) + (dec != 0 ? "." + Math.abs(dec) : "");
    }
}
