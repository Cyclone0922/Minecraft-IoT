package com.cyclone.minecraftiot.gui;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.network.PacketBindConnector;
import com.cyclone.minecraftiot.network.PacketSetSensorLabel;
import com.cyclone.minecraftiot.tileentity.TileSensor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class GuiSensor extends GuiContainer {

    private TileSensor tileSensor;
    private static final ResourceLocation background = new ResourceLocation("minecraftiot", "textures/gui/sensor.png");

    // 六个方向按钮。注意：Forge 1.7.10 的 ForgeDirection.VALID_DIRECTIONS 实际顺序是
    // [DOWN, UP, NORTH, SOUTH, WEST, EAST]（索引 0..5），并非"直觉"的上下北南东西。
    // 因此每个按钮用 DIR_IDX 显式记录它对应的 VALID_DIRECTIONS 索引，而不是用数组位置。
    // 布局（参考用户截图）：N 上中、U 右上、W 左中、E 右中、D 左下、S 下中
    private static final String[] DIR_LABEL = {"N", "U", "W", "E", "D", "S"};
    private static final int[] DIR_IDX = {2, 1, 4, 5, 0, 3}; // VALID_DIRECTIONS: DOWN=0,UP=1,NORTH=2,SOUTH=3,WEST=4,EAST=5
    private static final int[] DIR_X = {140, 158, 122, 158, 122, 140};
    private static final int[] DIR_Y = {6, 6, 30, 30, 54, 54};
    private static final int BTN = 18;

    // "写卡"按钮（槽位右侧）。显示配置编辑已迁移到连接器工作台。
    private static final int BIND_X = 30, BIND_Y = 8, BIND_W = 40, BIND_H = 18;

    // 自定义注释：输入框 + 保存按钮（左上连接槽下方空区，避开右侧方向按钮）
    private static final int NOTE_X = 8, NOTE_Y = 34, NOTE_LABEL_W = 18;
    private static final int NOTE_FIELD_X = 26, NOTE_FIELD_W = 70;
    private static final int NOTE_BTN_X = 98, NOTE_BTN_W = 20, NOTE_H = 12;

    private GuiTextField noteField;

    // 命名弹窗（点击"写卡"后弹出，输入自定义卡名以便背包区分）
    private static final int MODAL_X = 16, MODAL_Y = 8, MODAL_W = 144, MODAL_H = 50;
    private static final int MODAL_TITLE_Y = MODAL_Y + 5;
    private static final int MODAL_FIELD_X = MODAL_X + 8, MODAL_FIELD_Y = MODAL_Y + 18;
    private static final int MODAL_FIELD_W = 128, MODAL_FIELD_H = 14;
    private static final int MODAL_BTN_Y = MODAL_Y + MODAL_H - 16, MODAL_BTN_H = 14;
    private static final int MODAL_CANCEL_X = MODAL_X + MODAL_W - 88, MODAL_CANCEL_W = 36;
    private static final int MODAL_CONFIRM_X = MODAL_X + MODAL_W - 48, MODAL_CONFIRM_W = 40;
    private boolean naming = false;
    private GuiTextField nameField;

    // 扫描数据区块标题
    private static final int DATA_TITLE_X = 8, DATA_TITLE_Y = 65;
    // 「NBT」切换按钮（紧跟标题）：蓝=原始NBT，灰=友好格式
    private static final int RAW_X = 62, RAW_Y = 62, RAW_W = 36, RAW_H = 12;
    private boolean showRaw = false;
    // 「显示全部」按钮：打开全屏原始NBT浮层
    private static final int SHOWALL_X = 100, SHOWALL_W = 36, SHOWALL_Y = 62, SHOWALL_H = 12;

    // 数据区渲染下限（玩家背包从 y=140 开始，留 2px 空隙）
    private static final int DATA_MAX_Y = 133;

    // -1 = 显示全部方向；0..5 = 只显示该方向
    private int selectedIndex = -1;

    public GuiSensor(Container container) {
        super(container);
        this.tileSensor = ((ContainerSensor) container).tileSensor;
        this.xSize = 176;
        this.ySize = 216;
    }

    @Override
    public void initGui() {
        super.initGui();
        // 开启按键重复：按住 Backspace/Delete/方向键可连续删除/移动（原版 Minecraft 默认关闭重复事件）
        org.lwjgl.input.Keyboard.enableRepeatEvents(true);
        // fontRendererObj 只有到 initGui 阶段才就绪，必须在这里创建输入框（构造时用会 NPE）
        this.noteField = new GuiTextField(this.fontRendererObj, NOTE_FIELD_X, NOTE_Y, NOTE_FIELD_W, NOTE_H);
        this.noteField.setMaxStringLength(300);
        this.nameField = new GuiTextField(this.fontRendererObj, MODAL_FIELD_X, MODAL_FIELD_Y, MODAL_FIELD_W, MODAL_FIELD_H);
        this.nameField.setMaxStringLength(60);
        refreshNoteField();
    }

    /** 注释输入框跟随当前选中方向：无选中时清空并禁用，选中某方向时载入该方向注释 */
    private void refreshNoteField() {
        if (noteField == null || tileSensor == null) return;
        boolean hasDir = selectedIndex >= 0;
        noteField.setEnabled(hasDir);
        noteField.setText(hasDir ? tileSensor.getLabel(selectedIndex) : "");
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawDefaultBackground();
        this.mc.getTextureManager().bindTexture(background);
        int i = (width - xSize) / 2;
        int j = (height - ySize) / 2;
        drawTexturedModalRect(i, j, 0, 0, xSize, ySize);

        // connector 槽位背景（左上角 8,8，与 ContainerSensor 对应）
        int sx = i + 8, sy = j + 8;
        drawRect(sx, sy, sx + 18, sy + 18, 0xFF373737);
        drawRect(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF8B8B8B);

        // "写卡"按钮（槽位右侧）。显示配置编辑已迁移到连接器工作台，传感器不再提供"配置"按钮。
        drawRect(i + BIND_X, j + BIND_Y, i + BIND_X + BIND_W, j + BIND_Y + BIND_H, 0xFF3B6EA5);
        this.fontRendererObj.drawString("\u5199\u5361", i + BIND_X + 6, j + BIND_Y + 5, 0xFFFFFFFF);

        // 六个方向按钮
        for (int d = 0; d < 6; d++) {
            int bx = i + DIR_X[d], by = j + DIR_Y[d];
            boolean isSelected = (selectedIndex == DIR_IDX[d]);
            boolean enabled = (selectedIndex < 0) || isSelected; // 未选时全部可用；选中后仅当前可用
            int bg = !enabled ? 0xFF777777 : (isSelected ? 0xFF2E8B57 : 0xFF3B6EA5);
            drawRect(bx, by, bx + BTN, by + BTN, bg);
            this.fontRendererObj.drawString(DIR_LABEL[d], bx + 6, by + 5, 0xFFFFFFFF);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int textColor = 0xFF333333;

        // 自定义注释输入框 + 保存按钮（左上连接槽下方）。无选中方向时保存按钮变灰不可用。
        // 命名弹窗打开时不绘制注释区，避免图层压在弹窗输入框上。
        if (!naming) {
            this.fontRendererObj.drawString("\u6ce8\u91ca:", NOTE_X, NOTE_Y + 1, textColor);
            this.noteField.drawTextBox();
            boolean canSave = tileSensor != null && selectedIndex >= 0;
            drawRect(NOTE_BTN_X, NOTE_Y, NOTE_BTN_X + NOTE_BTN_W, NOTE_Y + NOTE_H, canSave ? 0xFF3B6EA5 : 0xFF777777);
            this.fontRendererObj.drawString("\u5b58", NOTE_BTN_X + 5, NOTE_Y + 1, 0xFFFFFFFF);
            if (!canSave) {
                this.fontRendererObj.drawString("(\u5148\u9009\u65b9\u5411\u518d\u6ce8\u91ca)", NOTE_X + 8, NOTE_Y + NOTE_H + 3, 0xFF555555);
            }
        }

        // 扫描数据区块：标题 + 「NBT」切换 + 「显示全部」
        this.fontRendererObj.drawString("\u626b\u63cf\u6570\u636e", DATA_TITLE_X, DATA_TITLE_Y, textColor);
        boolean rawActive = showRaw;
        drawRect(RAW_X, RAW_Y, RAW_X + RAW_W, RAW_Y + RAW_H, rawActive ? 0xFF3B6EA5 : 0xFF777777);
        this.fontRendererObj.drawString("NBT", RAW_X + 4, RAW_Y + 3, 0xFFFFFFFF);
        drawRect(SHOWALL_X, SHOWALL_Y, SHOWALL_X + SHOWALL_W, SHOWALL_Y + SHOWALL_H, 0xFF3B6EA5);
        this.fontRendererObj.drawString("\u5168\u90e8", SHOWALL_X + 4, SHOWALL_Y + 3, 0xFFFFFFFF);

        String[] lines = tileSensor != null ? (showRaw ? tileSensor.getRawResults() : tileSensor.getScanResults()) : null;
        if (lines == null || countNonNull(lines) == 0) {
            this.fontRendererObj.drawString("(\u65e0\u6570\u636e)", 8, 88, textColor);
            return;
        }

        int maxWidth = xSize - 16;
        int y = 72;
        if (selectedIndex >= 0) {
            // 只显示选中方向的那一行
            if (selectedIndex < lines.length && lines[selectedIndex] != null) {
                y = drawWrapped(lines[selectedIndex], y, maxWidth, textColor);
            }
        } else {
            // 显示全部方向
            for (int i = 0; i < lines.length; i++) {
                if (lines[i] == null) continue;
                y = drawWrapped(lines[i], y, maxWidth, textColor);
                if (y > DATA_MAX_Y) break;
            }
        }

        // 命名弹窗：最后绘制，确保覆盖注释区/数据区/按钮等所有图层
        if (naming) {
            drawRect(0, 0, xSize, ySize, 0x90000000); // 半透明遮罩盖住整个 GUI
            drawRect(MODAL_X, MODAL_Y, MODAL_X + MODAL_W, MODAL_Y + MODAL_H, 0xFF3A3A3A);
            drawRect(MODAL_X, MODAL_Y, MODAL_X + MODAL_W, MODAL_Y + 1, 0xFF9A9A9A); // 顶边高亮
            this.fontRendererObj.drawString("\u547d\u540d\u7ed1\u5b9a\u5361", MODAL_X + 8, MODAL_TITLE_Y, 0xFFFFFFFF);
            this.nameField.drawTextBox();
            drawRect(MODAL_CANCEL_X, MODAL_BTN_Y, MODAL_CANCEL_X + MODAL_CANCEL_W, MODAL_BTN_Y + MODAL_BTN_H, 0xFF777777);
            this.fontRendererObj.drawString("\u53d6\u6d88", MODAL_CANCEL_X + 8, MODAL_BTN_Y + 3, 0xFFFFFFFF);
            drawRect(MODAL_CONFIRM_X, MODAL_BTN_Y, MODAL_CONFIRM_X + MODAL_CONFIRM_W, MODAL_BTN_Y + MODAL_BTN_H, 0xFF3B6EA5);
            this.fontRendererObj.drawString("\u786e\u8ba4", MODAL_CONFIRM_X + 10, MODAL_BTN_Y + 3, 0xFFFFFFFF);
        }
    }

    private int drawWrapped(String text, int y, int maxWidth, int color) {
        // 白名单模板输出的多行文本用 \n 分隔，先按行拆开再逐行折行
        for (String line : text.split("\n", -1)) {
            if (line.isEmpty()) continue;
            for (String seg : wrap(line, maxWidth)) {
                this.fontRendererObj.drawString(seg, 8, y, color);
                y += 9;
                if (y > DATA_MAX_Y) break;
            }
            if (y > DATA_MAX_Y) break;
        }
        return y;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        int i = (width - xSize) / 2;
        int j = (height - ySize) / 2;

        // 命名弹窗激活时：只处理弹窗内的输入框与按钮，吞掉其余点击
        if (naming) {
            nameField.mouseClicked(mouseX - i, mouseY - j, mouseButton);
            if (inRect(mouseX, mouseY, i + MODAL_CONFIRM_X, j + MODAL_BTN_Y, MODAL_CONFIRM_W, MODAL_BTN_H)) {
                confirmNaming();
            } else if (inRect(mouseX, mouseY, i + MODAL_CANCEL_X, j + MODAL_BTN_Y, MODAL_CANCEL_W, MODAL_BTN_H)) {
                naming = false;
            }
            return;
        }

        // 注释输入框：点击聚焦
        noteField.mouseClicked(mouseX - i, mouseY - j, mouseButton);

        // "保存注释"按钮：需先选中方向，把方向索引一起发给服务端
        if (inRect(mouseX, mouseY, i + NOTE_BTN_X, j + NOTE_Y, NOTE_BTN_W, NOTE_H)) {
            if (tileSensor != null && selectedIndex >= 0) {
                MinecraftIotMod.network.sendToServer(new PacketSetSensorLabel(tileSensor.xCoord, tileSensor.yCoord, tileSensor.zCoord, selectedIndex, noteField.getText()));
            }
            return;
        }

        // "写入绑定卡"按钮：弹出命名框，确认后再连同名字绑定到服务端
        if (inRect(mouseX, mouseY, i + BIND_X, j + BIND_Y, BIND_W, BIND_H)) {
            if (tileSensor != null) {
                openNaming();
            }
            return;
        }

        // "原始NBT" 切换按钮
        if (inRect(mouseX, mouseY, i + RAW_X, j + RAW_Y, RAW_W, RAW_H)) {
            showRaw = !showRaw;
            return;
        }

        // "显示全部"按钮：打开全屏原始NBT浮层（实时刷新）
        if (inRect(mouseX, mouseY, i + SHOWALL_X, j + SHOWALL_Y, SHOWALL_W, SHOWALL_H)) {
            openFullView();
            return;
        }

        // 方向按钮：点击选中/取消（selectedIndex 记录 VALID_DIRECTIONS 索引），并刷新注释输入框
        for (int d = 0; d < 6; d++) {
            if (inRect(mouseX, mouseY, i + DIR_X[d], j + DIR_Y[d], BTN, BTN)) {
                if (selectedIndex == DIR_IDX[d]) {
                    selectedIndex = -1; // 再次点击解除，恢复显示全部
                } else {
                    selectedIndex = DIR_IDX[d];  // 只显示该方向，其他变灰
                }
                refreshNoteField();
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // 命名弹窗激活时：Enter 确认、Esc 取消，其余输入交给命名输入框（模态吞掉其他按键）
        if (naming) {
            if (keyCode == 1) { // Esc
                naming = false;
                return;
            }
            if (keyCode == 28) { // Enter
                confirmNaming();
                return;
            }
            if (this.nameField.textboxKeyTyped(typedChar, keyCode)) {
                return;
            }
            return;
        }
        // 让注释输入框优先消费键入字符；未聚焦时走默认（Esc/背包）
        if (this.noteField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    /** 打开命名弹窗：预填默认名（按当前选中方向），聚焦输入框 */
    private void openNaming() {
        naming = true;
        String label = dirLabelFor(selectedIndex);
        nameField.setText("\u7ed1\u5b9a\u5361-" + (label.isEmpty() ? "\u5168\u90e8" : label));
        nameField.setFocused(true);
        nameField.setCursorPositionEnd();
    }

    /** 确认命名：把方向 + 自定义名字发给服务端绑定并关闭弹窗 */
    private void confirmNaming() {
        if (tileSensor != null) {
            MinecraftIotMod.network.sendToServer(new PacketBindConnector(tileSensor.xCoord, tileSensor.yCoord, tileSensor.zCoord, selectedIndex, nameField.getText()));
        }
        naming = false;
    }

    /** 由 VALID_DIRECTIONS 索引反查 GUI 方向字母（N/U/W/E/D/S），未命中返回空 */
    private String dirLabelFor(int dirIdx) {
        for (int d = 0; d < 6; d++) {
            if (DIR_IDX[d] == dirIdx) return DIR_LABEL[d];
        }
        return "";
    }

    private boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** 打开全屏原始NBT浮层（实时刷新，滚轮滚动）；传入当前容器以便返回时复用（保留服务端窗口号） */
    private void openFullView() {
        if (tileSensor == null) return;
        Minecraft.getMinecraft().displayGuiScreen(new GuiSensorFullView(tileSensor, (ContainerSensor) this.inventorySlots));
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

    /** 按字体实际像素宽度把一行文本拆成多行 */
    private List<String> wrap(String text, int maxWidth) {
        List<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String trial = cur.toString() + c;
            if (fontRendererObj.getStringWidth(trial) > maxWidth && cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private int countNonNull(String[] arr) {
        int n = 0;
        for (int i = 0; i < arr.length; i++) if (arr[i] != null) n++;
        return n;
    }
}
