package com.cyclone.minecraftiot.util;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 布尔表达式求值器（执行器"执行条件"用）。
 *
 * 语法：
 *   变量        {路径}   从 NBT 取值（缺失视为 0）
 *   数字        123  12.5
 *   算术        + - * / ( )
 *   比较        > >= < <= == !=
 *   逻辑        && || !
 *   三元        条件 ? 真值 : 假值
 *   函数        percent(a,b) round(a) floor(a) ceil(a) abs(a) min(a,b) max(a,b)
 *
 * 求值结果为 boolean：非零数字为 true，零为 false。
 * 解析失败返回 false（安全默认：条件不成立则不执行）。
 *
 * 示例：
 *   {BurnTime} > 0
 *   {Water} / {WaterCapacity} * 100 < 20
 *   {EUIn} > 0 && {Temperature} > 300
 */
public class ExprBoolean {

    /** 求值布尔表达式；变量从 nbt 取；解析失败返回 false */
    public static boolean evaluate(String expr, NBTTagCompound nbt) {
        if (expr == null || expr.trim().isEmpty()) return false;
        try {
            Parser p = new Parser(expr, nbt);
            double v = p.parseOr();
            return v != 0 && !Double.isNaN(v);
        } catch (Exception e) {
            return false;
        }
    }

    // ============ 递归下降解析器 ============

    private static class Parser {
        private final String s;
        private final NBTTagCompound nbt;
        private int pos = 0;

        Parser(String s, NBTTagCompound nbt) { this.s = s; this.nbt = nbt; }

        private void ws() { while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++; }
        private char peek() { ws(); return pos < s.length() ? s.charAt(pos) : '\0'; }

        // ||  最低优先级
        double parseOr() {
            double left = parseAnd();
            while (true) {
                char c = peek();
                if (c == '|' && pos + 1 < s.length() && s.charAt(pos + 1) == '|') {
                    pos += 2;
                    double right = parseAnd();
                    left = (left != 0 || right != 0) ? 1 : 0;
                } else break;
            }
            return left;
        }

        // &&
        double parseAnd() {
            double left = parseNot();
            while (true) {
                char c = peek();
                if (c == '&' && pos + 1 < s.length() && s.charAt(pos + 1) == '&') {
                    pos += 2;
                    double right = parseNot();
                    left = (left != 0 && right != 0) ? 1 : 0;
                } else break;
            }
            return left;
        }

        // !  一元非
        double parseNot() {
            ws();
            if (pos < s.length() && s.charAt(pos) == '!') {
                pos++;
                double v = parseNot();
                return v == 0 ? 1 : 0;
            }
            return parseTernary();
        }

        // ?:  三元
        double parseTernary() {
            double cond = parseCompare();
            ws();
            if (pos < s.length() && s.charAt(pos) == '?') {
                pos++;
                double trueVal = parseOr();
                ws();
                if (pos < s.length() && s.charAt(pos) == ':') {
                    pos++;
                    double falseVal = parseCompare();
                    return cond != 0 ? trueVal : falseVal;
                }
                return cond != 0 ? trueVal : 0;
            }
            return cond;
        }

        // 比较 > >= < <= == !=
        double parseCompare() {
            double left = parseAdd();
            while (true) {
                char c = peek();
                if (c == '>' ) {
                    pos++;
                    if (pos < s.length() && s.charAt(pos) == '=') { pos++; left = left >= parseAdd() ? 1 : 0; }
                    else left = left > parseAdd() ? 1 : 0;
                } else if (c == '<') {
                    pos++;
                    if (pos < s.length() && s.charAt(pos) == '=') { pos++; left = left <= parseAdd() ? 1 : 0; }
                    else left = left < parseAdd() ? 1 : 0;
                } else if (c == '=' && pos + 1 < s.length() && s.charAt(pos + 1) == '=') {
                    pos += 2; left = left == parseAdd() ? 1 : 0;
                } else if (c == '!' && pos + 1 < s.length() && s.charAt(pos + 1) == '=') {
                    pos += 2; left = left != parseAdd() ? 1 : 0;
                } else break;
            }
            return left;
        }

