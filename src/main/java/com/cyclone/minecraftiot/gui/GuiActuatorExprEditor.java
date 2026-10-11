package com.cyclone.minecraftiot.gui;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.network.PacketActuatorNbtData;
import com.cyclone.minecraftiot.network.PacketActuatorNbtRequest;
import com.cyclone.minecraftiot.tileentity.TileActuator;
import com.cyclone.minecraftiot.util.NbtVariableUtil;
import com.cyclone.minecraftiot.util.SignalValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

/**
 * 执行器全屏表达式编辑器（不暂停游戏，游戏刻照常运行）。
 *  - 数据快照：每 20t 向服务端请求（PacketActuatorNbtRequest → Data）：
 *      ① 绑定卡目标机器 NBT → 拍平可用变量；
 *      ② 6 面信号输入 {inX} → 实时值（有输入的面才显示，随时时变化）。
 *  - 单选：点击勾选框选中一项（再点取消），NBT 变量与 {inX} 在同一列表。
 *  - "勾选填入"：把选中变量以 {path} 形式增量追加到表达式末尾（不覆盖、不带"变量名: "前缀；
 *    文本非空且末尾紧贴内容时自动补 " + " 保证可直接求值）。
 *  - "保存"只回填主 GUI 表达式输入框（挂 pending 草稿），不写入执行器数据；
 *    需回主界面点"保存"才发网络包落存档。
 *  - "复制代码"把表达式复制到系统剪贴板。
 */
public class GuiActuatorExprEditor extends GuiScreen {

    /** 变量列表条目：NBT 变量或 {inX} 信号输入 */
    public static class VarItem {
        public final String path;   // 表达式引用路径（含 {} 内名，如 "tanks.0.Amount"、"inN"）
        public final String value;  // 预览值
        public final boolean isIn;  // true = {inX} 信号输入
        public VarItem(String path, String value, boolean isIn) {
            this.path = path;
            this.value = value;
            this.isIn = isIn;
        }
        public String ref() { return "{" + path + "}"; }
    }

    private final TileActuator tile;
    private final ContainerActuator container; // 进入编辑器前的主 GUI 容器；返回时复用（保留服务端窗口号）
    private final int side;      // 当前编辑面（VALID_DIRECTIONS 索引）
    private final int mode;      // 0=条件表达式 1=输出表达式
    private final String initialExpr;

    private List<VarItem> vars = new ArrayList<VarItem>();
    private int checkedIdx = -1; // 单选：当前勾选项下标（-1 = 无）
    private int scroll = 0;
    private GuiTextField exprField;
    private int refreshTick = 0;
    private boolean hasNbt = false; // 是否有目标 NBT（无卡/无数据时提示）

    private static final int VAR_X = 16;
    private static final int VAR_W = 320;
    private static final int VAR_ROW_H = 11;
    private static final int VAR_TOP = 40;

    // 常用运算符快捷按钮（点击插入到表达式光标处）
    private static final String[] OPS = {"+", "-", "*", "/", ">", "<", "==", "!=", "&&", "||", "!", "?"};
    private static final int OP_X = 16;
    private static final int OP_W = 18;
    private static final int OP_H = 14;
    private static final int OP_GAP = 2;

    public GuiActuatorExprEditor(TileActuator tile, ContainerActuator container, int side, int mode, String initialExpr) {
        this.tile = tile;
        this.container = container;
        this.side = side;
        this.mode = mode;
        this.initialExpr = initialExpr == null ? "" : initialExpr;
    }

    @Override
    public void initGui() {
        super.initGui();
        // 开启按键重复：按住 Backspace/Delete 可连续删除
        org.lwjgl.input.Keyboard.enableRepeatEvents(true);
        // 表达式输入框（全屏底部）
        exprField = new GuiTextField(this.fontRendererObj, 16, this.height - 132, this.width - 32, 16);
        exprField.setMaxStringLength(5000);
        exprField.setText(initialExpr);
        exprField.setFocused(true);
        exprField.setCursorPositionEnd();
        // 立即请求一次数据快照（打开就列出 {inX} 与可用变量）
        requestNbt();
    }

