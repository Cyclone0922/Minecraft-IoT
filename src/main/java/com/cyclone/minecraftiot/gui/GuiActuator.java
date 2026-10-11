package com.cyclone.minecraftiot.gui;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.network.PacketActuatorConfig;
import com.cyclone.minecraftiot.tileentity.TileActuator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 执行器 GUI（方向合一模型）。
 * 左上：绑定卡槽 + 技能插件槽。
 * 右上：方向按钮切换当前编辑面——当前面 = 输出面，条件/输出表达式/红石强度都挂在本面。
 * 中部：条件表达式输入框 + 输出表达式输入框（各带"全屏"编辑按钮）+ 红石强度调节 + 保存按钮。
 * 底部：玩家背包。
 *
 * 草稿机制（pending）：全屏表达式编辑器"保存"只回填输入框（挂 pending 草稿），
 * 主界面点"保存"发网络包落存档，收到服务端确认（PacketActuatorSaveAck）后清 pending 并聊天提示。
 */
public class GuiActuator extends GuiContainer {

    private static final ResourceLocation background = new ResourceLocation("minecraftiot", "textures/gui/sensor.png");

    private TileActuator tile;

    // 方向按钮（与传感器 GUI 同布局）
    private static final String[] DIR_LABEL = {"N", "U", "W", "E", "D", "S"};
    private static final int[] DIR_IDX = {2, 1, 4, 5, 0, 3};
    private static final int[] DIR_X = {140, 158, 122, 158, 122, 140};
    private static final int[] DIR_Y = {6, 6, 30, 30, 54, 54};
    private static final int BTN = 18;

    // 条件表达式输入框（右侧"全屏"按钮）
    private static final int COND_X = 8, COND_Y = 70, COND_W = 130, COND_H = 14;
    private static final int FS_COND_X = 140, FS_W = 28, FS_H = 14;
    // 输出表达式输入框
    private static final int OUT_X = 8, OUT_Y = 94, OUT_W = 130, OUT_H = 14;
    private static final int FS_OUT_X = 140;
    // 红石强度
    private static final int RS_LABEL_X = 8, RS_Y = 132;
    private static final int RS_MINUS_X = 50, RS_PLUS_X = 90, RS_BTN_W = 20, RS_BTN_H = 14;
    private static final int RS_VAL_X = 74, RS_VAL_Y = 132;
    // 保存按钮
    private static final int SAVE_X = 120, SAVE_Y = 132, SAVE_W = 48, SAVE_H = 16;

    // 未保存草稿（每面）：全屏编辑器"保存"只写这里，主界面保存并收到确认后清空。
    // 跨 GUI 实例存续（全屏浮窗关闭时会重建本 GUI），故用静态；1.7.10 同一时刻仅一个 GUI，无并发串扰。
    private static final String[] pendingCond = new String[6];
    private static final String[] pendingOut = new String[6];

    /** 全屏编辑器"保存"回调：条件表达式回填草稿（不落存档） */
    public static void setPendingCond(int side, String expr) {
        if (side >= 0 && side < 6) pendingCond[side] = expr == null ? "" : expr;
    }

    /** 全屏编辑器"保存"回调：输出表达式回填草稿（不落存档） */
    public static void setPendingOut(int side, String expr) {
        if (side >= 0 && side < 6) pendingOut[side] = expr == null ? "" : expr;
    }

    /** 清空全部面的未保存草稿（退出执行器 GUI 时调用） */
    public static void clearPending() {
        for (int i = 0; i < 6; i++) {
            pendingCond[i] = null;
            pendingOut[i] = null;
        }
    }

    // 全屏编辑器跳转标志：打开全屏编辑器时为 true（此时旧 GUI 的 onGuiClosed 会触发，但不得清草稿）；
    // 回到主界面后复位 false，此时玩家真正退出 GUI 才清空草稿。
    private static boolean editorOpen = false;

    /** 全屏编辑器打开/关闭标记 */
    public static void setEditorOpen(boolean open) {
        editorOpen = open;
    }

    // 全屏编辑器返回时带回来的面选择（一次性，initGui 读取后复位）
    private static int pendingSide = -1;

    /** 全屏编辑器返回主界面时回写当前编辑面，保证主界面保持该面 */
    public static void setPendingSide(int side) {
        pendingSide = side;
    }

    private GuiTextField condField;
    private GuiTextField outField;
    private int selectedSide = 0; // 当前编辑的面（VALID_DIRECTIONS 索引）

