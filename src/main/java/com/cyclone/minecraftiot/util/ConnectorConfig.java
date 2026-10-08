package com.cyclone.minecraftiot.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 连接器（绑定卡）显示配置的读写工具。
 *
 * 配置存储在连接器物品的 NBT 里（不再依赖 minecraftiot.json 全局配置文件）：
 *   - cfgMode: byte  显示模式
 *       0 = 原始NBT（不套用任何友好方案，直接显示原始 NBT 摘要）
 *       1 = 默认友好方案（按目标机器 NBT 自动组合变量 / 锅炉蓄水器聚合展示）
 *       2 = 自定义表达式（TemplateExpr 语法，一行表达式求值为多行）
 *   - cfgExpr: String  自定义表达式（仅 MODE_CUSTOM 时有效）
 *
 * 绑定信息（由传感器"写卡"写入）：
 *   - sensorX / sensorY / sensorZ / dimension / dirIndex
 *   - display.Name：自定义卡名（物品栏 tooltip 显示）
 */
public class ConnectorConfig {

    public static final int MODE_RAW = 0;      // 原始NBT
    public static final int MODE_DEFAULT = 1;  // 默认友好方案
    public static final int MODE_CUSTOM = 2;   // 自定义表达式

    private static final String KEY_MODE = "cfgMode";
    private static final String KEY_EXPR = "cfgExpr";

    // ============ 显示模式 ============

    public static int getMode(ItemStack card) {
        if (card == null) return MODE_RAW;
        NBTTagCompound tag = card.getTagCompound();
        if (tag == null || !tag.hasKey(KEY_MODE)) return MODE_RAW;
        return tag.getByte(KEY_MODE);
    }

    public static void setMode(ItemStack card, int mode) {
        if (card == null) return;
        NBTTagCompound tag = ensureTag(card);
        tag.setByte(KEY_MODE, (byte) mode);
    }

    /** 是否有自定义显示配置（默认友好或自定义表达式；原始NBT 视为"无配置"） */
    public static boolean hasConfig(ItemStack card) {
        int mode = getMode(card);
        if (mode == MODE_DEFAULT) return true;
        if (mode == MODE_CUSTOM) {
            String e = getExpr(card);
            return e != null && !e.isEmpty();
        }
        return false;
    }

    /** 清空配置：恢复为"原始NBT"模式（显示原始 NBT） */
    public static void clearConfig(ItemStack card) {
        if (card == null) return;
        NBTTagCompound tag = card.getTagCompound();
        if (tag == null) return;
        tag.removeTag(KEY_MODE);
        tag.removeTag(KEY_EXPR);
    }

    // ============ 自定义表达式 ============

    public static String getExpr(ItemStack card) {
        if (card == null) return "";
        NBTTagCompound tag = card.getTagCompound();
        return (tag == null || !tag.hasKey(KEY_EXPR)) ? "" : tag.getString(KEY_EXPR);
    }

    public static void setExpr(ItemStack card, String expr) {
        if (card == null) return;
        NBTTagCompound tag = ensureTag(card);
        tag.setString(KEY_EXPR, expr == null ? "" : expr);
    }

    // ============ 绑定信息 ============

    public static boolean hasBinding(ItemStack card) {
        if (card == null) return false;
        NBTTagCompound tag = card.getTagCompound();
        return tag != null && tag.hasKey("sensorX");
    }

    /** 清空绑定信息（恢复出厂设置，保留 NBT 结构） */
    public static void clearBinding(ItemStack card) {
        if (card == null) return;
        NBTTagCompound tag = card.getTagCompound();
        if (tag == null) return;
        tag.removeTag("sensorX");
        tag.removeTag("sensorY");
        tag.removeTag("sensorZ");
        tag.removeTag("dimension");
        tag.removeTag("dirIndex");
        tag.removeTag(KEY_MODE);
        tag.removeTag(KEY_EXPR);
    }

    // ============ 卡名 ============

    /** 自定义卡名（display.Name）；未命名返回空串 */
    public static String getName(ItemStack card) {
        if (card == null) return "";
        NBTTagCompound tag = card.getTagCompound();
        if (tag == null || !tag.hasKey("display")) return "";
        NBTTagCompound d = tag.getCompoundTag("display");
        return d.hasKey("Name") ? d.getString("Name") : "";
    }

    public static void setName(ItemStack card, String name) {
        if (card == null) return;
        NBTTagCompound tag = ensureTag(card);
        NBTTagCompound d = tag.hasKey("display") ? tag.getCompoundTag("display") : new NBTTagCompound();
        if (name == null || name.isEmpty()) {
            d.removeTag("Name");
        } else {
            d.setString("Name", name);
        }
        if (d.hasNoTags()) {
            tag.removeTag("display");
        } else {
            tag.setTag("display", d);
        }
    }

    // ============ 拷贝 ============

    /** 拷贝显示配置（模式+表达式），不含绑定信息 */
    public static void copyConfig(ItemStack src, ItemStack dst) {
        if (src == null || dst == null) return;
        NBTTagCompound srcTag = src.getTagCompound();
        NBTTagCompound dstTag = ensureTag(dst);
        if (srcTag != null && srcTag.hasKey(KEY_MODE)) {
            dstTag.setByte(KEY_MODE, srcTag.getByte(KEY_MODE));
        } else {
            dstTag.removeTag(KEY_MODE);
        }
        if (srcTag != null && srcTag.hasKey(KEY_EXPR)) {
            dstTag.setString(KEY_EXPR, srcTag.getString(KEY_EXPR));
        } else {
            dstTag.removeTag(KEY_EXPR);
        }
    }

    /** 拷贝全部（配置 + 绑定信息 + 卡名） */
    public static void copyAll(ItemStack src, ItemStack dst) {
        if (src == null || dst == null) return;
        NBTTagCompound srcTag = src.getTagCompound();
        if (srcTag == null) return;
        NBTTagCompound dstTag = ensureTag(dst);
        // 拷贝绑定键
        for (String k : new String[]{"sensorX", "sensorY", "sensorZ", "dimension", "dirIndex"}) {
            if (srcTag.hasKey(k)) dstTag.setInteger(k, srcTag.getInteger(k));
            else dstTag.removeTag(k);
        }
        copyConfig(src, dst);
        // 拷贝卡名
        String name = getName(src);
        if (!name.isEmpty()) setName(dst, name);
        else setName(dst, "");
    }

    // ============ 内部 ============

    private static NBTTagCompound ensureTag(ItemStack card) {
        NBTTagCompound tag = card.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            card.setTagCompound(tag);
        }
        return tag;
    }
}
