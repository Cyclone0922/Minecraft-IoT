package com.cyclone.sensordisplay.util;

/**
 * 能量双轨缓存。
 *
 * 双轨模型（决策②）：
 * 1. IE（Internet Energy，本网能量）：本 mod 自身运转的能耗货币。
 *    外部 RF/EU/gJ/AE 输入后 1:1 转 IE，只进不出。
 * 2. 过路能量：被路由中继的外部能量，原样转发、不转 IE、不跨类型，
 *    有 20%~50% 损耗。过路能量由路由器单独按类型记账，本类只管理 IE。
 *
 * P1 只实现 IE 缓存：存/取/容量/是否足够。
 */
public class EnergyDualTrack {

    private double ieStored = 0;
    private double ieCapacity = 10000; // P1 默认容量

    public EnergyDualTrack() {}

    public EnergyDualTrack(double capacity) {
        this.ieCapacity = capacity;
    }

    /** 当前 IE 存量 */
    public double getIe() { return ieStored; }

    /** IE 容量 */
    public double getIeCapacity() { return ieCapacity; }

    public void setIeCapacity(double c) { this.ieCapacity = Math.max(0, c); }

    /** IE 占满百分比 0~1 */
    public double getIePercent() {
        return ieCapacity <= 0 ? 0 : Math.min(1, ieStored / ieCapacity);
    }

    /**
     * 存入 IE，返回实际存入量（受容量限制）。
     * 外部能量输入调用此方法完成 1:1 转换。
     */
    public double addIe(double amount) {
        if (amount <= 0) return 0;
        double space = ieCapacity - ieStored;
        double actual = Math.min(amount, space);
        ieStored += actual;
        return actual;
    }

    /** IE 是否足够支付 amount */
    public boolean canAfford(double amount) {
        return ieStored >= amount;
    }

    /**
     * 尝试消耗 IE，返回是否成功。
     * 红石信号操作不耗能（调用方不调用此方法）。
     */
    public boolean consumeIe(double amount) {
        if (amount <= 0) return true;
        if (ieStored < amount) return false;
        ieStored -= amount;
        return true;
    }

    /** 直接设置存量（调试/创造模式用） */
    public void setIe(double v) {
        this.ieStored = Math.max(0, Math.min(ieCapacity, v));
    }

    // ===== NBT 持久化 =====

    public void writeToNBT(net.minecraft.nbt.NBTTagCompound tag, String prefix) {
        tag.setDouble(prefix + "Ie", ieStored);
        tag.setDouble(prefix + "IeCap", ieCapacity);
    }

    public void readFromNBT(net.minecraft.nbt.NBTTagCompound tag, String prefix) {
        if (tag.hasKey(prefix + "Ie")) ieStored = tag.getDouble(prefix + "Ie");
        if (tag.hasKey(prefix + "IeCap")) ieCapacity = tag.getDouble(prefix + "IeCap");
    }
}