    public GuiActuator(Container container) {
        super(container);
        this.tile = ((ContainerActuator) container).tileActuator;
        this.xSize = 176;
        this.ySize = 236;
    }

    @Override
    public void initGui() {
        super.initGui();
        // 全屏编辑器返回时带回来的面选择（一次性，读取后复位）
        if (pendingSide >= 0 && pendingSide < 6) {
            selectedSide = pendingSide;
            pendingSide = -1;
        }
        // 开启按键重复：按住 Backspace/Delete/方向键可连续删除/移动（原版 Minecraft 默认关闭重复事件）
        org.lwjgl.input.Keyboard.enableRepeatEvents(true);
        // 输入框必须用相对坐标，否则绘制时会再叠加 translate 导致错位
        this.condField = new GuiTextField(this.fontRendererObj, COND_X, COND_Y, COND_W, COND_H);
        this.condField.setMaxStringLength(500);
        // 打开 GUI 不自动聚焦：点击表达式框后才进入编辑（避免打开即弹光标、误触键盘输入）
        this.outField = new GuiTextField(this.fontRendererObj, OUT_X, OUT_Y, OUT_W, OUT_H);
        this.outField.setMaxStringLength(500);
        loadSideConfig(selectedSide);

        // 方向按钮（切换当前编辑面）
        for (int i = 0; i < 6; i++) {
            this.buttonList.add(new GuiButton(i, guiLeft + DIR_X[i], guiTop + DIR_Y[i], BTN, BTN, DIR_LABEL[i]));
        }
        // 全屏编辑按钮（条件/输出各一）
        this.buttonList.add(new GuiButton(13, guiLeft + FS_COND_X, guiTop + COND_Y, FS_W, FS_H, "全屏"));
        this.buttonList.add(new GuiButton(14, guiLeft + FS_OUT_X, guiTop + OUT_Y, FS_W, FS_H, "全屏"));
        // 红石强度 -/+
        this.buttonList.add(new GuiButton(10, guiLeft + RS_MINUS_X, guiTop + RS_Y, RS_BTN_W, RS_BTN_H, "-"));
        this.buttonList.add(new GuiButton(11, guiLeft + RS_PLUS_X, guiTop + RS_Y, RS_BTN_W, RS_BTN_H, "+"));
        // 保存
        this.buttonList.add(new GuiButton(12, guiLeft + SAVE_X, guiTop + SAVE_Y, SAVE_W, SAVE_H, "保存"));
        // 已回到主界面（从全屏编辑器返回）：复位跳转标志，此后玩家真正退出 GUI 才清空草稿
        editorOpen = false;
    }

    /** 把指定面的配置加载到输入框：优先未保存草稿（pending），否则读执行器已存配置 */
    private void loadSideConfig(int side) {
        if (condField != null) {
            String t = pendingCond[side] != null ? pendingCond[side] : tile.getCondition(side);
            condField.setText(t == null ? "" : t);
        }
        if (outField != null) {
            String t = pendingOut[side] != null ? pendingOut[side] : tile.getOutputExpr(side);
            outField.setText(t == null ? "" : t);
        }
    }

