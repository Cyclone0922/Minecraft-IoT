package com.cyclone.minecraftiot.gui;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.network.PacketActuatorBind;
import com.cyclone.minecraftiot.network.PacketActuatorListData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;

/**
 * 远程执行器绑定浮窗（不暂停游戏刻）。
 * 列出同网络内全部执行器，点击某行把它设为本执行器的物品源（insert() 从其 takeBuffer 取物）。
 * Esc 关闭（不绑定）。
 */
public class GuiActuatorSelect extends GuiScreen {

    private static GuiActuatorSelect pending;

    private final int ax, ay, az;
    private final int[] tx, ty, tz;
    private final String[] skillName;
    private final int[] takeBufSize;
    private final boolean[] itemTransfer;
    private int hoverIndex = -1;

    private GuiActuatorSelect(PacketActuatorListData msg) {
        this.ax = msg.ax;
        this.ay = msg.ay;
        this.az = msg.az;
        this.tx = msg.tx;
        this.ty = msg.ty;
        this.tz = msg.tz;
        this.skillName = msg.skillName;
        this.takeBufSize = msg.takeBufSize;
        this.itemTransfer = msg.itemTransfer;
    }

    public static void show(PacketActuatorListData msg) {
        pending = new GuiActuatorSelect(msg);
    }

    private static GuiActuatorSelect consumePending() {
        GuiActuatorSelect g = pending;
        pending = null;
        return g;
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && hoverIndex >= 0 && hoverIndex < tx.length) {
            MinecraftIotMod.network.sendToServer(new PacketActuatorBind(ax, ay, az,
                    tx[hoverIndex], ty[hoverIndex], tz[hoverIndex]));
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int w = 280;
        int left = (width - w) / 2;
        int top = 60;
        int rowH = 34;
        int listH = Math.max(1, tx.length) * rowH;

        drawCenteredString(fontRendererObj,
                EnumChatFormatting.WHITE + "选择物品源执行器（共 " + tx.length + " 个）",
                width / 2, top - 20, 0xFFFFFF);
        drawCenteredString(fontRendererObj,
                EnumChatFormatting.GRAY + "点击一行：本执行器 insert() 将从其 takeBuffer 取物",
                width / 2, top - 8, 0xAAAAAA);

        if (tx.length == 0) {
            drawCenteredString(fontRendererObj,
                    EnumChatFormatting.RED + "同网络内没有其他执行器",
                    width / 2, top + 20, 0xFFFFFF);
        }

        hoverIndex = -1;
        for (int i = 0; i < tx.length; i++) {
            int y0 = top + i * rowH;
            boolean hover = mouseX >= left && mouseX <= left + w && mouseY >= y0 && mouseY <= y0 + rowH;
            if (hover) hoverIndex = i;
            int bg = hover ? 0xFF333A4D : (i % 2 == 0 ? 0xFF23252B : 0xFF1C1E24);
            drawRect(left, y0, left + w, y0 + rowH, bg);
            drawRect(left, y0, left + w, y0 + 1, 0xFF444B5C);
            // 第一行：技能名 + takeBuffer 数量
            String name = skillName[i] != null && !skillName[i].isEmpty()
                    ? skillName[i] : "无技能";
            fontRendererObj.drawString(EnumChatFormatting.AQUA + name,
                    left + 8, y0 + 5, 0xFFFFFF);
            String buf = "缓存 " + takeBufSize[i];
            fontRendererObj.drawString(EnumChatFormatting.YELLOW + buf,
                    left + w - 8 - fontRendererObj.getStringWidth(buf), y0 + 5, 0xFFFF55);
            // 第二行：坐标 + 是否有物品转移插件
            String coord = "(" + tx[i] + ", " + ty[i] + ", " + tz[i] + ")";
            fontRendererObj.drawString(EnumChatFormatting.DARK_GRAY + coord,
                    left + 8, y0 + 17, 0x888888);
            String tip = itemTransfer[i] ? "✓ 物品转移" : "✗ 无物品转移";
            fontRendererObj.drawString(itemTransfer[i]
                            ? EnumChatFormatting.GREEN + tip
                            : EnumChatFormatting.RED + tip,
                    left + w - 8 - fontRendererObj.getStringWidth(tip), y0 + 17,
                    itemTransfer[i] ? 0x55FF55 : 0xFF5555);
        }

        drawCenteredString(fontRendererObj,
                EnumChatFormatting.GRAY + "Esc 关闭（不绑定）",
                width / 2, top + listH + 10, 0x888888);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** 主线程钩子：网络线程 show 后由渲染循环调用打开 */
    public static void pump() {
        GuiActuatorSelect g = consumePending();
        if (g != null) {
            Minecraft.getMinecraft().displayGuiScreen(g);
        }
    }
}
