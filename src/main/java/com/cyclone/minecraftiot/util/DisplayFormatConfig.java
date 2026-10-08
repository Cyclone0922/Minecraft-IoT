package com.cyclone.minecraftiot.util;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.tileentity.TileEntity;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 显示格式配置文件（离线配置=白名单）。
 *
 * 文件位置：config/minecraftiot.json
 * 结构：按 TileEntity 全类名 为键，定义每个方块类型的显示模板。
 *   - enabled: 是否启用该模板
 *   - rows: 有序行列表，每行两种：
 *       * { "text": "自定义文本" }：字面文本行（原样输出，不加方向前缀）
 *       * { "label": "标签", "field": "字段名" }：数据行，输出 "[方向] 标签: 值"
 *         field 可以是内置字段提取器名（fuel/input/output/progress/fuelLeft/brewTime/ingredients/cooldown/items 等），
 *         也可用 "nbt.键名" 直接读 NBT 顶层键。
 *
 * 未在白名单内（或未启用）的方块，传感器回退输出原始 NBT 摘要。
 */
public class DisplayFormatConfig {
    public static class Row {
        public String label; // 数据行标签（可空）
        public String field; // 内置字段提取器名，或 "nbt.键名"
        public String text;  // 字面自定义文本行（有 text 时优先当作文本行）
        public String expr;  // 表达式行（TemplateExpr 语法）：有 expr 时按表达式求值输出，覆盖 text/field
    }

    public static class Template {
        public boolean enabled = true;
        public List<Row> rows = new ArrayList<Row>();
    }

    private static Map<String, Template> templates = new HashMap<String, Template>();
    private static File configDir; // 由 load() 记录，供 writeTemplate 写回

    /** 从 config 目录加载 minecraftiot.json；文件不存在时写入默认配置 */
    public static void load(File configDir) {
        DisplayFormatConfig.configDir = configDir;
        templates.clear();
        File f = new File(configDir, "minecraftiot.json");
        if (!f.exists()) {
            writeDefault(f);
        }
        try {
            FileReader reader = new FileReader(f);
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Template>>() {}.getType();
            Map<String, Template> map = gson.fromJson(reader, type);
            reader.close();
            if (map != null) templates = map;
            MinecraftIotMod.log.info("[DisplayFormatConfig] loaded " + templates.size() + " template(s) from " + f.getAbsolutePath());
        } catch (Exception e) {
            MinecraftIotMod.log.error("[DisplayFormatConfig] failed to load " + f.getAbsolutePath() + ", fallback to raw NBT", e);
        }
    }

    /** 查询某个 TileEntity 是否有启用中的显示模板（按类名精确/父类匹配，取最具体的模板） */
    public static Template lookup(TileEntity te) {
        if (templates.isEmpty() || te == null) return null;
        Template best = null;
        Class<?> bestClass = null;
        for (Map.Entry<String, Template> e : templates.entrySet()) {
            Template t = e.getValue();
            if (t == null || !t.enabled) continue;
            try {
                Class<?> c = Class.forName(e.getKey());
                if (c.isAssignableFrom(te.getClass())) {
                    // 选继承层级最深的（最接近实际类）模板，保证精确模板优先于基类模板
                    if (bestClass == null || bestClass.isAssignableFrom(c)) {
                        best = t;
                        bestClass = c;
                    }
                }
            } catch (ClassNotFoundException cnfe) {
                // 未知类名：跳过，继续匹配
            } catch (Exception ex) {
                // 其他异常：跳过
            }
        }
        return best;
    }

    /** 按类名写入/更新显示模板：更新内存映射并落盘（保留其余模板条目）。供传感器 GUI 的模板编辑器调用 */
    public static void writeTemplate(String className, List<Row> rows) {
        if (className == null || className.isEmpty() || configDir == null) return;
        Template t = new Template();
        t.enabled = true;
        t.rows = (rows == null ? new ArrayList<Row>() : rows);
        templates.put(className, t);
        File f = new File(configDir, "minecraftiot.json");
        try {
            f.getParentFile().mkdirs();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            FileWriter w = new FileWriter(f);
            gson.toJson(templates, w);
            w.close();
            MinecraftIotMod.log.info("[DisplayFormatConfig] wrote template for " + className + " -> " + f.getAbsolutePath());
        } catch (Exception e) {
            MinecraftIotMod.log.error("[DisplayFormatConfig] failed to write template for " + className, e);
        }
    }

    private static void writeDefault(File f) {
        try {
            f.getParentFile().mkdirs();
            FileWriter w = new FileWriter(f);
            w.write("{\n");
            w.write("  \"net.minecraft.tileentity.TileEntityFurnace\": {\n");
            w.write("    \"enabled\": true,\n");
            w.write("    \"rows\": [\n");
            w.write("      { \"label\": \"\\u71c3\\u6599\", \"field\": \"fuel\" },\n");
            w.write("      { \"label\": \"\\u5f85\\u70e7\", \"field\": \"input\" },\n");
            w.write("      { \"label\": \"\\u4ea7\\u7269\", \"field\": \"output\" },\n");
            w.write("      { \"label\": \"\\u8fdb\\u5ea6\", \"field\": \"progress\" },\n");
            w.write("      { \"label\": \"\\u71c3\\u6599\\u5269\\u4f59\", \"field\": \"fuelLeft\" }\n");
            w.write("    ]\n");
            w.write("  },\n");
            w.write("  \"net.minecraft.tileentity.TileEntityBrewingStand\": {\n");
            w.write("    \"enabled\": true,\n");
            w.write("    \"rows\": [\n");
            w.write("      { \"label\": \"\\u917f\\u9020\\u8fdb\\u5ea6\", \"field\": \"brewTime\" },\n");
            w.write("      { \"label\": \"\\u6750\\u6599\", \"field\": \"ingredients\" },\n");
            w.write("      { \"label\": \"\\u70c8\\u7130\\u7c89\", \"field\": \"fuel\" }\n");
            w.write("    ]\n");
            w.write("  },\n");
            w.write("  \"net.minecraft.tileentity.TileEntityDispenser\": {\n");
            w.write("    \"enabled\": true,\n");
            w.write("    \"rows\": [\n");
            w.write("      { \"label\": \"\\u5185\\u5bb9\", \"field\": \"items\" }\n");
            w.write("    ]\n");
            w.write("  },\n");
            w.write("  \"net.minecraft.tileentity.TileEntityHopper\": {\n");
            w.write("    \"enabled\": true,\n");
            w.write("    \"rows\": [\n");
            w.write("      { \"label\": \"\\u5185\\u5bb9\", \"field\": \"items\" },\n");
            w.write("      { \"label\": \"\\u51b7\\u5374\", \"field\": \"cooldown\" }\n");
            w.write("    ]\n");
            w.write("  }\n");
            w.write("}\n");
            w.close();
            MinecraftIotMod.log.info("[DisplayFormatConfig] wrote default config " + f.getAbsolutePath());
        } catch (Exception e) {
            MinecraftIotMod.log.error("[DisplayFormatConfig] failed to write default config", e);
        }
    }
}
