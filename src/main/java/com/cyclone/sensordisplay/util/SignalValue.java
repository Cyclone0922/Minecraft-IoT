package com.cyclone.sensordisplay.util;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 信号值（执行器"技能0"信号域传输的值）。
 * 三型制：DOUBLE / BOOLEAN / STRING。
 * NBT 序列化：{prefix}_T=类型标记，{prefix}_V=值。
 */
public final class SignalValue {

    public static final int TYPE_DOUBLE = 0;
    public static final int TYPE_BOOL = 1;
    public static final int TYPE_STRING = 2;

    public final int type;
    public final double num;
    public final boolean bool;
    public final String str;

    private SignalValue(int type, double num, boolean bool, String str) {
        this.type = type;
        this.num = num;
        this.bool = bool;
        this.str = str;
    }

    public static SignalValue ofDouble(double v) {
        return new SignalValue(TYPE_DOUBLE, v, false, null);
    }

    public static SignalValue ofBool(boolean v) {
        return new SignalValue(TYPE_BOOL, 0, v, null);
    }

    public static SignalValue ofString(String v) {
        return new SignalValue(TYPE_STRING, 0, false, v == null ? "" : v);
    }

    /** truthy：DOUBLE 非 0 且非 NaN；BOOLEAN 为 true；STRING 非空 */
    public boolean truthy() {
        if (type == TYPE_DOUBLE) return num != 0 && !Double.isNaN(num);
        if (type == TYPE_BOOL) return bool;
        return str != null && !str.isEmpty();
    }

    public double asDouble() {
        if (type == TYPE_DOUBLE) return num;
        if (type == TYPE_BOOL) return bool ? 1 : 0;
        String s = str == null ? "" : str.trim();
        if (s.isEmpty()) return 0;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
    }

    public String asString() {
        if (type == TYPE_STRING) return str == null ? "" : str;
        if (type == TYPE_BOOL) return bool ? "true" : "false";
        if (Double.isNaN(num) || Double.isInfinite(num)) return "\u2014";
        if (num == Math.rint(num) && Math.abs(num) < 1e15) return String.valueOf((long) num);
        String s = String.valueOf(num);
        while (s.indexOf('.') >= 0 && (s.endsWith("0") || s.endsWith("."))) {
            s = s.endsWith(".") ? s.substring(0, s.length() - 1) : s.substring(0, s.length() - 1);
        }
        return s;
    }

    public void writeToNBT(NBTTagCompound tag, String prefix) {
        tag.setInteger(prefix + "_T", type);
        if (type == TYPE_DOUBLE) tag.setDouble(prefix + "_V", num);
        else if (type == TYPE_BOOL) tag.setBoolean(prefix + "_V", bool);
        else tag.setString(prefix + "_V", str == null ? "" : str);
    }

    /** 读信号值；键缺失返回 null */
    public static SignalValue readFromNBT(NBTTagCompound tag, String prefix) {
        if (tag == null || !tag.hasKey(prefix + "_T")) return null;
        int t = tag.getInteger(prefix + "_T");
        if (t == TYPE_DOUBLE) return ofDouble(tag.getDouble(prefix + "_V"));
        if (t == TYPE_BOOL) return ofBool(tag.getBoolean(prefix + "_V"));
        return ofString(tag.getString(prefix + "_V"));
    }
}