    /** 服务端保存成功确认（PacketActuatorSaveAck 回调）：清草稿 + 聊天提示 */
    public static void onSaveAck(int x, int y, int z, int side) {
        if (side >= 0 && side < 6) {
            pendingCond[side] = null;
            pendingOut[side] = null;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            String dirName = (side >= 0 && side < 6)
                    ? ForgeDirection.VALID_DIRECTIONS[side].name() : "?";
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    "\u00a7a[\u6267\u884c\u5668] \u4fdd\u5b58\u6210\u529f (\u9762: " + dirName + ")"));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id >= 0 && button.id < 6) {
            // 切换方向（方向合一：当前面 = 输出面，条件/动作都挂在本面）；不自动聚焦输入框
            selectedSide = DIR_IDX[button.id];
            loadSideConfig(selectedSide);
        } else if (button.id == 10) {
            int cur = tile.getRedstoneLevel(selectedSide);
            tile.setRedstoneLevel(selectedSide, Math.max(0, cur - 1));
        } else if (button.id == 11) {
            int cur = tile.getRedstoneLevel(selectedSide);
            tile.setRedstoneLevel(selectedSide, Math.min(15, cur + 1));
        } else if (button.id == 12) {
            // 保存：发网络包到服务端（服务端校验通过后回确认包；失败则静默）
            String expr = condField.getText();
            String outExpr = outField.getText();
            int rs = tile.getRedstoneLevel(selectedSide);
            MinecraftIotMod.network.sendToServer(
                    new PacketActuatorConfig(tile.xCoord, tile.yCoord, tile.zCoord,
                            selectedSide, expr, rs, outExpr));
        } else if (button.id == 13) {
            // 条件表达式全屏编辑（先标记跳转，防止本 GUI 关闭时误清草稿）
            setEditorOpen(true);
            Minecraft.getMinecraft().displayGuiScreen(new GuiActuatorExprEditor(
                    tile, (ContainerActuator) this.inventorySlots, selectedSide, 0, condField.getText()));
        } else if (button.id == 14) {
            // 输出表达式全屏编辑
            setEditorOpen(true);
            Minecraft.getMinecraft().displayGuiScreen(new GuiActuatorExprEditor(
                    tile, (ContainerActuator) this.inventorySlots, selectedSide, 1, outField.getText()));
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.mc.getTextureManager().bindTexture(background);
        this.drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // 标题
        this.fontRendererObj.drawString("执行器", 8, 5, 0x404040);
        // 槽位标签
        this.fontRendererObj.drawString("卡", 28, 11, 0x404040);
        this.fontRendererObj.drawString("插件", 28, 35, 0x404040);
        // 条件标签
        this.fontRendererObj.drawString("条件 (" + ForgeDirection.VALID_DIRECTIONS[selectedSide].name() + "):", 8, 58, 0x404040);
        // 输出表达式标签
        this.fontRendererObj.drawString("输出表达式:", 8, 82, 0x404040);
        // 红石强度标签
        this.fontRendererObj.drawString("强度:", RS_LABEL_X, RS_Y + 3, 0x404040);
        this.fontRendererObj.drawString(String.valueOf(tile.getRedstoneLevel(selectedSide)), RS_VAL_X, RS_VAL_Y + 3, 0x404040);
        // 状态行（与背包区顶对齐）
        String connStatus = tile.hasConnector() ? "绑定卡: 已插" : "绑定卡: 未插";
        this.fontRendererObj.drawString(connStatus, 8, 148, tile.hasConnector() ? 0x228B22 : 0xAA0000);
        String skillStatus = tile.hasRedstoneSkill() ? "红石技能: 已装" : "红石技能: 未装";
        this.fontRendererObj.drawString(skillStatus, 62, 148, tile.hasRedstoneSkill() ? 0x228B22 : 0xAA0000);

        // 输入框
        condField.drawTextBox();
        outField.drawTextBox();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (condField.isFocused()) {
            condField.textboxKeyTyped(typedChar, keyCode);
        } else if (outField.isFocused()) {
            outField.textboxKeyTyped(typedChar, keyCode);
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        // 输入框存的是 GUI 相对坐标，减去 guiLeft/guiTop 后点击检测才对齐
        condField.mouseClicked(mouseX - guiLeft, mouseY - guiTop, mouseButton);
        outField.mouseClicked(mouseX - guiLeft, mouseY - guiTop, mouseButton);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        condField.updateCursorCounter();
        outField.updateCursorCounter();
    }

    /**
     * 关闭执行器 GUI：清空未保存草稿（pending）。
     * 例外：跳去全屏编辑器时（editorOpen=true）不清——草稿要在编辑器-主界面之间往返存续。
     */
    @Override
    public void onGuiClosed() {
        if (!editorOpen) {
            clearPending();
        }
        super.onGuiClosed();
    }

    /**
     * 窗口号防御：若本 GUI 容器与服务端同步对象（thePlayer.openContainer）窗口号分叉
     * （例如经 displayGuiScreen 重开 GUI 时容器被重建、windowId 归零），
     * 点击包会因窗口号不匹配被服务端丢弃，表现为"卡拿不出来"。
     * 这里在每次点击前把窗口号对齐到 thePlayer.openContainer，保证与服务端一致。
     */
    @Override
    protected void handleMouseClick(Slot slot, int slotId, int clickedButton, int clickType) {
        if (mc.thePlayer != null && mc.thePlayer.openContainer != null
                && this.inventorySlots.windowId != mc.thePlayer.openContainer.windowId) {
            this.inventorySlots.windowId = mc.thePlayer.openContainer.windowId;
        }
        super.handleMouseClick(slot, slotId, clickedButton, clickType);
    }
}
