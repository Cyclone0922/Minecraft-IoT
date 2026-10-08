package com.cyclone.sensordisplay.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据显示模板把一个 TileEntity 格式化成多行友好文本。
 * 方向信息只在块首行出现一次（"[方向] 标签: 值"），后续行仅 "标签: 值"，
 * 字面自定义文本行原样输出。
 */
public class DisplayFormatter {

    public static List<String> format(TileEntity te, ForgeDirection dir, DisplayFormatConfig.Template tpl) {
        return format(te, dir, tpl, null);
    }

    /**
     * @param serverNbt 服务端已下发的原始 NBT（已注入 IC2 合成键 EUIn/EUOut/Voltage 等运行时值）。
     *                  null 时退回 te.writeToNBT + 锅炉虚拟变量。
     *                  表达式/var 路径解析都基于该 NBT，因此运行时合成键可被引用。
     */
    public static List<String> format(TileEntity te, ForgeDirection dir, DisplayFormatConfig.Template tpl, NBTTagCompound serverNbt) {
        List<String> out = new ArrayList<String>();
        if (tpl == null || tpl.rows == null) return out;
        NBTTagCompound nbt = serverNbt != null ? serverNbt : new NBTTagCompound();
        if (serverNbt == null) te.writeToNBT(nbt);
        // Railcraft 锅炉：注入聚合的虚拟变量（温度/水/蒸汽），使 var.Temperature 等路径可解析
        BoilerAggregator.injectVirtualData(te, nbt);

        for (DisplayFormatConfig.Row row : tpl.rows) {
            if (row == null) continue;
            // 表达式行：TemplateExpr 语法，求值结果可能含 \n（多行）
            if (row.expr != null && !row.expr.isEmpty()) {
                out.add(TemplateExpr.evaluate(row.expr, nbt));
                continue;
            }
            if (row.text != null && !row.text.isEmpty()) {
                out.add(row.text); // 字面自定义文本行
                continue;
            }
            if (row.field == null || row.field.isEmpty()) continue;
            String value = resolveField(te, nbt, row.field);
            if (value == null) continue; // 该字段当前无数据，跳过此行
            StringBuilder line = new StringBuilder();
            if (row.label != null && !row.label.isEmpty()) line.append(row.label).append(": ");
            line.append(value);
            out.add(line.toString());
        }
        // 方向只在块首行出现一次
        if (!out.isEmpty()) {
            out.set(0, "[" + dir.name() + "] " + out.get(0));
        }
        return out;
    }

