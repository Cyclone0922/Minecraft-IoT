package com.cyclone.minecraftiot.gui;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.network.PacketRouterBind;
import com.cyclone.minecraftiot.network.PacketRouterListData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;

/**
 * 路由器选择浮窗（不暂停游戏刻）。
 * 当执行器所在维度存在多个路由器时弹出：列出全部路由器（别名/MAC/坐标），
 * 点击某一行即把该路由器绑定到执行器（服务端落存档）并自动进入执行器主 GUI。
 */
public class GuiRouterSelect extends GuiScreen {

    private static GuiRouterSelect pending; // 网络线程收到列表后，主线程打开

    private final int ax, ay, az;
    private final int[] rx, ry, rz;
    private final int[] blocks;
    private final int[] used;
    private final String[] alias;
    private final String[] mac;
    private int hoverIndex = -1;

    private GuiRouterSelect(PacketRouterListData msg) {
        this.ax = msg.ax;
        this.ay = msg.ay;
        this.az = msg.az;
        this.rx = msg.rx;
        this.ry = msg.ry;
        this.rz = msg.rz;
        this.blocks = msg.blocks;
        this.used = msg.used;
        this.alias = msg.alias;
        this.mac = msg.mac;
    }

    /** 网络线程调用：记录待打开界面，由渲染主线程 consume 打开 */
    public static void show(PacketRouterListData msg) {
        pending = new GuiRouterSelect(msg);
    }

    public static GuiRouterSelect consumePending() {
        GuiRouterSelect g = pending;
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
        if (button == 0 && hoverIndex >= 0 && hoverIndex < rx.length) {
            // 点击某行 → 绑定该路由器组，服务端解析+容量校验后自动打开执行器主 GUI
            MinecraftIotMod.network.sendToServer(new PacketRouterBind(ax, ay, az,
                    rx[hoverIndex], ry[hoverIndex], rz[hoverIndex]));
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
        int listH = rx.length * rowH;

        // 标题
        drawCenteredString(fontRendererObj,
                EnumChatFormatting.WHITE + "\u8bf7\u9009\u62e9\u8981\u7ed1\u5b9a\u7684\u8def\u7531\u5668\u7ec4\uff08\u5171 "
                        + rx.length + " \u4e2a\uff09",
                width / 2, top - 20, 0xFFFFFF);
        drawCenteredString(fontRendererObj,
                EnumChatFormatting.GRAY + "\u70b9\u51fb\u4efb\u610f\u4e00\u884c\u7ed1\u5b9a\u5e76\u8fdb\u5165\u6267\u884c\u5668",
                width / 2, top - 8, 0xAAAAAA);

        // 行
        hoverIndex = -1;
        for (int i = 0; i < rx.length; i++) {
            int y0 = top + i * rowH;
            boolean hover = mouseX >= left && mouseX <= left + w && mouseY >= y0 && mouseY <= y0 + rowH;
            if (hover) hoverIndex = i;
            int cap = blocks[i] * 16;
            boolean full = used[i] >= cap;
            // 背景
            int bg = hover ? 0xFF333A4D : (i % 2 == 0 ? 0xFF23252B : 0xFF1C1E24);
            drawRect(left, y0, left + w, y0 + rowH, bg);
            drawRect(left, y0, left + w, y0 + 1, 0xFF444B5C);
            // 第一行：名称 + MAC
            String name = alias != null && alias[i] != null && !alias[i].isEmpty()
                    ? alias[i] : ("\u8def\u7531\u5668\u7ec4 #" + (i + 1));
            fontRendererObj.drawString(EnumChatFormatting.AQUA + name,
                    left + 8, y0 + 5, 0xFFFFFF);
            String macStr = mac != null && mac[i] != null ? mac[i] : "";
            fontRendererObj.drawString(EnumChatFormatting.GRAY + macStr,
                    left + 8 + fontRendererObj.getStringWidth(name) + 12, y0 + 5, 0xAAAAAA);
            // 第二行：坐标 + 容量/已用
            String coord = "(" + rx[i] + ", " + ry[i] + ", " + rz[i] + ")";
            fontRendererObj.drawString(EnumChatFormatting.DARK_GRAY + coord,
                    left + 8, y0 + 17, 0x888888);
            String capStr = "\u5bb9\u91cf " + used[i] + "/" + cap
                    + (blocks[i] > 1 ? " (" + blocks[i] + "\u5757)" : "");
            fontRendererObj.drawString(full
                            ? EnumChatFormatting.RED + capStr + " \u5df2\u6ee1"
                            : EnumChatFormatting.GREEN + capStr,
                    left + w - 8 - fontRendererObj.getStringWidth(capStr) - (full ? fontRendererObj.getStringWidth(" 已满") : 0),
                    y0 + 17, full ? 0xFF5555 : 0x55FF55);
        }

        // 底部提示
        drawCenteredString(fontRendererObj,
                EnumChatFormatting.GRAY + "Esc \u5173\u95ed\uff08\u4e0d\u7ed1\u5b9a\uff09\uff1b\u6ee1\u5bb9\u7ec4\u65e0\u6cd5\u7ed1\u5b9a",
                width / 2, top + listH + 10, 0x888888);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void updateScreen() {
        // 主线程消费：如果主线程此时已切到执行器 GUI（服务端 openGui），本界面会被替换，无需处理
        super.updateScreen();
    }

    /** 主线程钩子：网络线程 show 后由渲染循环调用打开（避免跨线程 displayGuiScreen） */
    public static void pump() {
        GuiRouterSelect g = consumePending();
        if (g != null) {
            Minecraft.getMinecraft().displayGuiScreen(g);
        }
    }
}
