package com.cyclone.minecraftiot.gui;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.network.PacketWorkbenchAction;
import com.cyclone.minecraftiot.network.PacketWorkbenchData;
import com.cyclone.minecraftiot.network.PacketWorkbenchRequest;
import com.cyclone.minecraftiot.tileentity.TileConnectorWorkbench;
import com.cyclone.minecraftiot.util.NbtVariableUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

/**
 * 连接器工作台的全屏表达式编辑器（不暂停游戏，游戏刻照常运行）。
 *  - 展示目标机器 NBT 拍平后的全部变量（路径: 值），可勾选
 *  - "勾选生成"把勾选变量组合成默认表达式（每行 路径: {路径}）
 *  - 表达式输入框支持 TemplateExpr 语法（"文本" + {路径} + 算术 + \n 字面转义）
 *  - 保存 → PacketWorkbenchAction(ACTION_SAVE_EXPR) → 写进卡 NBT
 */
public class GuiCardConfigEditor extends GuiScreen {

    private final TileConnectorWorkbench workbench;
    private final NBTTagCompound nbt;
    private final String targetClass;
    private final String initialExpr;

    private List<NbtVariableUtil.Var> vars = new ArrayList<NbtVariableUtil.Var>();
    private boolean[] checked;
    private int scroll = 0;
    private GuiTextField exprField;

    private static final int VAR_X = 16;
    private static final int VAR_W = 320;
    private static final int VAR_ROW_H = 11;
    private static final int VAR_TOP = 40;

    public GuiCardConfigEditor(TileConnectorWorkbench workbench, NBTTagCompound nbt, String targetClass, String initialExpr) {
        this.workbench = workbench;
        this.nbt = nbt;
        this.targetClass = targetClass == null ? "" : targetClass;
        this.initialExpr = initialExpr == null ? "" : initialExpr;
    }

    @Override
    public void initGui() {
        super.initGui();
        List<NbtVariableUtil.Var> all = NbtVariableUtil.flatten(nbt);
        if (all != null) {
            // 只保留标量可引用变量（路径 + 值）
            for (NbtVariableUtil.Var v : all) {
                if (v != null && v.path != null && !v.path.isEmpty() && v.value != null) {
                    vars.add(v);
                }
            }
        }
        checked = new boolean[vars.size()];
        // 表达式输入框（全屏底部）
        exprField = new GuiTextField(this.fontRendererObj, 16, this.height - 132, this.width - 32, 16);
        exprField.setMaxStringLength(5000);
        exprField.setText(initialExpr);
        exprField.setFocused(true);
        exprField.setCursorPositionEnd();
    }

    @Override
    public void updateScreen() {
        exprField.updateCursorCounter();
        // 每 20 tick 向服务端请求最新目标 NBT，动态刷新变量（游戏刻不暂停，方便观察动态值）
        if (++refreshTick >= 20) {
            refreshTick = 0;
            MinecraftIotMod.network.sendToServer(new PacketWorkbenchRequest(
                    workbench.xCoord, workbench.yCoord, workbench.zCoord));
        }
    }

    private int refreshTick = 0;

    /** 网络包回调：表达式编辑器打开时收到最新数据 → 重建变量列表（保留勾选与滚动） */
    public static void onData(PacketWorkbenchData p) {
        GuiScreen s = Minecraft.getMinecraft().currentScreen;
        if (s instanceof GuiCardConfigEditor) {
            GuiCardConfigEditor e = (GuiCardConfigEditor) s;
            if (p.getHasNbt() && p.getNbtBytes() != null) {
                try {
                    NBTTagCompound n = CompressedStreamTools.func_152457_a(p.getNbtBytes(), NBTSizeTracker.field_152451_a);
                    e.refreshVars(n);
                } catch (Throwable ignore) {}
            }
        }
    }

