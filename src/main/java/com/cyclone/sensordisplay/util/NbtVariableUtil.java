package com.cyclone.sensordisplay.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 通用 NBT 变量提取：
 * 把任意 TileEntity 的原始 NBT 拍平成一组「路径 → 类型 → 格式化值」的变量，
 * 供传感器 GUI 的模板编辑器列出、勾选、重命名，并最终写成白名单模板。
 *
 * 噪音键（x/y/z/facing/id 等纯位置标识）被过滤掉，不展示。
 * 物品条目（含数字 id 或字符串 id）会被识别并格式化成 "名字 ×数量"。
 */
public class NbtVariableUtil {

    /** 一条提取出的变量 */
    public static class Var {
        public String path;  // NBT 遍历路径，如 "InvSlots.input.Contents"
        public String type;  // number / string / item / items / list
        public String value; // 格式化预览值

        public Var(String path, String type, String value) {
            this.path = path;
            this.type = type;
            this.value = value;
        }
    }

    /** 过滤掉的噪音键（纯位置/标识，对玩家无意义） */
    private static final Set<String> NOISE = new HashSet<String>(Arrays.asList(
            "x", "y", "z", "facing", "id", "Id", "dimension", "Dimension", "EnergyNet",
            "_x", "_y", "_z", "name", "Name", "Direction", "direction"
    ));

    /** 非物品复合列表最多逐元素展开的元素个数（防超大列表刷屏） */
    private static final int MAX_LIST_ELEMENTS = 32;
    /** 递归深度上限，防嵌套列表爆栈/刷屏 */
    private static final int MAX_DEPTH = 10;

    public static List<Var> flatten(NBTTagCompound nbt) {
        List<Var> out = new ArrayList<Var>();
        if (nbt != null) walk(nbt, "", out, 0);
        return out;
    }

    private static void walk(NBTTagCompound tag, String prefix, List<Var> out, int depth) {
        if (depth > MAX_DEPTH) return;
        for (Object keyObj : tag.func_150296_c()) {
            String key = (String) keyObj;
            if (NOISE.contains(key)) continue;
            NBTBase t = tag.getTag(key);
            if (t == null) continue;
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            int id = t.getId();
            if (id == 8) { // string
                out.add(new Var(path, "string", ((NBTTagString) t).func_150285_a_()));
            } else if (id >= 1 && id <= 6) { // byte/short/int/long/float/double
                out.add(new Var(path, "number", formatNumber(t, id)));
            } else if (id == 7) { // byte 数组
                out.add(new Var(path, "list", "byte[" + ((NBTTagByteArray) t).func_150292_c().length + "]"));
            } else if (id == 11) { // int 数组
                out.add(new Var(path, "list", "int[" + ((NBTTagIntArray) t).func_150302_c().length + "]"));
            } else if (id == 10) { // compound
                NBTTagCompound c = (NBTTagCompound) t;
                if (isItem(c)) {
                    out.add(new Var(path, "item", formatItem(c)));
                } else {
                    walk(c, path, out, depth + 1);
                }
            } else if (id == 9) { // list
                NBTTagList list = (NBTTagList) t;
                if (list.tagCount() > 0 && list.func_150303_d() == 10) {
                    NBTTagCompound first = list.getCompoundTagAt(0);
                    if (isItem(first)) {
                        out.add(new Var(path, "items", formatItemList(list)));
                    } else {
                        // 非物品的复合列表（流体槽 tanks、能量阵列等）：
                        // 逐个元素递归提取内层变量，暴露 tanks.0.FluidName / tanks.0.Amount 等
                        walkListElements(list, path, out, depth + 1);
                    }
                } else {
                    out.add(new Var(path, "list", "[" + list.tagCount() + "]"));
                }
            }
        }
    }

    /** 展开非物品复合列表的每个元素：路径带下标（prefix.0 / prefix.1 ...） */
    private static void walkListElements(NBTTagList list, String prefix, List<Var> out, int depth) {
        if (depth > MAX_DEPTH) return;
        int n = list.tagCount();
        int cap = Math.min(n, MAX_LIST_ELEMENTS);
        for (int i = 0; i < cap; i++) {
            walk(list.getCompoundTagAt(i), prefix + "." + i, out, depth + 1);
        }
    }

    /** 按路径解析出格式化值，供 DisplayFormatter 的 "var.<path>" 提取器使用；路径缺失返回 null。支持列表下标（tanks.0.Amount） */
    public static String resolvePath(NBTTagCompound nbt, String path) {
        NBTBase cur = descend(nbt, path);
        return cur == null ? null : formatTag(cur);
    }

    private static String formatTag(NBTBase t) {
        int id = t.getId();
        if (id == 8) return ((NBTTagString) t).func_150285_a_();
        if (id >= 1 && id <= 6) return formatNumber(t, id);
        if (id == 7) return "byte[" + ((NBTTagByteArray) t).func_150292_c().length + "]";
        if (id == 11) return "int[" + ((NBTTagIntArray) t).func_150302_c().length + "]";
        if (id == 10) {
            NBTTagCompound c = (NBTTagCompound) t;
            if (isItem(c)) return formatItem(c);
            return "{...}";
        }
        if (id == 9) {
            NBTTagList l = (NBTTagList) t;
            if (l.tagCount() > 0 && l.func_150303_d() == 10 && isItem(l.getCompoundTagAt(0))) return formatItemList(l);
            return "[" + l.tagCount() + "]";
        }
        return null;
    }

