package com.cyclone.sensordisplay.util;

import net.minecraft.tileentity.TileEntity;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 多方块主控解析器。
 * Railcraft 9.12 的锅炉等多方块结构：只有"主控"方块存真实数据（热量、水箱等），
 * 其余从属方块（其他燃烧室/锅炉方块）没有或只有常量数据。
 * 这里通过反射识别 isMaster()/getMasterBlock() 模式（Railcraft TileMultiBlock），
 * 把从属方块解析到主控方块再读数据。仅反射 TileEntity，不产生对 Railcraft 的编译期依赖。
 * 方法引用按类缓存，避免每刻重复反射。
 */
public class MultiblockResolver {

    private static final Map<Class<?>, Method[]> CACHE = new ConcurrentHashMap<Class<?>, Method[]>();

    /** 若目标是多方块从属方块，返回其主控方块；否则（或无法识别）原样返回 */
    public static TileEntity resolve(TileEntity te) {
        if (te == null) return null;
        Class<?> c = te.getClass();
        // 只处理 Railcraft 包下的类，避免误伤其他带 isMaster 语义的方块
        if (!c.getName().startsWith("mods.railcraft.")) return te;

        Method[] ms = CACHE.get(c);
        if (ms == null) {
            Method isMaster = null, getMaster = null;
            try {
                isMaster = c.getMethod("isMaster");
            } catch (NoSuchMethodException ignore) {}
            try {
                getMaster = c.getMethod("getMasterBlock");
            } catch (NoSuchMethodException ignore) {}
            ms = new Method[] { isMaster, getMaster };
            CACHE.put(c, ms);
        }
        if (ms[0] == null || ms[1] == null) return te;

        try {
            // 已是主控：直接用
            if (Boolean.TRUE.equals(ms[0].invoke(te))) return te;
            // 从属方块：跟随到主控
            Object master = ms[1].invoke(te);
            if (master instanceof TileEntity) return (TileEntity) master;
        } catch (Exception ignore) {}
        return te;
    }
}
