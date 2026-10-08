package com.cyclone.minecraftiot.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.tileentity.TileEntity;

/**
 * IC2 运行时能量读取（导线电流 / 电压）。
 *
 * IC2 导线电流不存在 tile 也不在 NBT：电流由 IC2 EnergyNet 每 tick 在图网络上动态求解，
 * 结果落在每个节点(含导线)的 NodeStats(energyIn/energyOut/voltage) 上。
 * 这里复用 IC2 官方 EU 电表(ItemToolMeter→ContainerMeter)的读取方式：
 *   EnergyNet.instance.getNodeStats(TileEntity) -> NodeStats.getEnergyIn()/getEnergyOut()/getVoltage()
 * 该调用只是按 tile 做一次查表、返回当 tick 已算好的统计，不会触发网络重算或遍历，成本近似 O(1)。
 *
 * 通过类名反射访问 ic2.api.energy.*，避免对未安装 IC2 产生硬依赖：IC2 不在场时静默返回 null。
 */
public final class Ic2Runtime {

    private static boolean init;
    private static boolean available;
    private static Object netInstance;      // ic2.api.energy.EnergyNet.instance
    private static Method getNodeStats;     // IEnergyNet.getNodeStats(TileEntity)
    private static Method getEnergyIn;      // NodeStats.getEnergyIn()
    private static Method getEnergyOut;     // NodeStats.getEnergyOut()
    private static Method getVoltage;       // NodeStats.getVoltage()

    private Ic2Runtime() {}

    private static void init() {
        if (init) return;
        init = true;
        try {
            Class<?> energyNet = Class.forName("ic2.api.energy.EnergyNet");
            Field f = energyNet.getField("instance");
            netInstance = f.get(null);
            // getNodeStats 声明在 IEnergyNet 接口，EnergyNet 类本身只有 instance 字段。
            // 从运行时实现类（EnergyNetGlobal，公开声明 public getNodeStats(TileEntity)）取最稳。
            getNodeStats = netInstance.getClass().getMethod("getNodeStats", TileEntity.class);
            Class<?> nodeStats = Class.forName("ic2.api.energy.NodeStats");
            getEnergyIn = nodeStats.getMethod("getEnergyIn");
            getEnergyOut = nodeStats.getMethod("getEnergyOut");
            getVoltage = nodeStats.getMethod("getVoltage");
            available = netInstance != null;
        } catch (Throwable t) {
            available = false;
        }
    }

    /** 该方块是否属于 IC2 能量网络节点（source/conductor/sink）。 */
    public static boolean isIc2EnergyTile(TileEntity te) {
        if (te == null) return false;
        String n = te.getClass().getName();
        return n.startsWith("ic2.");
    }

    /**
     * 读取某方块当前 tick 的 IC2 能量统计。
     * @return [energyIn, energyOut, voltage]；IC2 未安装 / 非网络节点 / 读取失败时返回 null。
     */
    public static double[] readStats(TileEntity te) {
        init();
        if (!available || te == null) return null;
        try {
            Object stats = getNodeStats.invoke(netInstance, te);
            if (stats == null) return null;
            double in  = ((Number) getEnergyIn.invoke(stats)).doubleValue();
            double out = ((Number) getEnergyOut.invoke(stats)).doubleValue();
            double v   = ((Number) getVoltage.invoke(stats)).doubleValue();
            return new double[]{in, out, v};
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 把读取到的统计注入 rawNbt（合成键 EUIn/EUOut/Voltage），使现有 NBT 变量提取器、
     * 模板编辑器与表达式插值都能直接引用（如 {EUIn}、{Voltage}）。
     */
    public static void injectIntoNbt(net.minecraft.nbt.NBTTagCompound nbt, double[] stats) {
        if (nbt == null || stats == null) return;
        nbt.setDouble("EUIn", stats[0]);
        nbt.setDouble("EUOut", stats[1]);
        nbt.setDouble("Voltage", stats[2]);
    }
}
