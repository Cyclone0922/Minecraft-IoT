package com.cyclone.sensordisplay.gui;

import com.cyclone.sensordisplay.SensorDisplayMod;
import com.cyclone.sensordisplay.item.ItemConnector;
import com.cyclone.sensordisplay.network.PacketWorkbenchAction;
import com.cyclone.sensordisplay.network.PacketWorkbenchRequest;
import com.cyclone.sensordisplay.tileentity.TileConnectorWorkbench;
import com.cyclone.sensordisplay.util.ConnectorConfig;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * 连接器工作台 GUI：
 *  - 槽0=主卡（所有操作作用于此），槽1=拷贝目标
 *  - 信息区：卡名 / 绑定状态+显示模式 / 目标机器
 *  - 按钮：配置表达式 / 默认友好 / 清空为原始NBT / 清空绑定 / 重命名 / 拷贝配置 / 拷贝全部
 *  - 每 tick 检测槽位变化自动重新请求服务端状态
 */
public class GuiConnectorWorkbench extends GuiContainer {

    private static final ResourceLocation background = new ResourceLocation("sensordisplay", "textures/gui/sensor.png");

    private final TileConnectorWorkbench workbench;
    private final ContainerConnectorWorkbench container;

    // 信息区
    private static final int INFO_NAME_Y = 48;
    private static final int INFO_BIND_Y = 60;
    private static final int INFO_TARGET_Y = 72;
    private static final int STATUS_Y = 85;

    // 按钮（两列）
    private static final int BTN_X1 = 8, BTN_X2 = 90, BTN_W = 80, BTN_H = 11;
    private static final int[] BTN_Y1 = {86, 99, 112, 125}; // 列1：配置/默认/清NBT/清绑定
    private static final int[] BTN_Y2 = {86, 99, 112};      // 列2：重命名/拷贝配置/拷贝全部

    // 命名弹窗
    private static final int MODAL_X = 16, MODAL_Y = 8, MODAL_W = 144, MODAL_H = 50;
    private static final int MODAL_FIELD_X = MODAL_X + 8, MODAL_FIELD_Y = MODAL_Y + 18;
    private static final int MODAL_FIELD_W = 128, MODAL_FIELD_H = 14;
    private static final int MODAL_BTN_Y = MODAL_Y + MODAL_H - 16, MODAL_BTN_H = 14;
    private static final int MODAL_CANCEL_X = MODAL_X + MODAL_W - 88, MODAL_CANCEL_W = 36;
    private static final int MODAL_CONFIRM_X = MODAL_X + MODAL_W - 48, MODAL_CONFIRM_W = 40;

    private boolean naming = false;
    private GuiTextField nameField;
    private String status = "";
    private int statusTimer = 0;

    private ItemStack prevCardA = null;
    private ItemStack prevCardB = null;
    private int refreshTimer = 0;

    public GuiConnectorWorkbench(Container container, TileConnectorWorkbench workbench) {
        super(container);
        this.container = (ContainerConnectorWorkbench) container;
        this.workbench = workbench;
        this.xSize = 176;
        this.ySize = 216;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.nameField = new GuiTextField(this.fontRendererObj, MODAL_FIELD_X, MODAL_FIELD_Y, MODAL_FIELD_W, MODAL_FIELD_H);
        this.nameField.setMaxStringLength(60);
        requestData();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        // 槽0/槽1 任一变化都重新请求服务端状态（拷贝按钮可用性依赖两卡同机判断）
        Slot sa = (Slot) container.inventorySlots.get(0);
        Slot sb = (Slot) container.inventorySlots.get(1);
        ItemStack cardA = (sa != null) ? sa.getStack() : null;
        ItemStack cardB = (sb != null) ? sb.getStack() : null;
        if (cardA != prevCardA || cardB != prevCardB) {
            prevCardA = cardA;
            prevCardB = cardB;
            requestData();
        }
        // 每 20 tick 定时刷新一次，保持绑定/同机状态新鲜
        if (--refreshTimer <= 0) {
            refreshTimer = 20;
            requestData();
        }
        if (statusTimer > 0) {
            statusTimer--;
            if (statusTimer == 0) status = "";
        }
    }

