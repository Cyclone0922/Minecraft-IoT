package com.cyclone.minecraftiot.gui;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.network.PacketActuatorConfig;
import com.cyclone.minecraftiot.tileentity.TileActuator;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 执行器 GUI。
 * 左上：绑定卡槽 + 技能插件槽。
 * 右上：方向按钮切换当前编辑面。
 * 中部：条件表达式输入框 + 输出表达式输入框 + 输出方向按钮组 + 红石强度调节 + 保存按钮。
 * 底部：玩家背包。
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

    // 条件表达式输入框
    private static final int COND_X = 8, COND_Y = 70, COND_W = 160, COND_H = 14;
    // 输出表达式输入框
    private static final int OUT_X = 8, OUT_Y = 94, OUT_W = 160, OUT_H = 14;
    // 输出方向按钮组
    private static final int OUTDIR_Y = 112;
    private static final int[] OUTDIR_X = {60, 78, 96, 114, 132, 150};
    // 红石强度
    private static final int RS_LABEL_X = 8, RS_Y = 132;
    private static final int RS_MINUS_X = 50, RS_PLUS_X = 90, RS_BTN_W = 20, RS_BTN_H = 14;
    private static final int RS_VAL_X = 74, RS_VAL_Y = 132;
    // 保存按钮
    private static final int SAVE_X = 120, SAVE_Y = 132, SAVE_W = 48, SAVE_H = 16;

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
        // 输入框必须用相对坐标，否则绘制时会再叠加 translate 导致错位
        this.condField = new GuiTextField(this.fontRendererObj, COND_X, COND_Y, COND_W, COND_H);
        this.condField.setMaxStringLength(500);
        this.condField.setFocused(true);
        this.outField = new GuiTextField(this.fontRendererObj, OUT_X, OUT_Y, OUT_W, OUT_H);
        this.outField.setMaxStringLength(500);
        loadSideConfig(selectedSide);

        // 方向按钮（切换当前编辑面）
        for (int i = 0; i < 6; i++) {
            this.buttonList.add(new GuiButton(i, guiLeft + DIR_X[i], guiTop + DIR_Y[i], BTN, BTN, DIR_LABEL[i]));
        }
        // 红石强度 -/+
        this.buttonList.add(new GuiButton(10, guiLeft + RS_MINUS_X, guiTop + RS_Y, RS_BTN_W, RS_BTN_H, "-"));
        this.buttonList.add(new GuiButton(11, guiLeft + RS_PLUS_X, guiTop + RS_Y, RS_BTN_W, RS_BTN_H, "+"));
        // 保存
        this.buttonList.add(new GuiButton(12, guiLeft + SAVE_X, guiTop + SAVE_Y, SAVE_W, SAVE_H, "保存"));
        // 输出方向按钮组（N/U/W/E/D/S，点击设置该面输出方向）
        for (int i = 0; i < 6; i++) {
            this.buttonList.add(new GuiButton(20 + i, guiLeft + OUTDIR_X[i], guiTop + OUTDIR_Y, 16, 12, DIR_LABEL[i]));
        }
        refreshOutDirButtons();
    }

    /** 把指定面的配置加载到输入框 */
    private void loadSideConfig(int side) {
        if (condField != null) condField.setText(tile.getCondition(side));
        if (outField != null) outField.setText(tile.getOutputExpr(side));
    }

    /** 刷新输出方向按钮高亮：当前选中的方向蓝色，其余灰色 */
    private void refreshOutDirButtons() {
        int cur = tile.getSignalDir(selectedSide);
        for (int i = 0; i < 6; i++) {
            int idx = DIR_IDX[i];
            GuiButton b = null;
            for (Object o : this.buttonList) {
                GuiButton g = (GuiButton) o;
                if (g.id == 20 + i) { b = g; break; }
            }
            if (b != null) {
                b.displayString = (idx == cur ? "\u00a7b" : "\u00a78") + DIR_LABEL[i];
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id >= 0 && button.id < 6) {
            // 切换方向
            selectedSide = DIR_IDX[button.id];
            loadSideConfig(selectedSide);
            refreshOutDirButtons();
            this.condField.setFocused(true);
        } else if (button.id == 10) {
            int cur = tile.getRedstoneLevel(selectedSide);
            tile.setRedstoneLevel(selectedSide, Math.max(0, cur - 1));
        } else if (button.id == 11) {
            int cur = tile.getRedstoneLevel(selectedSide);
            tile.setRedstoneLevel(selectedSide, Math.min(15, cur + 1));
        } else if (button.id >= 20 && button.id < 26) {
            // 输出方向：点击直接设置
            tile.setSignalDir(selectedSide, DIR_IDX[button.id - 20]);
            refreshOutDirButtons();
        } else if (button.id == 12) {
            // 保存：发网络包到服务端
            String expr = condField.getText();
            String outExpr = outField.getText();
            int rs = tile.getRedstoneLevel(selectedSide);
            int outDir = tile.getSignalDir(selectedSide);
            MinecraftIotMod.network.sendToServer(
                    new PacketActuatorConfig(tile.xCoord, tile.yCoord, tile.zCoord,
                            selectedSide, expr, rs, outExpr, outDir));
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
        // 输出方向标签
        this.fontRendererObj.drawString("输出方向:", 8, 114, 0x404040);
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
}