    /** 按字段名解析出值；无法解析或当前无数据时返回 null */
    private static String resolveField(TileEntity te, NBTTagCompound nbt, String field) {
        ItemStack[] items = itemsBySlot(nbt);

        if (te instanceof TileEntityFurnace) {
            if (field.equals("input")) return stackStr(items, 0);
            if (field.equals("fuel")) return stackStr(items, 1);
            if (field.equals("output")) return stackStr(items, 2);
            if (field.equals("progress")) {
                int cook = nbt.getInteger("CookTime");
                int burn = nbt.getInteger("BurnTime");
                if (cook > 0) return Math.min(100, cook * 100 / 200) + "%";
                return (burn > 0) ? "\u9884\u70ed\u4e2d 0%" : "\u7a7a\u95f2";
            }
            if (field.equals("fuelLeft")) {
                int burn = nbt.getInteger("BurnTime");
                ItemStack fuel = stackAt(items, 1);
                if (fuel == null || burn <= 0) return "\u2014";
                int total = TileEntityFurnace.getItemBurnTime(fuel);
                if (total <= 0) return "\u2014";
                return Math.min(100, burn * 100 / total) + "%";
            }
        }

        if (te instanceof TileEntityBrewingStand) {
            if (field.equals("brewTime")) {
                int t = nbt.getInteger("BrewTime");
                return (t <= 0) ? "\u7a7a\u95f2" : (t / 20) + "s";
            }
            if (field.equals("ingredients")) return listStr(items, 0, 3);
            if (field.equals("fuel")) return stackStr(items, 4);
        }

        if (te instanceof TileEntityHopper) {
            if (field.equals("items")) return listStr(items, 0, items.length - 1);
            if (field.equals("cooldown")) {
                int cd = nbt.getInteger("TransferCooldown");
                return (cd <= 0) ? "\u5c31\u7eea" : (cd / 20) + "s";
            }
        }

        // 通用：items 字段列出全部非空格；否则当作顶层 NBT 键读取
        if (field.equals("items")) return listStr(items, 0, items.length - 1);

        // ============ CoFH/ThermalExpansion 通用提取器（Energy/Active/ProcMax/ProcRem/SlotN） ============
        if (field.equals("energy")) {
            if (nbt.hasKey("Energy")) return nbt.getInteger("Energy") + " RF";
            return null;
        }
        if (field.equals("active")) {
            if (!nbt.hasKey("Active")) return null;
            return nbt.getBoolean("Active") ? "\u8fd0\u884c\u4e2d" : "\u505c\u6b62";
        }
        if (field.equals("teProgress")) {
            if (!nbt.hasKey("ProcMax") || !nbt.hasKey("ProcRem")) return null;
            int max = nbt.getInteger("ProcMax");
            int rem = nbt.getInteger("ProcRem");
            if (max <= 0) return "\u7a7a\u95f2";
            if (rem > 0) return Math.max(0, Math.min(100, (max - rem) * 100 / max)) + "%";
            return "100%";
        }
        if (field.startsWith("slot:")) {
            String sub = field.substring(5).trim();
            if (sub.isEmpty()) return null;
            // 支持 "slot:0" 单槽位，或 "slot:0,1,2" 逗号列表（多个输入/输出位合并到一行）
            StringBuilder sb = new StringBuilder();
            boolean any = false;
            for (String p : sub.split(",")) {
                int s;
                try { s = Integer.parseInt(p.trim()); } catch (NumberFormatException nfe) { continue; }
                ItemStack st = stackAt(items, s);
                if (st == null) continue;
                if (any) sb.append(", ");
                sb.append(itemName(st)).append(" \u00d7").append(st.stackSize);
                any = true;
            }
            return any ? sb.toString() : "\u7a7a";
        }

        // ============ IC2 (工业2实验版) 提取器 ============
        // IC2 机器 NBT：energy(double), active(byte), progress(short), InvSlots:{槽名:{Contents:[{Index,id,Count,Damage}]}}
        // 物品 id 是数字 short，与原版 String id 不同，需用 Item.getItemById 还原
        if (field.equals("ic2Energy")) {
            if (nbt.hasKey("energy")) return (long) nbt.getDouble("energy") + " EU";
            return null;
        }
        if (field.equals("ic2Storage")) {
            if (nbt.hasKey("storage")) return (long) nbt.getDouble("storage") + " EU";
            return null;
        }
        if (field.equals("ic2Power")) {
            // 发电机当前输出（EU/t）：基类有 public double power 字段，反射读取
            try {
                java.lang.reflect.Field f = te.getClass().getField("power");
                Object v = f.get(te);
                if (v instanceof Number) return ((Number) v).doubleValue() + " EU/t";
            } catch (Exception ignore) { }
            return null;
        }
        if (field.equals("ic2Fuel")) {
            if (!nbt.hasKey("fuel")) return null;
            int f = nbt.getShort("fuel");
            if (f <= 0) return "\u7a7a";
            return f + " tick";
        }
        if (field.equals("ic2Active")) {
            if (!nbt.hasKey("active")) return null;
            return nbt.getBoolean("active") ? "\u8fd0\u884c\u4e2d" : "\u505c\u6b62";
        }
        if (field.equals("ic2Progress")) {
            // 优先反射 getProgress()（返回 0..1 浮点，含超频/降频），失败退回原始 progress
            try {
                java.lang.reflect.Method m = te.getClass().getMethod("getProgress");
                Object r = m.invoke(te);
                if (r instanceof Number) {
                    float f = ((Number) r).floatValue();
                    return Math.max(0, Math.min(100, (int) (f * 100))) + "%";
                }
            } catch (Exception ignore) { }
            if (nbt.hasKey("progress")) return nbt.getShort("progress") + " tick";
            return null;
        }
        if (field.startsWith("ic2Slot:")) {
            String slot = field.substring(8).trim();
            if (slot.isEmpty() || !nbt.hasKey("InvSlots")) return null;
            NBTTagCompound invSlots = nbt.getCompoundTag("InvSlots");
            if (!invSlots.hasKey(slot)) return null;
            NBTTagCompound slotTag = invSlots.getCompoundTag(slot);
            if (!slotTag.hasKey("Contents")) return null;
            NBTTagList contents = slotTag.getTagList("Contents", 10);
            StringBuilder sb = new StringBuilder();
            boolean any = false;
            for (int i = 0; i < contents.tagCount(); i++) {
                ItemStack st = ic2Item(contents.getCompoundTagAt(i));
                if (st == null) continue;
                if (any) sb.append(", ");
                sb.append(itemName(st)).append(" \u00d7").append(st.stackSize);
                any = true;
            }
            return any ? sb.toString() : "\u7a7a";
        }

        // 通用路径提取器：var.<path>，path 为 NbtVariableUtil 的 NBT 遍历路径（如 var.InvSlots.input.Contents）
        if (field.startsWith("var.")) {
            String p = field.substring(4);
            String v = NbtVariableUtil.resolvePath(nbt, p);
            if (v == null || v.isEmpty()) return null;
            return v;
        }

        if (field.startsWith("nbt.")) {
            String key = field.substring(4);
            if (nbt.hasKey(key)) {
                return String.valueOf(nbt.getTag(key));
            }
        }
        return null;
    }