        // + -
        double parseAdd() {
            double left = parseMul();
            while (true) {
                char c = peek();
                if (c == '+') { pos++; left = left + parseMul(); }
                else if (c == '-') { pos++; left = left - parseMul(); }
                else break;
            }
            return left;
        }

        // * /
        double parseMul() {
            double left = parseUnary();
            while (true) {
                char c = peek();
                if (c == '*') { pos++; left = left * parseUnary(); }
                else if (c == '/') { pos++; double d = parseUnary(); left = d == 0 ? 0 : left / d; }
                else break;
            }
            return left;
        }

        // 一元负号
        double parseUnary() {
            ws();
            if (pos < s.length() && s.charAt(pos) == '-') {
                pos++;
                return -parsePrimary();
            }
            return parsePrimary();
        }

        // 原子：括号、数字、变量、函数
        double parsePrimary() {
            ws();
            if (pos >= s.length()) return 0;
            char c = s.charAt(pos);
            if (c == '(') { pos++; double v = parseOr(); expect(')'); return v; }
            if (c == '{') return parseVariable();
            if (Character.isDigit(c)) return parseNumber();
            if (Character.isLetter(c)) return parseFunctionOrBare();
            pos++;
            return 0;
        }

        private void expect(char c) { ws(); if (pos < s.length() && s.charAt(pos) == c) pos++; }

        private double parseVariable() {
            pos++; // {
            StringBuilder sb = new StringBuilder();
            while (pos < s.length() && s.charAt(pos) != '}') { sb.append(s.charAt(pos)); pos++; }
            pos++; // }
            Object v = NbtVariableUtil.resolveValue(nbt, sb.toString().trim());
            return toDouble(v);
        }

        private double parseNumber() {
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (Character.isDigit(c) || c == '.' || c == 'e' || c == 'E'
                        || ((c == '+' || c == '-') && sb.length() > 0
                        && (sb.charAt(sb.length() - 1) == 'e' || sb.charAt(sb.length() - 1) == 'E'))) {
                    sb.append(c); pos++;
                } else break;
            }
            try { return Double.parseDouble(sb.toString()); } catch (Exception e) { return 0; }
        }

        private double parseFunctionOrBare() {
            StringBuilder sb = new StringBuilder();
            while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_')) {
                sb.append(s.charAt(pos)); pos++;
            }
            ws();
            if (pos < s.length() && s.charAt(pos) == '(') {
                pos++;
                String name = sb.toString();
                java.util.List<Double> args = new java.util.ArrayList<Double>();
                if (peek() == ')') { pos++; }
                else {
                    args.add(parseOr());
                    while (peek() == ',') { pos++; args.add(parseOr()); }
                    expect(')');
                }
                return apply(name, args);
            }
            // 裸变量名
            Object v = NbtVariableUtil.resolveValue(nbt, sb.toString());
            return toDouble(v);
        }

        private double apply(String name, java.util.List<Double> args) {
            if (name.equals("percent") && args.size() == 2) {
                double b = args.get(1); return b == 0 ? 0 : args.get(0) / b * 100;
            }
            if (name.equals("round") && args.size() == 1) return Math.round(args.get(0));
            if (name.equals("floor") && args.size() == 1) return Math.floor(args.get(0));
            if (name.equals("ceil") && args.size() == 1) return Math.ceil(args.get(0));
            if (name.equals("abs") && args.size() == 1) return Math.abs(args.get(0));
            if (name.equals("min") && args.size() == 2) return Math.min(args.get(0), args.get(1));
            if (name.equals("max") && args.size() == 2) return Math.max(args.get(0), args.get(1));
            return 0;
        }

        private static double toDouble(Object v) {
            if (v == null) return 0;
            if (v instanceof Number) return ((Number) v).doubleValue();
            String s = v.toString().trim();
            if (s.isEmpty()) return 0;
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
        }
    }
}