    private void requestData() {
        SensorDisplayMod.network.sendToServer(new PacketWorkbenchRequest(
                workbench.xCoord, workbench.yCoord, workbench.zCoord));
    }

    private void sendAction(int action) {
        sendAction(action, "");
    }

    private void sendAction(int action, String text) {
        SensorDisplayMod.network.sendToServer(new PacketWorkbenchAction(
                workbench.xCoord, workbench.yCoord, workbench.zCoord, action, text));
        status = "\u5df2\u53d1\u9001\u64cd\u4f5c...";
        statusTimer = 100;
        requestData();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawDefaultBackground();
        this.mc.getTextureManager().bindTexture(background);
        int i = (width - xSize) / 2;
        int j = (height - ySize) / 2;
        drawTexturedModalRect(i, j, 0, 0, xSize, ySize);

        // 两个连接器槽位背景
        for (int[] p : new int[][]{{8, 8}, {8, 26}}) {
            int sx = i + p[0], sy = j + p[1];
            drawRect(sx, sy, sx + 18, sy + 18, 0xFF373737);
            drawRect(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF8B8B8B);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int textColor = 0xFF333333;
        int dim = 0xFF777777;

        this.fontRendererObj.drawString("\u8fde\u63a5\u5668\u5de5\u4f5c\u53f0", 30, 12, textColor);
        this.fontRendererObj.drawString("\u4e3b\u5361", 28, 13, dim);
        this.fontRendererObj.drawString("\u62f7\u8d1d\u76ee\u6807", 28, 31, dim);

        boolean hasCard = workbench.clientHasCard();
        // 卡名
        this.fontRendererObj.drawString("\u5361\u540d: " + (hasCard ? (workbench.clientName().isEmpty() ? "\uff08\u672a\u547d\u540d\uff09" : workbench.clientName()) : "\uff08\u65e0\u5361\uff09"),
                INFO_NAME_X(), INFO_NAME_Y, hasCard ? textColor : dim);
        // 绑定 + 模式
        String bind = hasCard ? (workbench.clientHasBinding() ? "\u5df2\u7ed1\u5b9a" : "\u672a\u7ed1\u5b9a") : "\u2014";
        String mode = modeLabel(hasCard ? workbench.clientMode() : ConnectorConfig.MODE_RAW);
        this.fontRendererObj.drawString("\u7ed1\u5b9a: " + bind + "    \u6a21\u5f0f: " + mode,
                INFO_BIND_X(), INFO_BIND_Y, hasCard ? textColor : dim);
        // 目标机器
        String target = (hasCard && workbench.clientTargetClass() != null && !workbench.clientTargetClass().isEmpty())
                ? shortClass(workbench.clientTargetClass()) : "\uff08\u65e0\uff09";
        this.fontRendererObj.drawString("\u76ee\u6807: " + target, INFO_TARGET_X(), INFO_TARGET_Y, textColor);

        // 状态提示
        if (!status.isEmpty()) {
            this.fontRendererObj.drawString(status, 8, STATUS_Y, 0xFF3B6EA5);
        }

        // 按钮列1：配置表达式 / 默认友好 / 清空为原始NBT / 清空绑定
        drawButton(BTN_X1, BTN_Y1[0], "\u914d\u7f6e\u8868\u8fbe\u5f0f", hasCard);
        drawButton(BTN_X1, BTN_Y1[1], "\u9ed8\u8ba4\u53cb\u597d\u65b9\u6848", hasCard);
        drawButton(BTN_X1, BTN_Y1[2], "\u6e05\u7a7a\u4e3a\u539f\u59cbNBT", hasCard);
        drawButton(BTN_X1, BTN_Y1[3], "\u6e05\u7a7a\u7ed1\u5b9a", hasCard);
        // 按钮列2：重命名 / 拷贝配置 / 拷贝全部。
        // 拷贝全部：两槽都有连接器即可点（直接覆盖目标卡，不要求同机）；拷贝配置：两卡绑定同种机器时可点。
        boolean hasB = hasCardB();
        boolean copyable = hasCard && hasB && workbench.clientSameMachine();
        drawButton(BTN_X2, BTN_Y2[0], "\u91cd\u547d\u540d", hasCard);
        drawButton(BTN_X2, BTN_Y2[1], "\u62f7\u8d1d\u914d\u7f6e", copyable);
        drawButton(BTN_X2, BTN_Y2[2], "\u62f7\u8d1d\u5168\u90e8", hasCard && hasB);

        // 命名弹窗
        if (naming) {
            drawRect(0, 0, xSize, ySize, 0x90000000);
            drawRect(MODAL_X, MODAL_Y, MODAL_X + MODAL_W, MODAL_Y + MODAL_H, 0xFF3A3A3A);
            drawRect(MODAL_X, MODAL_Y, MODAL_X + MODAL_W, MODAL_Y + 1, 0xFF9A9A9A);
            this.fontRendererObj.drawString("\u91cd\u547d\u540d\u8fde\u63a5\u5668", MODAL_X + 8, MODAL_Y + 5, 0xFFFFFFFF);
            this.nameField.drawTextBox();
            drawRect(MODAL_CANCEL_X, MODAL_BTN_Y, MODAL_CANCEL_X + MODAL_CANCEL_W, MODAL_BTN_Y + MODAL_BTN_H, 0xFF777777);
            this.fontRendererObj.drawString("\u53d6\u6d88", MODAL_CANCEL_X + 8, MODAL_BTN_Y + 3, 0xFFFFFFFF);
            drawRect(MODAL_CONFIRM_X, MODAL_BTN_Y, MODAL_CONFIRM_X + MODAL_CONFIRM_W, MODAL_BTN_Y + MODAL_BTN_H, 0xFF3B6EA5);
            this.fontRendererObj.drawString("\u786e\u8ba4", MODAL_CONFIRM_X + 10, MODAL_BTN_Y + 3, 0xFFFFFFFF);
        }
    }

    private int INFO_NAME_X() { return 8; }
    private int INFO_BIND_X() { return 8; }
    private int INFO_TARGET_X() { return 8; }

    /** 槽1（拷贝目标）是否插有连接器（客户端 Container 同步的槽位） */
    private boolean hasCardB() {
        Slot s = (Slot) container.inventorySlots.get(1);
        ItemStack b = (s != null) ? s.getStack() : null;
        return b != null && b.getItem() instanceof ItemConnector;
    }

    private void drawButton(int x, int y, String label, boolean enabled) {
        drawRect(x, y, x + BTN_W, y + BTN_H, enabled ? 0xFF3B6EA5 : 0xFF777777);
        int tw = this.fontRendererObj.getStringWidth(label);
        this.fontRendererObj.drawString(label, x + (BTN_W - tw) / 2, y + 2, 0xFFFFFFFF);
    }

    private String modeLabel(int mode) {
        switch (mode) {
            case ConnectorConfig.MODE_DEFAULT: return "\u9ed8\u8ba4\u53cb\u597d";
            case ConnectorConfig.MODE_CUSTOM: return "\u81ea\u5b9a\u4e49\u8868\u8fbe\u5f0f";
            default: return "\u539f\u59cbNBT";
        }
    }

    private String shortClass(String cls) {
        int i = cls.lastIndexOf('.');
        String s = i >= 0 ? cls.substring(i + 1) : cls;
        return s.length() > 22 ? s.substring(0, 22) + "..." : s;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        int i = (width - xSize) / 2;
        int j = (height - ySize) / 2;

        if (naming) {
            nameField.mouseClicked(mouseX - i, mouseY - j, mouseButton);
            if (inRect(mouseX, mouseY, i + MODAL_CONFIRM_X, j + MODAL_BTN_Y, MODAL_CONFIRM_W, MODAL_BTN_H)) {
                confirmRename();
            } else if (inRect(mouseX, mouseY, i + MODAL_CANCEL_X, j + MODAL_BTN_Y, MODAL_CANCEL_W, MODAL_BTN_H)) {
                naming = false;
            }
            return;
        }

        boolean hasCard = workbench.clientHasCard();
        // 列1
        if (inRect(mouseX, mouseY, i + BTN_X1, j + BTN_Y1[0], BTN_W, BTN_H)) { openConfigEditor(); return; }
        if (inRect(mouseX, mouseY, i + BTN_X1, j + BTN_Y1[1], BTN_W, BTN_H)) { if (hasCard) sendAction(TileConnectorWorkbench.ACTION_SET_DEFAULT); return; }
        if (inRect(mouseX, mouseY, i + BTN_X1, j + BTN_Y1[2], BTN_W, BTN_H)) { if (hasCard) sendAction(TileConnectorWorkbench.ACTION_CLEAR_CONFIG); return; }
        if (inRect(mouseX, mouseY, i + BTN_X1, j + BTN_Y1[3], BTN_W, BTN_H)) { if (hasCard) sendAction(TileConnectorWorkbench.ACTION_CLEAR_BINDING); return; }
        // 列2
        if (inRect(mouseX, mouseY, i + BTN_X2, j + BTN_Y2[0], BTN_W, BTN_H)) { if (hasCard) openRename(); return; }
        if (inRect(mouseX, mouseY, i + BTN_X2, j + BTN_Y2[1], BTN_W, BTN_H)) { if (hasCard && hasCardB() && workbench.clientSameMachine()) sendAction(TileConnectorWorkbench.ACTION_COPY_CONFIG); return; }
        if (inRect(mouseX, mouseY, i + BTN_X2, j + BTN_Y2[2], BTN_W, BTN_H)) { if (hasCard && hasCardB()) sendAction(TileConnectorWorkbench.ACTION_COPY_ALL); return; }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (naming) {
            if (keyCode == 1) { naming = false; return; }
            if (keyCode == 28) { confirmRename(); return; }
            if (this.nameField.textboxKeyTyped(typedChar, keyCode)) return;
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    /** 打开表达式编辑器：需要服务端下发的目标 NBT */
    private void openConfigEditor() {
        if (!workbench.clientHasCard()) {
            status = "\u8bf7\u5148\u63d2\u5165\u8fde\u63a5\u5668";
            statusTimer = 100;
            return;
        }
        if (workbench.clientNbt() == null) {
            status = "\u65e0\u76ee\u6807\u6570\u636e\uff08\u9700\u5148\u7ed1\u5b9a\u65b9\u5411\uff09";
            statusTimer = 100;
            return;
        }
        net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                new GuiCardConfigEditor(workbench, workbench.clientNbt(), workbench.clientTargetClass(), workbench.clientExpr()));
    }

    /** 打开重命名弹窗：预填默认命名方案（已绑定→绑定卡，否则连接器） */
    private void openRename() {
        naming = true;
        String cur = workbench.clientName();
        String prefill = cur.isEmpty()
                ? (workbench.clientHasBinding() ? "\u7ed1\u5b9a\u5361" : "\u8fde\u63a5\u5668")
                : cur;
        nameField.setText(prefill);
        nameField.setFocused(true);
        nameField.setCursorPositionEnd();
    }

    private void confirmRename() {
        sendAction(TileConnectorWorkbench.ACTION_RENAME, nameField.getText());
        naming = false;
    }

    private boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
