package com.cyclone.minecraftiot.gui;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.network.PacketRouterCharge;
import com.cyclone.minecraftiot.tileentity.TileRouter;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.lwjgl.input.Keyboard;

/**
 * 路由器 GUI（0.5.1 测试用）：
 *  - 显示 IE 存量/容量、网络物品池总量
 *  - 输入框 + "充电"按钮：直接给路由器加 IE（测试阶段）
 */
public class GuiRouter extends GuiContainer {
    private final TileRouter router;
    private net.minecraft.client.gui.GuiTextField chargeField;
    private GuiButton chargeBtn;

    public GuiRouter(ContainerRouter container, TileRouter router) {
        super(container);
        this.router = router;
        this.xSize = 176;
        this.ySize = 166;
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        int cx = (width - xSize) / 2;
        int cy = (height - ySize) / 2;
        chargeField = new GuiTextField(fontRendererObj, cx + 10, cy + 70, 80, 12);
        chargeField.setMaxStringLength(10);
        chargeField.setText("1000");
        chargeBtn = new GuiButton(0, cx + 95, cy + 68, 70, 16, "充电");
        buttonList.add(chargeBtn);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton btn) {
        if (btn == chargeBtn) {
            try {
                double amt = Double.parseDouble(chargeField.getText().trim());
                if (amt > 0) {
                    MinecraftIotMod.network.sendToServer(new PacketRouterCharge(amt));
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (chargeField.textboxKeyTyped(c, key)) return;
        super.keyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) {
        super.mouseClicked(mx, my, btn);
        chargeField.mouseClicked(mx, my, btn);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        chargeField.updateCursorCounter();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mx, int my) {
        int cx = (width - xSize) / 2;
        int cy = (height - ySize) / 2;
        // 深色背景
        drawRect(cx, cy, cx + xSize, cy + ySize, 0xFF2A2A2A);
        drawRect(cx, cy, cx + xSize, cy + 14, 0xFF3A3A3A); // 标题栏
        drawString(fontRendererObj, "Router", cx + 6, cy + 4, 0xFFFFFF);

        int ie = (int) router.getEnergy().getIe();
        int ieCap = (int) router.getEnergy().getIeCapacity();
        int pool = router.getPool().getTotal();
        drawString(fontRendererObj, "IE: " + ie + " / " + ieCap, cx + 10, cy + 24, 0xFFFFFF);
        drawString(fontRendererObj, "Pool: " + pool, cx + 10, cy + 38, 0xFFFFFF);
        drawString(fontRendererObj, "Charge IE:", cx + 10, cy + 58, 0xAAAAAA);
        if (chargeField != null) {
            // 输入框白底
            drawRect(chargeField.xPosition - 1, chargeField.yPosition - 1,
                    chargeField.xPosition + chargeField.width + 1, chargeField.yPosition + chargeField.height + 1, 0xFF000000);
            chargeField.drawTextBox();
        }
    }
}