    /** 按路径解析出类型化值：数值→Double、字符串→String、物品→"名字 ×n"、列表→"[n]"；缺失返回 null。供表达式引擎使用。支持列表下标（tanks.0.Amount） */
    public static Object resolveValue(NBTTagCompound nbt, String path) {
        NBTBase cur = descend(nbt, path);
        return cur == null ? null : formatValue(cur);
    }

    /** 按点分路径下降：支持复合键与列表下标（tanks.0.Amount） */
    private static NBTBase descend(NBTTagCompound root, String path) {
        if (root == null || path == null || path.isEmpty()) return null;
        NBTBase cur = root;
        String[] parts = path.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            cur = step(cur, parts[i]);
            if (cur == null) return null;
        }
        return cur;
    }

    /** 取下一级：复合键取 tag；列表段解析成整数下标取元素（仅限复合元素列表） */
    private static NBTBase step(NBTBase cur, String part) {
        if (cur instanceof NBTTagCompound) {
            NBTTagCompound c = (NBTTagCompound) cur;
            if (!c.hasKey(part)) return null;
            return c.getTag(part);
        }
        if (cur instanceof NBTTagList) {
            NBTTagList l = (NBTTagList) cur;
            if (l.func_150303_d() != 10) return null; // 原始类型列表不支持下标
            int idx;
            try { idx = Integer.parseInt(part); } catch (Exception e) { return null; }
            if (idx < 0 || idx >= l.tagCount()) return null;
            return l.getCompoundTagAt(idx);
        }
        return null;
    }

    private static Object formatValue(NBTBase t) {
        int id = t.getId();
        switch (id) {
            case 1: return (double) ((NBTTagByte) t).func_150290_f();
            case 2: return (double) ((NBTTagShort) t).func_150289_e();
            case 3: return (double) ((NBTTagInt) t).func_150287_d();
            case 4: return (double) ((NBTTagLong) t).func_150291_c();
            case 5: return (double) ((NBTTagFloat) t).func_150288_h();
            case 6: return ((NBTTagDouble) t).func_150286_g();
            case 8: return ((NBTTagString) t).func_150285_a_();
            case 7: return "byte[" + ((NBTTagByteArray) t).func_150292_c().length + "]";
            case 11: return "int[" + ((NBTTagIntArray) t).func_150302_c().length + "]";
            case 10: {
                NBTTagCompound c = (NBTTagCompound) t;
                return isItem(c) ? formatItem(c) : null;
            }
            case 9: {
                NBTTagList l = (NBTTagList) t;
                if (l.tagCount() > 0 && l.func_150303_d() == 10 && isItem(l.getCompoundTagAt(0))) return formatItemList(l);
                return "[" + l.tagCount() + "]";
            }
            default: return null;
        }
    }

    private static String formatNumber(NBTBase t, int id) {
        switch (id) {
            case 1: return String.valueOf(((NBTTagByte) t).func_150290_f());
            case 2: return String.valueOf(((NBTTagShort) t).func_150289_e());
            case 3: return String.valueOf(((NBTTagInt) t).func_150287_d());
            case 4: return String.valueOf(((NBTTagLong) t).func_150291_c());
            case 5: return String.valueOf(((NBTTagFloat) t).func_150288_h());
            case 6: return String.valueOf(((NBTTagDouble) t).func_150286_g());
            default: return null;
        }
    }

    /** 判定一个 NBTTagCompound 是否是物品条目（有 id，且有 Count 或 Index） */
    public static boolean isItem(NBTTagCompound c) {
        return c != null && c.hasKey("id") && (c.hasKey("Count") || c.hasKey("Index"));
    }

    /** 把物品条目还原成 ItemStack（兼容 IC2 数字 id 与原版字符串 id） */
    public static ItemStack toStack(NBTTagCompound c) {
        if (!isItem(c)) return null;
        NBTBase idt = c.getTag("id");
        int count = c.hasKey("Count") ? c.getByte("Count") : 1;
        int damage = c.getShort("Damage");
        Item item = null;
        if (idt.getId() == 2) item = Item.getItemById(((NBTTagShort) idt).func_150289_e());
        else if (idt.getId() == 3) item = Item.getItemById(((NBTTagInt) idt).func_150287_d());
        else if (idt.getId() == 8) {
            String name = ((NBTTagString) idt).func_150285_a_();
            Object obj = Item.itemRegistry.getObject(name);
            if (obj instanceof Item) item = (Item) obj;
        }
        if (item == null) return null;
        return new ItemStack(item, count, damage);
    }

    private static String formatItem(NBTTagCompound c) {
        ItemStack st = toStack(c);
        if (st == null) return "?";
        return itemName(st) + " \u00d7" + st.stackSize;
    }

    private static String formatItemList(NBTTagList list) {
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        int shown = 0;
        int total = 0;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound c = list.getCompoundTagAt(i);
            ItemStack st = toStack(c);
            if (st == null) continue;
            total++;
            if (shown >= 4) continue;
            if (any) sb.append(", ");
            sb.append(itemName(st)).append(" \u00d7").append(st.stackSize);
            shown++;
            any = true;
        }
        if (total > shown) sb.append(" +").append(total - shown).append("\u4ef6");
        return any ? sb.toString() : "\u7a7a";
    }

    private static String itemName(ItemStack st) {
        try {
            String name = st.getDisplayName();
            return (name == null || name.isEmpty()) ? st.getItem().getUnlocalizedName() : name;
        } catch (Exception e) {
            return st.getItem() == null ? "?" : st.getItem().getUnlocalizedName();
        }
    }
}