    // ============ 辅助 ============

    private static ItemStack stackAt(ItemStack[] items, int slot) {
        if (items == null || slot >= items.length) return null;
        return items[slot];
    }

    private static String stackStr(ItemStack[] items, int slot) {
        ItemStack st = stackAt(items, slot);
        if (st == null) return "\u7a7a";
        return itemName(st) + " \u00d7" + st.stackSize;
    }

    private static String listStr(ItemStack[] items, int from, int to) {
        if (items == null) return "\u7a7a";
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (int i = from; i <= to && i < items.length; i++) {
            ItemStack st = items[i];
            if (st == null) continue;
            if (shown >= 4) break; // 最多列 4 个，避免超长
            if (sb.length() > 0) sb.append(", ");
            sb.append(itemName(st)).append(" \u00d7").append(st.stackSize);
            shown++;
        }
        int total = 0;
        for (int i = from; i <= to && i < items.length; i++) {
            if (items[i] != null) total++;
        }
        if (total > shown) sb.append(" +").append(total - shown).append("\u4ef6");
        return sb.length() == 0 ? "\u7a7a" : sb.toString();
    }

    private static String itemName(ItemStack st) {
        try {
            String name = st.getDisplayName();
            return (name == null || name.isEmpty()) ? st.getItem().getUnlocalizedName() : name;
        } catch (Exception e) {
            return st.getItem() == null ? "?" : st.getItem().getUnlocalizedName();
        }
    }

    /** IC2 物品条目：id(short)=数字方块/物品 id，Damage(short)、Count(byte)，用 Item.getItemById 还原 */
    private static ItemStack ic2Item(NBTTagCompound c) {
        if (c == null || !c.hasKey("id")) return null;
        short id = c.getShort("id");
        short damage = c.getShort("Damage");
        byte count = c.getByte("Count");
        Item item = Item.getItemById(id);
        if (item == null) return null;
        return new ItemStack(item, count, damage);
    }

    /** 从 NBT 的物品列表按槽位还原出 ItemStack 数组（兼容原版 "Items" 与 TE/CoFH 的 "Inventory" 两种列表键） */
    private static ItemStack[] itemsBySlot(NBTTagCompound nbt) {
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        String listKey = nbt.hasKey("Items") ? "Items" : (nbt.hasKey("Inventory") ? "Inventory" : null);
        if (listKey != null) {
            NBTTagList list = nbt.getTagList(listKey, 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound c = list.getCompoundTagAt(i);
                int slot = c.getInteger("Slot");
                ItemStack st = ItemStack.loadItemStackFromNBT(c);
                if (st == null) continue;
                while (stacks.size() <= slot) stacks.add(null);
                stacks.set(slot, st);
            }
        }
        return stacks.toArray(new ItemStack[stacks.size()]);
    }
}