    /** 用最新 NBT 重建变量列表：保留已勾选路径与滚动位置，不打断输入框 */
    private void refreshVars(NBTTagCompound newNbt) {
        if (newNbt == null) return;
        List<NbtVariableUtil.Var> all = NbtVariableUtil.flatten(newNbt);
        if (all == null) return;
        List<NbtVariableUtil.Var> fresh = new ArrayList<NbtVariableUtil.Var>();
        for (NbtVariableUtil.Var v : all) {
            if (v != null && v.path != null && !v.path.isEmpty() && v.value != null) fresh.add(v);
        }
        if (fresh.isEmpty()) return; // 避免瞬间清空列表
        boolean[] oldChecked = checked;
        boolean[] nc = new boolean[fresh.size()];
        for (int i = 0; i < fresh.size(); i++) {
            String path = fresh.get(i).path;
            for (int j = 0; oldChecked != null && j < oldChecked.length; j++) {
                if (oldChecked[j] && path.equals(vars.get(j).path)) { nc[i] = true; break; }
            }
        }
        vars = fresh;
        checked = nc;
        if (scroll > Math.max(0, vars.size() - visibleRows())) scroll = Math.max(0, vars.size() - visibleRows());
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
        // 变量勾选
        int startY = VAR_TOP - scroll * VAR_ROW_H;
        for (int i = 0; i < vars.size(); i++) {
            int y = startY + i * VAR_ROW_H;
            if (mouseX >= VAR_X - 2 && mouseX <= VAR_X + 10 && mouseY >= y && mouseY < y + VAR_ROW_H) {
                checked[i] = !checked[i];
                return;
            }
        }
        // 按钮（底部一行四个等宽）
        if (inRect(mouseX, mouseY, 16, this.height - 96, 70, 16)) { generateFromChecked(); return; }
        if (inRect(mouseX, mouseY, 90, this.height - 96, 70, 16)) { copyExpr(); return; }
        if (inRect(mouseX, mouseY, 164, this.height - 96, 70, 16)) { save(); return; }
        if (inRect(mouseX, mouseY, 238, this.height - 96, 70, 16)) { close(); return; }
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
     * 勾选变量 → 组合默认表达式（TemplateExpr 语法，多行）。
     * 文本段必须用双引号包裹，行间用 "\n" 字面转义（字符串内部 \n → 真实换行）：
     *   "路径1: " + {路径1} + "\n" + "路径2: " + {路径2} + "\n" + ...
     */
    private void generateFromChecked() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vars.size(); i++) {
            if (checked[i]) {
                if (sb.length() > 0) {
                    sb.append(" + \"\\n\" + ");
                }
                sb.append("\"").append(vars.get(i).path).append(": \"").append(" + {").append(vars.get(i).path).append("}");
            }
        }
        if (sb.length() == 0) {
            sb.append("\"\u8bf7\u5148\u52fe\u9009\u53d8\u91cf\"");
        }
        exprField.setText(sb.toString());
        exprField.setCursorPositionEnd();
    }

    private void save() {
        String expr = exprField.getText();
        MinecraftIotMod.network.sendToServer(new PacketWorkbenchAction(
                workbench.xCoord, workbench.yCoord, workbench.zCoord,
                TileConnectorWorkbench.ACTION_SAVE_EXPR, expr));
        close();
    }

    private void close() {
        // 重新走 GuiHandler 打开工作台 GUI：服务端重建 Container，客户端槽位同步链路保持一致。
        // 不能在这里 new ContainerConnectorWorkbench —— 客户端新建的 Container 与
        // openContainer（服务端同步对象）分叉：拖出卡后服务端槽位已扣、但客户端 GUI 仍
        // 从本地 tile 读到卡，退出/重开 GUI 时表现为"卡自动回到工作台"。
        Minecraft.getMinecraft().thePlayer.openGui(MinecraftIotMod.instance,
                com.cyclone.minecraftiot.gui.GuiHandler.GUI_WORKBENCH,
                Minecraft.getMinecraft().theWorld,
                workbench.xCoord, workbench.yCoord, workbench.zCoord);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int textColor = 0xFF333333;
        int dim = 0xFF777777;

        // 标题
        this.fontRendererObj.drawString("\u8868\u8fbe\u5f0f\u7f16\u8f91\u5668 - " + shortClass(targetClass), 16, 12, 0xFF3B6EA5);
        this.fontRendererObj.drawString("\u53ef\u7528\u53d8\u91cf\uff08\u52fe\u9009\u540e\u70b9\u201c\u52fe\u9009\u751f\u6210\u201d\uff09", 16, 26, 0xFFFFFFFF);

        // 变量列表（滚动）
        int startY = VAR_TOP - scroll * VAR_ROW_H;
        int endIdx = Math.min(vars.size(), scroll + visibleRows());
        for (int i = scroll; i < endIdx; i++) {
            NbtVariableUtil.Var v = vars.get(i);
            int y = startY + i * VAR_ROW_H;
            // 复选框
            drawRect(VAR_X - 2, y + 1, VAR_X + 6, y + 9, 0xFF3A3A3A);
            if (checked[i]) {
                drawRect(VAR_X - 1, y + 2, VAR_X + 5, y + 8, 0xFF3B6EA5);
            }
            String line = v.path + ": " + String.valueOf(v.value);
            this.fontRendererObj.drawString(fit(line, VAR_W - 16), VAR_X + 12, y, 0xFFFFFFFF);
        }

        // 表达式区
        int ey = this.height - 132;
        this.fontRendererObj.drawString("\u81ea\u5b9a\u4e49\u8868\u8fbe\u5f0f\uff08TemplateExpr\uff1a\u201c\u6587\u672c\u201d + {path} + \\n \u884c\u5206\u9694\uff09", 16, ey - 12, dim);
        drawRect(16, ey - 2, this.width - 16, ey + 18, 0xFF3A3A3A);
        exprField.drawTextBox();

        // 按钮（底部一行四个等宽）
        drawRect(16, this.height - 96, 86, this.height - 80, 0xFF3B6EA5);
        this.fontRendererObj.drawString("\u52fe\u9009\u751f\u6210", 16 + (70 - this.fontRendererObj.getStringWidth("\u52fe\u9009\u751f\u6210")) / 2, this.height - 91, 0xFFFFFFFF);
        drawRect(90, this.height - 96, 160, this.height - 80, 0xFF3B6EA5);
        this.fontRendererObj.drawString("\u590d\u5236\u4ee3\u7801", 90 + (70 - this.fontRendererObj.getStringWidth("\u590d\u5236\u4ee3\u7801")) / 2, this.height - 91, 0xFFFFFFFF);
        drawRect(164, this.height - 96, 234, this.height - 80, 0xFF3B6EA5);
        this.fontRendererObj.drawString("\u4fdd\u5b58", 164 + (70 - this.fontRendererObj.getStringWidth("\u4fdd\u5b58")) / 2, this.height - 91, 0xFFFFFFFF);
        drawRect(238, this.height - 96, 308, this.height - 80, 0xFF777777);
        this.fontRendererObj.drawString("\u8fd4\u56de", 238 + (70 - this.fontRendererObj.getStringWidth("\u8fd4\u56de")) / 2, this.height - 91, 0xFFFFFFFF);
    }

    private String shortClass(String cls) {
        int i = cls.lastIndexOf('.');
        String s = i >= 0 ? cls.substring(i + 1) : cls;
        return s.length() > 30 ? s.substring(0, 30) + "..." : s;
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