    private void requestNbt() {
        if (tile == null || tile.getWorldObj() == null) return;
        MinecraftIotMod.network.sendToServer(new PacketActuatorNbtRequest(
                tile.xCoord, tile.yCoord, tile.zCoord, side));
    }

    @Override
    public void updateScreen() {
        exprField.updateCursorCounter();
        // 每 20 tick 请求最新快照，{inX} 与 NBT 变量时时刷新（游戏刻不暂停，方便观察动态值）
        if (++refreshTick >= 20) {
            refreshTick = 0;
            requestNbt();
        }
    }

    /** 网络包回调：收到最新快照 → 重建变量列表（保留单选与滚动），不打断输入框 */
    public static void onData(PacketActuatorNbtData p) {
        GuiScreen s = Minecraft.getMinecraft().currentScreen;
        if (!(s instanceof GuiActuatorExprEditor)) return;
        GuiActuatorExprEditor e = (GuiActuatorExprEditor) s;
        if (e.tile == null || e.tile.xCoord != p.getX() || e.tile.yCoord != p.getY() || e.tile.zCoord != p.getZ()) return;
        e.refreshVars(p);
    }

    /** 6 面方向字母（与 SignalExpr.dirSide 映射一致：0=D 1=U 2=N 3=S 4=W 5=E） */
    private static final char[] IN_DIR_CHAR = {'D', 'U', 'N', 'S', 'W', 'E'};

    /** 用最新快照重建列表：先 6 面 {inX}（有输入才显示），再 NBT 变量；保留单选与滚动 */
    private void refreshVars(PacketActuatorNbtData p) {
        List<VarItem> fresh = new ArrayList<VarItem>();
        // ① 6 面信号输入：仅显示"有输入可读"的面（按 buildInValues(side) 语义）
        for (int i = 0; i < 6; i++) {
            if (!p.getInHas(i)) continue;
            String v;
            byte t = p.getInType(i);
            if (t == SignalValue.TYPE_DOUBLE) v = SignalValue.ofDouble(p.getInNum(i)).asString();
            else if (t == SignalValue.TYPE_BOOL) v = String.valueOf(p.getInBool(i));
            else v = p.getInStr(i);
            fresh.add(new VarItem("in" + IN_DIR_CHAR[i], v, true));
        }
        // ② 目标机器 NBT 变量
        if (p.getHasNbt() && p.getNbtBytes() != null) {
            try {
                NBTTagCompound n = CompressedStreamTools.func_152457_a(p.getNbtBytes(), NBTSizeTracker.field_152451_a);
                List<NbtVariableUtil.Var> all = NbtVariableUtil.flatten(n);
                for (NbtVariableUtil.Var v : all) {
                    if (v != null && v.path != null && !v.path.isEmpty() && v.value != null) {
                        fresh.add(new VarItem(v.path, v.value, false));
                    }
                }
                hasNbt = true;
            } catch (Throwable ignore) {
                hasNbt = false;
            }
        } else {
            hasNbt = false;
        }
        if (fresh.isEmpty()) {
            // 全部无数据：保留空列表，界面提示
            vars = fresh;
            checkedIdx = -1;
            return;
        }
        // 保留单选（按 path 匹配）
        String keepPath = (checkedIdx >= 0 && checkedIdx < vars.size())
                ? vars.get(checkedIdx).path : null;
        vars = fresh;
        checkedIdx = -1;
        if (keepPath != null) {
            for (int i = 0; i < vars.size(); i++) {
                if (vars.get(i).path.equals(keepPath)) { checkedIdx = i; break; }
            }
        }
        int maxScroll = Math.max(0, vars.size() - visibleRows());
        if (scroll > maxScroll) scroll = maxScroll;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false; // 不暂停：方便观察动态值
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        exprField.mouseClicked(mouseX, mouseY, mouseButton);
        // 点击落在表达式输入框区域内则只交给输入框
        if (mouseX >= 16 && mouseX <= this.width - 16 && mouseY >= this.height - 134 && mouseY < this.height - 114) return;
        // 运算符快捷按钮（插入到光标处）
        int opY = this.height - 114;
        for (int i = 0; i < OPS.length; i++) {
            int ox = OP_X + i * (OP_W + OP_GAP);
            if (mouseX >= ox && mouseX < ox + OP_W && mouseY >= opY && mouseY < opY + OP_H) {
                insertOp(OPS[i]);
                return;
            }
        }
        // 变量勾选（单选：点选中，再点同一项取消）
        int startY = VAR_TOP - scroll * VAR_ROW_H;
        for (int i = 0; i < vars.size(); i++) {
            int y = startY + i * VAR_ROW_H;
            if (mouseX >= VAR_X - 2 && mouseX <= VAR_X + 10 && mouseY >= y && mouseY < y + VAR_ROW_H) {
                checkedIdx = (checkedIdx == i) ? -1 : i;
                return;
            }
        }
        // 按钮（底部一行四个等宽）
        if (inRect(mouseX, mouseY, 16, this.height - 96, 70, 16)) { fillChecked(); return; }
        if (inRect(mouseX, mouseY, 90, this.height - 96, 70, 16)) { copyExpr(); return; }
        if (inRect(mouseX, mouseY, 164, this.height - 96, 70, 16)) { save(); return; }
        if (inRect(mouseX, mouseY, 238, this.height - 96, 70, 16)) { close(); return; }
    }

