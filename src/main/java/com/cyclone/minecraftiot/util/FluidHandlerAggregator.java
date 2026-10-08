package com.cyclone.minecraftiot.util;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

/**
 * 通用液体容器（IFluidHandler）数据聚合器。
 *
 * 覆盖"没有物品格、但带水箱"的一类方块：RC 蒸汽引擎（TileEngineSteam）、蒸汽涡轮、
 * 蒸汽捕集器，以及任意 mod 的储罐/流体容器。这类方块不是 IInventory、也不是 RC 蓄水器，
 * 此前被传感器扫描判定跳过；这里按 Forge 官方流体 API 直接读取，不依赖具体 mod 类。
 *
 * 数据来源：IFluidHandler.getTankInfo(ForgeDirection.UNKNOWN) → FluidTankInfo[]（液体名/当前量/容量）。
 *
 * 消费方式与 TankAggregator 一致：
 *   summarize()：默认友好多行文本（"液体N: 名 量/容量 mB"）；
 *   injectVirtualData()：以虚拟 NBT 键 Tank{i}_FluidName / Tank{i}_Amount / Tank{i}_Capacity 注入，
 *   供表达式编辑器拍平变量、表达式引用。
 */
public class FluidHandlerAggregator {

    public static boolean isFluidHandler(TileEntity t) {
        return t instanceof IFluidHandler;
    }

    /** 聚合结果：tanks 为空数组表示无槽位/空容器 */
    public static TankAggregator.TankData aggregate(TileEntity te) {
        if (te == null || !(te instanceof IFluidHandler)) return null;
        TankAggregator.TankData d = new TankAggregator.TankData();
        try {
            FluidTankInfo[] infos = ((IFluidHandler) te).getTankInfo(ForgeDirection.UNKNOWN);
            if (infos == null) return d;
            for (FluidTankInfo info : infos) {
                if (info == null) continue;
                TankAggregator.TankSlot s = new TankAggregator.TankSlot();
                FluidStack fs = info.fluid;
                if (fs != null && fs.getFluid() != null) {
                    s.fluidName = fs.getFluid().getName();
                    s.amount = fs.amount;
                }
                s.capacity = info.capacity;
                d.tanks.add(s);
            }
        } catch (Throwable ignore) {}
        return d;
    }

    /** 输出"液体N: 名 量/容量 mB"多行友好文本；非液体容器返回 null */
    public static String summarize(TileEntity te) {
        TankAggregator.TankData d = aggregate(te);
        if (d == null || d.tanks.isEmpty()) return null;
        return TankAggregator.summarize(d);
    }

    /** 把聚合结果以虚拟 NBT 键注入（Tank{i}_FluidName / _Amount / _Capacity） */
    public static void injectVirtualData(TileEntity te, NBTTagCompound nbt) {
        TankAggregator.TankData d = aggregate(te);
        if (d == null || nbt == null) return;
        for (int i = 0; i < d.tanks.size(); i++) {
            TankAggregator.TankSlot s = d.tanks.get(i);
            if (s == null) continue;
            String p = "Tank" + i;
            if (s.fluidName != null) nbt.setString(p + "_FluidName", s.fluidName);
            nbt.setInteger(p + "_Amount", (int) s.amount);
            nbt.setInteger(p + "_Capacity", (int) s.capacity);
        }
    }
}
