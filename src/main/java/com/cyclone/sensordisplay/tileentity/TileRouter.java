package com.cyclone.sensordisplay.tileentity;

import com.cyclone.sensordisplay.util.EnergyDualTrack;
import com.cyclone.sensordisplay.util.MacAddress;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/**
 * 路由器 TileEntity（P1 单方块骨架）。
 *
 * 职责：
 * - 网络根节点，持有 IE 能量缓存（EnergyDualTrack）
 * - 出厂生成固定 MAC 地址，可配置别名
 * - P1：供能（IE 缓存）、设备注册占位；多方块拼接/AP/存储器连接在后续阶段扩展
 *
 * 耗电结算在后续阶段实现；P1 路由器只做能量存/取与标识。
 */
public class TileRouter extends TileEntity {

    private final EnergyDualTrack energy = new EnergyDualTrack(10000);
    private String mac = null;       // 出厂生成，固定
    private String alias = "";        // 玩家可配置别名
    private int level = 1;            // 路由器等级（P1 固定 1）

    public TileRouter() {}

    /** 懒生成 MAC：第一次读取时如果还没有就生成一个 */
    public String getMac() {
        if (mac == null) {
            mac = MacAddress.generate();
            markDirty();
        }
        return mac;
    }

    public String getAlias() { return alias == null ? "" : alias; }

    public void setAlias(String a) {
        this.alias = a == null ? "" : a;
        markDirty();
    }

    public int getLevel() { return level; }

    public EnergyDualTrack getEnergy() { return energy; }

    /** 供外部能量输入调用：amount 为外部能量单位，1:1 转 IE，返回实际转入量 */
    public double acceptEnergy(double amount) {
        double got = energy.addIe(amount);
        if (got > 0) markDirty();
        return got;
    }

    /** 设备请求消耗 IE（执行器等调用），返回是否成功 */
    public boolean requestEnergy(double amount) {
        if (energy.consumeIe(amount)) {
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        energy.writeToNBT(tag, "Router");
        if (mac != null) tag.setString("Mac", mac);
        if (alias != null && !alias.isEmpty()) tag.setString("Alias", alias);
        tag.setInteger("Level", level);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        energy.readFromNBT(tag, "Router");
        if (tag.hasKey("Mac")) mac = tag.getString("Mac");
        if (tag.hasKey("Alias")) alias = tag.getString("Alias");
        if (tag.hasKey("Level")) level = tag.getInteger("Level");
    }
}