    /** 运算符快捷插入：在表达式光标处插入运算符（不自动补空格，严格原样插入） */
    private void insertOp(String op) {
        String t = exprField.getText() == null ? "" : exprField.getText();
        int pos = Math.min(exprField.getCursorPosition(), t.length());
        exprField.setText(t.substring(0, pos) + op + t.substring(pos));
        exprField.setCursorPosition(pos + op.length());
        exprField.setFocused(true); // 插入后保持输入框聚焦，方便继续输入
    }

    /** 复制当前表达式到剪贴板（方便在外部编辑） */
    private void copyExpr() {
        GuiScreen.setClipboardString(exprField.getText() == null ? "" : exprField.getText());
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) { close(); return; }
        if (exprField.textboxKeyTyped(typedChar, keyCode)) return;
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dw = Mouse.getEventDWheel();
        if (dw != 0) {
            scroll += (dw > 0) ? -1 : 1;
            int maxScroll = Math.max(0, vars.size() - visibleRows());
            if (scroll < 0) scroll = 0;
            if (scroll > maxScroll) scroll = maxScroll;
        }
    }

    private int visibleRows() {
        return (this.height - 96 - VAR_TOP) / VAR_ROW_H;
    }

    /**
     * 勾选填入：把单选选中的变量以 {path} 形式**尾部增量**填入表达式末尾（不覆盖已有内容）。
     * 不自动拼接任何内容（无 "变量名: " 前缀，也无自动 " + " 分隔），严格裸填 {path}。
     */
    private void fillChecked() {
        if (checkedIdx < 0 || checkedIdx >= vars.size()) return;
        String cur = exprField.getText() == null ? "" : exprField.getText();
        String ref = vars.get(checkedIdx).ref();
        exprField.setText(cur + ref);
        exprField.setCursorPositionEnd();
    }

    /**
     * 保存：只把表达式回填到主 GUI 输入框（挂 pending 草稿），不写入执行器数据。
     * 需回主界面点"保存"按钮才发网络包落存档。
     */
    private void save() {
        String expr = exprField.getText();
        if (mode == 0) {
            GuiActuator.setPendingCond(side, expr);
        } else {
            GuiActuator.setPendingOut(side, expr);
        }
        close();
    }

    private void close() {
        // 回到主界面时保持当前编辑面（主 GUI 实例会重建，通过静态字段带回）
        GuiActuator.setPendingSide(side);
        // 复用进入编辑器时主 GUI 的容器实例：其 windowId 由服务端 OpenGui 包分配且与服务端一致。
        // 若重建容器或重新 openGui，客户端窗口号会归零，点击包会被服务端按窗口号丢弃，
        // 表现为第一次"卡拿不出来"、重开才好。
        if (container != null) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiActuator(container));
        } else {
            Minecraft.getMinecraft().thePlayer.openGui(MinecraftIotMod.instance,
                    GuiHandler.GUI_ACTUATOR,
                    Minecraft.getMinecraft().theWorld,
                    tile.xCoord, tile.yCoord, tile.zCoord);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int dim = 0xFF777777;

        // 标题
        String modeName = (mode == 0) ? "条件" : "输出表达式";
        String dirName = (side >= 0 && side < 6)
                ? ForgeDirection.VALID_DIRECTIONS[side].name() : "?";
        this.fontRendererObj.drawString("执行器表达式编辑器 - " + modeName + "(" + dirName + ")", 16, 12, 0xFF3B6EA5);
        this.fontRendererObj.drawString("可用变量（单选一个 → 点\"勾选填入\"追加到末尾）", 16, 26, 0xFFFFFFFF);
        this.fontRendererObj.drawString("{inX} 为各面信号输入，仅显示有输入的面，值实时变化", 16 + this.fontRendererObj.getStringWidth("可用变量（单选一个 → 点\"勾选填入\"追加到末尾）") + 8, 26, 0xFF7FD4FF);

        if (vars.isEmpty()) {
            String hint = hasNbt
                    ? "目标机器 NBT 无可用变量，且当前没有可读的 {inX} 输入"
                    : "无可用变量（请确保执行器已插入绑定卡且目标机器可读）";
            this.fontRendererObj.drawString(hint, 16, 44, 0xFF888888);
        }

        // 变量列表（滚动）：{inX} 用亮蓝，NBT 变量用白色
        int startY = VAR_TOP - scroll * VAR_ROW_H;
        int endIdx = Math.min(vars.size(), scroll + visibleRows());
        for (int i = scroll; i < endIdx; i++) {
            VarItem v = vars.get(i);
            int y = startY + i * VAR_ROW_H;
            // 复选框（单选）
            drawRect(VAR_X - 2, y + 1, VAR_X + 6, y + 9, 0xFF3A3A3A);
            if (checkedIdx == i) {
                drawRect(VAR_X - 1, y + 2, VAR_X + 5, y + 8, 0xFF3B6EA5);
            }
            String line = v.ref() + ": " + v.value;
            this.fontRendererObj.drawString(fit(line, VAR_W - 16), VAR_X + 12, y,
                    v.isIn ? 0xFF7FD4FF : 0xFFFFFFFF);
        }

        // 表达式区
        int ey = this.height - 132;
        this.fontRendererObj.drawString("自定义表达式（SignalExpr：\"文本\" + {path} + \\n 行分隔；{inX} 引用各面输入）", 16, ey - 12, dim);
        drawRect(16, ey - 2, this.width - 16, ey + 18, 0xFF3A3A3A);
        exprField.drawTextBox();

        // 运算符快捷按钮行（点击插入到光标处）
        int opY = this.height - 114;
        for (int i = 0; i < OPS.length; i++) {
            int ox = OP_X + i * (OP_W + OP_GAP);
            drawRect(ox, opY, ox + OP_W, opY + OP_H, 0xFF3B6EA5);
            String op = OPS[i];
            this.fontRendererObj.drawString(op, ox + (OP_W - this.fontRendererObj.getStringWidth(op)) / 2, opY + 3, 0xFFFFFFFF);
        }

        // 按钮（底部一行四个等宽）
        drawRect(16, this.height - 96, 86, this.height - 80, 0xFF3B6EA5);
        this.fontRendererObj.drawString("勾选填入", 16 + (70 - this.fontRendererObj.getStringWidth("勾选填入")) / 2, this.height - 91, 0xFFFFFFFF);
        drawRect(90, this.height - 96, 160, this.height - 80, 0xFF3B6EA5);
        this.fontRendererObj.drawString("复制代码", 90 + (70 - this.fontRendererObj.getStringWidth("复制代码")) / 2, this.height - 91, 0xFFFFFFFF);
        drawRect(164, this.height - 96, 234, this.height - 80, 0xFF3B6EA5);
        this.fontRendererObj.drawString("保存", 164 + (70 - this.fontRendererObj.getStringWidth("保存")) / 2, this.height - 91, 0xFFFFFFFF);
        drawRect(238, this.height - 96, 308, this.height - 80, 0xFF777777);
        this.fontRendererObj.drawString("返回", 238 + (70 - this.fontRendererObj.getStringWidth("返回")) / 2, this.height - 91, 0xFFFFFFFF);
    }

    private String fit(String s, int maxPx) {
        while (!s.isEmpty() && this.fontRendererObj.getStringWidth(s) > maxPx) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
