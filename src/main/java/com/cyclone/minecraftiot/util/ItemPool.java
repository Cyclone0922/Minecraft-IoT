package com.cyclone.minecraftiot.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物品池（Item Pool）：网络集中物品存储的核心数据结构。
 *
 * 语义：
 *  - 以"种类"为单位，一种物品（id+meta+tag 相同）一个条目，数量可超过 64（无堆叠上限）
 *  - 内部用 LinkedHashMap 保证 GUI 显示顺序稳定
 *  - 每个条目保留一份示例 ItemStack（首个放入的 copy），用于 GUI 渲染贴图
 *
 * 容量约束由调用方（路由器/缓存器）传入：
 *  - kindCap：种类上限（默认 64）
 *  - itemCap：物品总量上限（默认 4096）
 *  add 时若超出种类上限或总量上限则拒绝放入（返回 0）。
 *
 * 序列化：存成 NBT 列表，供路由器/缓存器持久化（落档/灾备转移）。
 */
public class ItemPool {

    /** 单种类默认上限与总量默认上限（与文档一致：单块缓存器/路由器基础） */
    public static final int DEFAULT_KIND_CAP = 64;
    public static final int DEFAULT_ITEM_CAP = 4096;

    // key -> 条目；key = id:meta:tagHash
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<String, Entry>();
    private int totalCount = 0; // 当前物品总量

    private static final class Entry {
        ItemStack sample; // 示例栈（用于渲染/提取）
        int count;        // 当前数量（可 > 64）
        Entry(ItemStack sample, int count) { this.sample = sample; this.count = count; }
    }

    // ===== 查询 =====

    public int getTotalCount() { return totalCount; }
    public int getKindCount() { return entries.size(); }

    /** GUI 渲染列表：返回所有条目的示例栈 + 数量（copy，勿改原池） */
    public List<ItemStack[]> snapshot() {
        List<ItemStack[]> out = new ArrayList<ItemStack[]>();
        for (Entry e : entries.values()) {
            ItemStack s = e.sample.copy();
            s.stackSize = e.count;
            out.add(new ItemStack[]{s, new ItemStack(net.minecraft.item.Item.getItemById(0))}); // 占位
        }
        return out;
    }

    /** 返回示例栈 + 数量对（数量放 second 位置用整型栈标记占位） */
    public List<Object[]> snapshotPairs() {
        List<Object[]> out = new ArrayList<Object[]>();
        for (Entry e : entries.values()) {
            ItemStack s = e.sample.copy();
            s.stackSize = e.count;
            out.add(new Object[]{s, Integer.valueOf(e.count)});
        }
        return out;
    }

    /** 某种类当前数量（无则 0） */
    public int getCountOf(ItemStack stack) {
        Entry e = entries.get(key(stack));
        return e == null ? 0 : e.count;
    }

    public boolean isEmpty() { return entries.isEmpty(); }

    // ===== 写入 =====

    /**
     * 放入 amount 个 stack 类型物品（合并到同种类）。
     * 校验：种类数 < kindCap（新种类才查）；总量 + amount <= itemCap。
     * 返回实际放入数（0 = 满/超限拒绝）。
     */
    public int addItemStack(ItemStack stack, int amount, int kindCap, int itemCap) {
        if (stack == null || stack.stackSize <= 0 || amount <= 0) return 0;
        int maxKind = Math.max(1, kindCap);
        int maxItem = Math.max(1, itemCap);
        String k = key(stack);
        Entry e = entries.get(k);
        if (e == null) {
            if (entries.size() >= maxKind) return 0; // 种类满
            e = new Entry(stack.copy(), 0);
            entries.put(k, e);
        }
        int space = maxItem - totalCount;
        int put = Math.min(amount, space);
        if (put <= 0) {
            if (e.count == 0) entries.remove(k); // 刚建但放不下，撤销
            return 0;
        }
        e.count += put;
        totalCount += put;
        return put;
    }

    /**
     * 取出至多 amount 个 want 类型物品，返回实际取出的 ItemStack 列表（数量正确，堆叠按原 maxStackSize）。
     * 取完种类后自动移除空条目。
     */
    public List<ItemStack> extractItemStack(ItemStack want, int amount) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        if (want == null || amount <= 0) return out;
        Entry e = entries.get(key(want));
        if (e == null || e.count <= 0) return out;
        int take = Math.min(amount, e.count);
        int perStack = want.getMaxStackSize();
        if (perStack <= 0) perStack = 64;
        while (take > 0) {
            ItemStack s = e.sample.copy();
            s.stackSize = Math.min(take, perStack);
            take -= s.stackSize;
            out.add(s);
        }
        e.count -= Math.min(amount, e.count);
        if (e.count <= 0) entries.remove(key(want));
        recalcTotal();
        return out;
    }

    private void recalcTotal() {
        int n = 0;
        for (Entry e : entries.values()) n += e.count;
        totalCount = n;
    }

    /** 移除所有条目（灾备转移/掉落清空用），返回全部物品列表 */
    public List<ItemStack> drainAll() {
        List<ItemStack> out = new ArrayList<ItemStack>();
        for (Entry e : entries.values()) {
            if (e.count <= 0) continue;
            ItemStack s = e.sample.copy();
            s.stackSize = e.count;
            out.add(s);
        }
        entries.clear();
        totalCount = 0;
        return out;
    }

    /** 当前占用总量（用于灾备转移时容量判断） */
    public int getTotal() { return totalCount; }

    /** 清空池（不返回物品），用于退出灾备时清除冗余副本 */
    public void clear() {
        entries.clear();
        totalCount = 0;
    }

    // ===== 序列化 =====

    public void writeToNBT(NBTTagCompound tag, String keyPrefix) {
        NBTTagList list = new NBTTagList();
        for (Entry e : entries.values()) {
            if (e.count <= 0) continue;
            NBTTagCompound t = new NBTTagCompound();
            NBTTagCompound s = new NBTTagCompound();
            e.sample.writeToNBT(s);
            t.setTag("Item", s);
            t.setInteger("Count", e.count);
            list.appendTag(t);
        }
        if (list.tagCount() > 0) tag.setTag(keyPrefix + "Pool", list);
    }

    public void readFromNBT(NBTTagCompound tag, String keyPrefix) {
        entries.clear();
        totalCount = 0;
        if (!tag.hasKey(keyPrefix + "Pool")) return;
        NBTTagList list = tag.getTagList(keyPrefix + "Pool", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            ItemStack s = ItemStack.loadItemStackFromNBT(t.getCompoundTag("Item"));
            int c = t.getInteger("Count");
            if (s == null || c <= 0) continue;
            s.stackSize = Math.min(c, s.getMaxStackSize() <= 0 ? 64 : s.getMaxStackSize());
            Entry e = new Entry(s.copy(), c);
            entries.put(key(s), e);
            totalCount += c;
        }
    }

    // ===== 工具 =====

    /** 生成种类 key：id:meta:tagHash */
    public static String key(ItemStack stack) {
        int id = Item.getIdFromItem(stack.getItem());
        int meta = stack.getItemDamage();
        int tagHash = stack.hasTagCompound() ? stack.getTagCompound().hashCode() : 0;
        return id + ":" + meta + ":" + tagHash;
    }
}
