package com.cyclone.sensordisplay.util;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 信号值表达式求值器（执行器"技能0"用）。
 *
 * 与 TemplateExpr 语法兼容（数字/字符串/变量/函数/ + 拼接），新增：
 *   - true / false 字面量（BOOLEAN）
 *   - 比较  > >= < <= == !=   （结果为 BOOLEAN）
 *   - 逻辑  && || !           （结果为 BOOLEAN）
 *   - 三元  条件 ? 真值 : 假值 （保留分支类型）
 *   - 内置输入变量 {in}：求值入口携带"本面收到的信号值"，可直接引用参与运算
 *
 * 类型保留：数字字面量/算术/函数 → DOUBLE；true/false/比较/逻辑/三元 → BOOLEAN；
 *           "..." 字符串 → STRING；{路径} 按 NBT 值类型（Number→DOUBLE，String→STRING，其他→STRING）。
 *
 * 安全默认：变量缺失或解析失败返回 null（信号域中不输出、保持上次值）。
 */
public class SignalExpr {

    /** 求值值表达式；nbt 为变量来源（机器 NBT）；in 为内置 {in} 输入值（可为 null）；失败/缺失返回 null */
    public static SignalValue evaluate(String expr, NBTTagCompound nbt, SignalValue in) {
        if (expr == null || expr.trim().isEmpty()) return null;
        try {
            Parser p = new Parser(expr, nbt, in);
            return p.parseOr();
        } catch (Exception e) {
            return null;
        }
    }

    /** 求值值表达式（无 {in} 输入） */
    public static SignalValue evaluate(String expr, NBTTagCompound nbt) {
        return evaluate(expr, nbt, null);
    }

    /** 条件判断：表达式求值成功且 truthy */
    public static boolean test(String expr, NBTTagCompound nbt, SignalValue in) {
        SignalValue v = evaluate(expr, nbt, in);
        return v != null && v.truthy();
    }

    // ============ 递归下降解析器 ============

    private static final class Parser {
        private final String s;
        private final NBTTagCompound nbt;
        private final SignalValue in;
        private int pos = 0;

        Parser(String s, NBTTagCompound nbt, SignalValue in) {
            this.s = s;
            this.nbt = nbt;
            this.in = in;
        }

        private void ws() { while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++; }

        private char peek() { ws(); return pos < s.length() ? s.charAt(pos) : '\0'; }

        // ||  最低优先级
        SignalValue parseOr() {
            SignalValue left = parseAnd();
            while (true) {
                char c = peek();
                if (c == '|' && pos + 1 < s.length() && s.charAt(pos + 1) == '|') {
                    pos += 2;
                    SignalValue right = parseAnd();
                    if (left == null || right == null) return null;
                    left = SignalValue.ofBool(left.truthy() || right.truthy());
                } else break;
            }
            return left;
        }

        // &&
        SignalValue parseAnd() {
            SignalValue left = parseNot();
            while (true) {
                char c = peek();
                if (c == '&' && pos + 1 < s.length() && s.charAt(pos + 1) == '&') {
                    pos += 2;
                    SignalValue right = parseNot();
                    if (left == null || right == null) return null;
                    left = SignalValue.ofBool(left.truthy() && right.truthy());
                } else break;
            }
            return left;
        }

        // !  一元非
        SignalValue parseNot() {
            ws();
            if (pos < s.length() && s.charAt(pos) == '!') {
                pos++;
                SignalValue v = parseNot();
                if (v == null) return null;
                return SignalValue.ofBool(!v.truthy());
            }
            return parseTernary();
        }

        // ?:  三元
        SignalValue parseTernary() {
            SignalValue cond = parseCompare();
            ws();
            if (pos < s.length() && s.charAt(pos) == '?') {
                pos++;
                SignalValue trueVal = parseOr();
                ws();
                if (pos < s.length() && s.charAt(pos) == ':') {
                    pos++;
                    SignalValue falseVal = parseCompare();
                    if (cond == null || trueVal == null || falseVal == null) return null;
                    return cond.truthy() ? trueVal : falseVal;
                }
                return cond;
            }
            return cond;
        }

