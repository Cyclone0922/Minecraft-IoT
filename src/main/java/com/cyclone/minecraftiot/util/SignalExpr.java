package com.cyclone.minecraftiot.util;

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
            Parser p = new Parser(expr, nbt, new SignalValue[]{in}, 0);
            return p.parseOr();
        } catch (Exception e) {
            return null;
        }
    }

    /** 求值值表达式（多面输入）；ins 为 6 面输入数组（可为 null 元素）；selfSide 为当前编辑面 */
    public static SignalValue evaluate(String expr, NBTTagCompound nbt, SignalValue[] ins, int selfSide) {
        if (expr == null || expr.trim().isEmpty()) return null;
        try {
            Parser p = new Parser(expr, nbt, ins, selfSide);
            return p.parseOr();
        } catch (Exception e) {
            return null;
        }
    }

    /** 求值值表达式（无 {in} 输入） */
    public static SignalValue evaluate(String expr, NBTTagCompound nbt) {
        return evaluate(expr, nbt, null);
    }

    /**
     * 语法校验（保存前用）：只做词法/结构检查，不依赖 NBT 或输入值求值，
     * 因此不会因变量缺失而误判（如 "{inW} > 600 && {inN} > 800" 在无输入时求值为 null，但语法合法）。
     * 空/纯空白视为合法（表示清空配置）。
     * 规则：允许 token 为 {路径}、"字符串"、数字、标识符、运算符 + - * / > >= < <= == != && || ! ? : ( ) ,；
     * 括号与引号必须闭合；== 必须成对、&& / || 必须成对；不得出现非法字符。
     */
    public static boolean isValid(String expr) {
        if (expr == null) return true;
        String s = expr.trim();
        if (s.isEmpty()) return true;
        int paren = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) continue;
            if (c == '{') { // 变量 {路径}，允许字母数字 _ .
                int j = s.indexOf('}', i + 1);
                if (j < 0) return false;
                String inner = s.substring(i + 1, j);
                if (inner.isEmpty()) return false;
                for (int k = 0; k < inner.length(); k++) {
                    char ic = inner.charAt(k);
                    if (!(Character.isLetterOrDigit(ic) || ic == '_' || ic == '.')) return false;
                }
                i = j;
                continue;
            }
            if (c == '"') { // 字符串，需闭合，支持 \ 转义
                int j = i + 1;
                boolean closed = false;
                while (j < s.length()) {
                    char q = s.charAt(j);
                    if (q == '\\') { j += 2; continue; }
                    if (q == '"') { closed = true; j++; break; }
                    j++;
                }
                if (!closed) return false;
                i = j - 1;
                continue;
            }
            if (c == '(') { paren++; continue; }
            if (c == ')') { paren--; if (paren < 0) return false; continue; }
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.') continue;
            if (c == '+' || c == '-' || c == '*' || c == '/') continue;
            if (c == '&' || c == '|') { // && || 必须成对
                if (i + 1 >= s.length() || s.charAt(i + 1) != c) return false;
                i++;
                continue;
            }
            if (c == '=') { // == 必须成对
                if (i + 1 >= s.length() || s.charAt(i + 1) != '=') return false;
                i++;
                continue;
            }
            if (c == '>' || c == '<' || c == '!') { // 允许单独或跟 '='（>= <= !=；! 单目）
                if (i + 1 < s.length() && s.charAt(i + 1) == '=') i++;
                continue;
            }
            if (c == '?' || c == ':') continue;
            if (c == ',') continue;
            return false; // 其他字符非法
        }
        return paren == 0;
    }

    /**
     * 表达式空格标准化（保存时调用）：
     * 去掉所有多余空白（空格/TAB/换行），按 token 重建——
     * 变量/数字/字符串/标识符与运算符之间统一加一个空格；
     * 例外：右括号与逗号紧贴左侧、左括号紧贴右侧、函数名与左括号之间不加空格。
     * 字符串内部内容（含空格与 \n 转义）原样保留，不影响显示文本与换行功能。
     * 遇到无法识别的结构时原样返回（不破坏表达式）。
     */
    public static String normalize(String expr) {
        if (expr == null) return null;
        String s = expr.trim();
        if (s.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        boolean first = true;
        boolean prevIdent = false;      // 上一个 token 是标识符（函数名/true/false）
        boolean prevOpenParen = false;  // 上一个 token 是左括号
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            String tok;
            int kind; // 0=词 1=运算符 2=括号 3=逗号
            boolean ident = false;
            if (c == '{') { // 变量 {路径}
                int j = s.indexOf('}', i + 1);
                if (j < 0) return expr;
                tok = s.substring(i, j + 1);
                kind = 0;
                i = j + 1;
            } else if (c == '"') { // 字符串（含转义，内部原样保留）
                int j = i + 1;
                boolean closed = false;
                while (j < s.length()) {
                    char q = s.charAt(j);
                    if (q == '\\') { j += 2; continue; }
                    if (q == '"') { closed = true; j++; break; }
                    j++;
                }
                if (!closed) return expr;
                tok = s.substring(i, j);
                kind = 0;
                i = j;
            } else if (Character.isDigit(c) || c == '.') { // 数字（含小数/指数）
                int j = i;
                while (j < s.length()) {
                    char d = s.charAt(j);
                    if (Character.isDigit(d) || d == '.' || d == 'e' || d == 'E') { j++; continue; }
                    if ((d == '+' || d == '-') && j > i
                            && (s.charAt(j - 1) == 'e' || s.charAt(j - 1) == 'E')) { j++; continue; }
                    break;
                }
                tok = s.substring(i, j);
                kind = 0;
                i = j;
            } else if (Character.isLetter(c) || c == '_') { // 标识符（函数名/true/false）
                int j = i;
                while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) j++;
                tok = s.substring(i, j);
                kind = 0;
                ident = true;
                i = j;
            } else if ((c == '&' || c == '|' || c == '=' || c == '!' || c == '>' || c == '<')
                    && i + 1 < s.length() && s.charAt(i + 1) == c) { // && || == != >> <<
                tok = s.substring(i, i + 2);
                kind = 1;
                i += 2;
            } else if ((c == '>' || c == '<' || c == '=' || c == '!')
                    && i + 1 < s.length() && s.charAt(i + 1) == '=') { // >= <=
                tok = s.substring(i, i + 2);
                kind = 1;
                i += 2;
            } else {
                tok = String.valueOf(c);
                if (c == '(' || c == ')') kind = 2;
                else if (c == ',') kind = 3;
                else kind = 1;
                i++;
            }
            // 空格规则
            if (!first) {
                boolean needSpace = true;
                if (kind == 2 && tok.equals(")")) needSpace = false;            // 右括号紧贴左侧
                else if (kind == 3) needSpace = false;                           // 逗号紧贴左侧
                else if (prevOpenParen) needSpace = false;                       // 左括号紧贴右侧
                else if (kind == 2 && tok.equals("(") && prevIdent) needSpace = false; // 函数调用
                if (needSpace) out.append(' ');
            }
            out.append(tok);
            first = false;
            prevIdent = ident;
            prevOpenParen = (kind == 2 && tok.equals("("));
        }
        return out.toString();
    }

    /** 条件判断：表达式求值成功且 truthy */
    public static boolean test(String expr, NBTTagCompound nbt, SignalValue in) {
        SignalValue v = evaluate(expr, nbt, in);
        return v != null && v.truthy();
    }

    /** 条件判断（多面输入） */
    public static boolean test(String expr, NBTTagCompound nbt, SignalValue[] ins, int selfSide) {
        SignalValue v = evaluate(expr, nbt, ins, selfSide);
        return v != null && v.truthy();
    }

    // ============ 递归下降解析器 ============

    private static final class Parser {
        private final String s;
        private final NBTTagCompound nbt;
        private final SignalValue[] ins;
        private final int selfSide;
        private int pos = 0;

        Parser(String s, NBTTagCompound nbt, SignalValue[] ins, int selfSide) {
            this.s = s;
            this.nbt = nbt;
            this.ins = ins;
            this.selfSide = selfSide;
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

        /**
         * 变量解析：
         *   {in}    → 当前编辑面（selfSide）收到的输入
         *   {inN}/{inE}/{inS}/{inW}/{inU}/{inD} → 指定方向面的输入（综合多面信号用）
         *   其他路径 → NBT 值（Number→DOUBLE，Boolean→BOOLEAN，String→STRING）
         */
        private SignalValue resolve(String path) {
            if (path == null || path.isEmpty()) return null;
            if (path.startsWith("in")) {
                return resolveIn(path);
            }
            if (nbt == null) return null;
            Object v = NbtVariableUtil.resolveValue(nbt, path);
            if (v == null) return null;
            if (v instanceof Boolean) return SignalValue.ofBool((Boolean) v);
            if (v instanceof Number) return SignalValue.ofDouble(((Number) v).doubleValue());
            return SignalValue.ofString(v.toString());
        }

        /** {in} / {inX} 解析；未识别方向或对应面无输入返回 null（不落入 NBT 解析） */
        private SignalValue resolveIn(String path) {
            if (ins == null) return null;
            if (path.equals("in")) {
                return (selfSide >= 0 && selfSide < ins.length) ? ins[selfSide] : null;
            }
            if (path.length() == 3) {
                int side = dirSide(path.charAt(2));
                if (side >= 0 && side < ins.length) return ins[side];
            }
            return null;
        }

        private static int dirSide(char c) {
            switch (c) {
                case 'N': return 2; // NORTH
                case 'S': return 3; // SOUTH
                case 'W': return 4; // WEST
                case 'E': return 5; // EAST
                case 'U': return 1; // UP
                case 'D': return 0; // DOWN
                default: return -1;
            }
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
