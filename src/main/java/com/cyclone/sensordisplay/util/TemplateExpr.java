package com.cyclone.sensordisplay.util;

import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.List;

/**
 * 显示表达式引擎：让模板行直接书写自定义输出表达式，替代"重命名变量"。
 *
 * 语法（一行一个表达式，求值为字符串）：
 *   - 字符串字面量  "..."   支持转义 \n(换行) \t \\ \"
 *   - 变量插值      {路径}   路径为 NBT 路径（顶层键 或 a.b.c 嵌套）；缺失时替换为空串
 *   - 数字          123  12.5
 *   - 运算符        + - * / ( )   （+ 遇字符串为拼接，两数字为加法）
 *   - 函数          percent(a,b) round(a) floor(a) ceil(a) div(a,b) abs(a) min(a,b) max(a,b)
 *
 * 示例：
 *   "当前温度：" + {Temperature} + "\n当前水量：" + {Water} + " (" + ({Water}/{WaterCapacity}*100) + "%)"
 *   求值结果（两行）：
 *   当前温度：427
 *   当前水量：27477 (21.5%)
 */
public class TemplateExpr {

    /** 求值一行表达式；变量缺失替换为空串；返回求值后的字符串 */
    public static String evaluate(String expr, NBTTagCompound nbt) {
        if (expr == null) return "";
        try {
            Parser p = new Parser(expr, nbt);
            return valueToString(p.parseExpr());
        } catch (Exception e) {
            return expr; // 解析失败：原样输出，便于排查
        }
    }

    private static boolean isNum(Object v) { return v instanceof Double; }

    private static double num(Object v) {
        if (v instanceof Double) return (Double) v;
        String s = v == null ? "" : v.toString().trim();
        if (s.isEmpty()) return 0;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return Double.NaN; }
    }

    private static String valueToString(Object v) {
        if (v instanceof Double) return fmtNum((Double) v);
        return v == null ? "" : v.toString();
    }

    /** 数值格式化：整数不带小数点；小数最多 3 位、去尾零；NaN/无穷显示 — */
    private static String fmtNum(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "\u2014";
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        long r = Math.round(v * 1000);
        String s = String.valueOf(r / 1000.0);
        while (s.indexOf('.') >= 0 && (s.endsWith("0") || s.endsWith("."))) {
            s = s.endsWith(".") ? s.substring(0, s.length() - 1) : s.substring(0, s.length() - 1);
        }
        return s;
    }

    // ============ 递归下降解析器 ============

    private static class Parser {
        private final String s;
        private final NBTTagCompound nbt;
        private int pos = 0;

        Parser(String s, NBTTagCompound nbt) { this.s = s; this.nbt = nbt; }

        private void ws() { while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++; }

        private char peek() { ws(); return pos < s.length() ? s.charAt(pos) : '\0'; }

        Object parseExpr() {
            Object left = parseTerm();
            while (true) {
                char c = peek();
                if (c == '+') { pos++; left = plus(left, parseTerm()); }
                else if (c == '-') { pos++; left = minus(left, parseTerm()); }
                else break;
            }
            return left;
        }

        Object parseTerm() {
            Object left = parseFactor();
            while (true) {
                char c = peek();
                if (c == '*') { pos++; left = mul(left, parseFactor()); }
                else if (c == '/') { pos++; left = div(left, parseFactor()); }
                else break;
            }
            return left;
        }

        Object parseFactor() {
            ws();
            if (pos >= s.length()) return "";
            char c = s.charAt(pos);
            if (c == '(') { pos++; Object v = parseExpr(); expect(')'); return v; }
            if (c == '-') { pos++; Object v = parseFactor(); return isNum(v) ? -num(v) : v; }
            if (c == '"') return parseString();
            if (c == '{') return parseVariable();
            if (Character.isDigit(c)) return parseNumber();
            if (Character.isLetter(c)) return parseFunctionOrBare();
            pos++; // 跳过无法识别的字符
            return "";
        }

        private void expect(char c) { ws(); if (pos < s.length() && s.charAt(pos) == c) pos++; }

        private String parseString() {
            pos++; // 跳过 "
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == '"') { pos++; return sb.toString(); }
                if (c == '\\') {
                    pos++;
                    if (pos >= s.length()) break;
                    char e = s.charAt(pos); pos++;
                    switch (e) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case '\\': sb.append('\\'); break;
                        case '"': sb.append('"'); break;
                        default: sb.append(e);
                    }
                    continue;
                }
                sb.append(c); pos++;
            }
            return sb.toString();
        }

        private Object parseVariable() {
            pos++; // 跳过 {
            StringBuilder sb = new StringBuilder();
            while (pos < s.length() && s.charAt(pos) != '}') { sb.append(s.charAt(pos)); pos++; }
            pos++; // 跳过 }
            String path = sb.toString().trim();
            Object v = NbtVariableUtil.resolveValue(nbt, path);
            return v == null ? "" : v;
        }

        private Object parseNumber() {
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (Character.isDigit(c) || c == '.' || c == 'e' || c == 'E'
                        || ((c == '+' || c == '-') && sb.length() > 0
                        && (sb.charAt(sb.length() - 1) == 'e' || sb.charAt(sb.length() - 1) == 'E'))) {
                    sb.append(c); pos++;
                } else break;
            }
            try { return Double.parseDouble(sb.toString()); } catch (Exception e) { return ""; }
        }

        /** 函数调用或裸变量名（不带花括号的键） */
        private Object parseFunctionOrBare() {
            StringBuilder sb = new StringBuilder();
            while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_')) {
                sb.append(s.charAt(pos)); pos++;
            }
            ws();
            if (pos < s.length() && s.charAt(pos) == '(') {
                pos++;
                String name = sb.toString();
                List<Object> args = new ArrayList<Object>();
                if (peek() == ')') { pos++; }
                else {
                    args.add(parseExpr());
                    while (peek() == ',') { pos++; args.add(parseExpr()); }
                    expect(')');
                }
                return apply(name, args);
            }
            Object v = NbtVariableUtil.resolveValue(nbt, sb.toString());
            return v == null ? "" : v;
        }

        private Object apply(String name, List<Object> args) {
            if (name.equals("percent") && args.size() == 2) {
                double b = num(args.get(1)); return b == 0 ? 0d : num(args.get(0)) / b * 100.0;
            }
            if (name.equals("div") && args.size() == 2) {
                double b = num(args.get(1)); return b == 0 ? 0d : num(args.get(0)) / b;
            }
            if (name.equals("round") && args.size() == 1) return (double) Math.round(num(args.get(0)));
            if (name.equals("floor") && args.size() == 1) return Math.floor(num(args.get(0)));
            if (name.equals("ceil") && args.size() == 1) return Math.ceil(num(args.get(0)));
            if (name.equals("abs") && args.size() == 1) return Math.abs(num(args.get(0)));
            if (name.equals("min") && args.size() == 2) return Math.min(num(args.get(0)), num(args.get(1)));
            if (name.equals("max") && args.size() == 2) return Math.max(num(args.get(0)), num(args.get(1)));
            return "";
        }

        // ===== 运算 =====
        private Object plus(Object a, Object b) {
            if (isNum(a) && isNum(b)) return num(a) + num(b);
            return valueToString(a) + valueToString(b);
        }
        private Object minus(Object a, Object b) { return num(a) - num(b); }
        private Object mul(Object a, Object b) { return num(a) * num(b); }
        private Object div(Object a, Object b) { double d = num(b); return d == 0 ? 0d : num(a) / d; }
    }
}