        // 比较 > >= < <= == !=
        SignalValue parseCompare() {
            SignalValue left = parseAdd();
            while (true) {
                char c = peek();
                if (left == null) return null;
                if (c == '>') {
                    pos++;
                    if (pos < s.length() && s.charAt(pos) == '=') {
                        pos++;
                        SignalValue r = parseAdd();
                        if (r == null) return null;
                        left = SignalValue.ofBool(compare(left, r) >= 0);
                    } else {
                        SignalValue r = parseAdd();
                        if (r == null) return null;
                        left = SignalValue.ofBool(compare(left, r) > 0);
                    }
                } else if (c == '<') {
                    pos++;
                    if (pos < s.length() && s.charAt(pos) == '=') {
                        pos++;
                        SignalValue r = parseAdd();
                        if (r == null) return null;
                        left = SignalValue.ofBool(compare(left, r) <= 0);
                    } else {
                        SignalValue r = parseAdd();
                        if (r == null) return null;
                        left = SignalValue.ofBool(compare(left, r) < 0);
                    }
                } else if (c == '=' && pos + 1 < s.length() && s.charAt(pos + 1) == '=') {
                    pos += 2;
                    SignalValue r = parseAdd();
                    if (r == null) return null;
                    left = SignalValue.ofBool(eq(left, r));
                } else if (c == '!' && pos + 1 < s.length() && s.charAt(pos + 1) == '=') {
                    pos += 2;
                    SignalValue r = parseAdd();
                    if (r == null) return null;
                    left = SignalValue.ofBool(!eq(left, r));
                } else break;
            }
            return left;
        }

        private double compare(SignalValue a, SignalValue b) {
            return a.asDouble() - b.asDouble();
        }

        private boolean eq(SignalValue a, SignalValue b) {
            if (a.type == SignalValue.TYPE_STRING || b.type == SignalValue.TYPE_STRING) {
                return a.asString().equals(b.asString());
            }
            return a.asDouble() == b.asDouble();
        }

        // + -
        SignalValue parseAdd() {
            SignalValue left = parseMul();
            while (true) {
                char c = peek();
                if (left == null) return null;
                if (c == '+') {
                    pos++;
                    SignalValue right = parseMul();
                    if (right == null) return null;
                    if (left.type == SignalValue.TYPE_STRING || right.type == SignalValue.TYPE_STRING) {
                        left = SignalValue.ofString(left.asString() + right.asString());
                    } else {
                        left = SignalValue.ofDouble(left.asDouble() + right.asDouble());
                    }
                } else if (c == '-') {
                    pos++;
                    SignalValue right = parseMul();
                    if (right == null) return null;
                    left = SignalValue.ofDouble(left.asDouble() - right.asDouble());
                } else break;
            }
            return left;
        }

        // * /
        SignalValue parseMul() {
            SignalValue left = parseUnary();
            while (true) {
                char c = peek();
                if (left == null) return null;
                if (c == '*') {
                    pos++;
                    SignalValue right = parseUnary();
                    if (right == null) return null;
                    left = SignalValue.ofDouble(left.asDouble() * right.asDouble());
                } else if (c == '/') {
                    pos++;
                    SignalValue right = parseUnary();
                    if (right == null) return null;
                    double d = right.asDouble();
                    left = SignalValue.ofDouble(d == 0 ? 0 : left.asDouble() / d);
                } else break;
            }
            return left;
        }

        // 一元负号
        SignalValue parseUnary() {
            ws();
            if (pos < s.length() && s.charAt(pos) == '-') {
                pos++;
                SignalValue v = parsePrimary();
                if (v == null) return null;
                return SignalValue.ofDouble(-v.asDouble());
            }
            return parsePrimary();
        }

        // 原子：括号、变量、字符串、数字、函数/裸名/字面量
        SignalValue parsePrimary() {
            ws();
            if (pos >= s.length()) return null;
            char c = s.charAt(pos);
            if (c == '(') {
                pos++;
                SignalValue v = parseOr();
                expect(')');
                return v;
            }
            if (c == '{') return parseVariable();
            if (c == '"') return parseString();
            if (Character.isDigit(c)) return parseNumber();
            if (Character.isLetter(c)) return parseFunctionOrBare();
            pos++;
            return null;
        }

        private void expect(char c) { ws(); if (pos < s.length() && s.charAt(pos) == c) pos++; }

        private SignalValue parseString() {
            pos++; // 跳过 "
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == '"') { pos++; return SignalValue.ofString(sb.toString()); }
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
            return SignalValue.ofString(sb.toString());
        }

        private SignalValue parseVariable() {
            pos++; // {
            StringBuilder sb = new StringBuilder();
            while (pos < s.length() && s.charAt(pos) != '}') { sb.append(s.charAt(pos)); pos++; }
            pos++; // }
            return resolve(sb.toString().trim());
        }

        private SignalValue parseNumber() {
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (Character.isDigit(c) || c == '.' || c == 'e' || c == 'E'
                        || ((c == '+' || c == '-') && sb.length() > 0
                        && (sb.charAt(sb.length() - 1) == 'e' || sb.charAt(sb.length() - 1) == 'E'))) {
                    sb.append(c); pos++;
                } else break;
            }
            try { return SignalValue.ofDouble(Double.parseDouble(sb.toString())); }
            catch (NumberFormatException e) { return null; }
        }

        /** 函数调用、裸变量名、true/false 字面量 */
        private SignalValue parseFunctionOrBare() {
            StringBuilder sb = new StringBuilder();
            while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_')) {
                sb.append(s.charAt(pos)); pos++;
            }
            ws();
            String name = sb.toString();
            if (pos < s.length() && s.charAt(pos) == '(') {
                pos++;
                java.util.List<SignalValue> args = new java.util.ArrayList<SignalValue>();
                if (peek() == ')') { pos++; }
                else {
                    args.add(parseOr());
                    while (peek() == ',') { pos++; args.add(parseOr()); }
                    expect(')');
                }
                return apply(name, args);
            }
            if (name.equals("true")) return SignalValue.ofBool(true);
            if (name.equals("false")) return SignalValue.ofBool(false);
            return resolve(name);
        }

        /** 变量解析：{in} → 输入值；其他路径 → NBT 值（Number→DOUBLE，Boolean→BOOLEAN，String→STRING） */
        private SignalValue resolve(String path) {
            if (path == null || path.isEmpty()) return null;
            if (path.equals("in")) return in;
            if (nbt == null) return null;
            Object v = NbtVariableUtil.resolveValue(nbt, path);
            if (v == null) return null;
            if (v instanceof Boolean) return SignalValue.ofBool((Boolean) v);
            if (v instanceof Number) return SignalValue.ofDouble(((Number) v).doubleValue());
            return SignalValue.ofString(v.toString());
        }

        private SignalValue apply(String name, java.util.List<SignalValue> args) {
            if (name.equals("percent") && args.size() == 2) {
                if (args.get(0) == null || args.get(1) == null) return null;
                double b = args.get(1).asDouble();
                return SignalValue.ofDouble(b == 0 ? 0 : args.get(0).asDouble() / b * 100);
            }
            if (name.equals("div") && args.size() == 2) {
                if (args.get(0) == null || args.get(1) == null) return null;
                double b = args.get(1).asDouble();
                return SignalValue.ofDouble(b == 0 ? 0 : args.get(0).asDouble() / b);
            }
            if (name.equals("round") && args.size() == 1 && args.get(0) != null) {
                return SignalValue.ofDouble(Math.round(args.get(0).asDouble()));
            }
            if (name.equals("floor") && args.size() == 1 && args.get(0) != null) {
                return SignalValue.ofDouble(Math.floor(args.get(0).asDouble()));
            }
            if (name.equals("ceil") && args.size() == 1 && args.get(0) != null) {
                return SignalValue.ofDouble(Math.ceil(args.get(0).asDouble()));
            }
            if (name.equals("abs") && args.size() == 1 && args.get(0) != null) {
                return SignalValue.ofDouble(Math.abs(args.get(0).asDouble()));
            }
            if (name.equals("min") && args.size() == 2 && args.get(0) != null && args.get(1) != null) {
                return SignalValue.ofDouble(Math.min(args.get(0).asDouble(), args.get(1).asDouble()));
            }
            if (name.equals("max") && args.size() == 2 && args.get(0) != null && args.get(1) != null) {
                return SignalValue.ofDouble(Math.max(args.get(0).asDouble(), args.get(1).asDouble()));
            }
            return null;
        }
    }
}
