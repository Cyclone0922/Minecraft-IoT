package com.cyclone.minecraftiot.gui;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.tileentity.TileSensor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

/**
 * 传感器原始 NBT 全屏浮层。
 * 不暂停游戏刻：每帧读取客户端 TileSensor 的 rawResults（由服务端每秒推送刷新），
 * 支持滚轮滚动查看动态值，点"返回"重新打开传感器 GUI。
 */
public class GuiSensorFullView extends GuiScreen {

    private final TileSensor sensor;
    private int scroll = 0;
    private static final int LEFT = 16, TOP = 30, LINE_H = 9, SCROLL_STEP = 12;
    private static final int[] DIR_NAMES = {2, 1, 4, 5, 0, 3};
    private static final String[] DIR_LABEL = {"N", "U", "W", "E", "D", "S"};

    // 换行缓存：只在数据更新时（服务端每秒推送，rawResults 引用变化）重新排版，
    // 避免每帧对全部方向的原始 NBT 重复换行，饿死主线程导致游戏刻看似停止。
    private String[] lastRawRef;              // 上次排版时的 rawResults 引用
    private final List<List<String>> wrappedCache = new ArrayList<List<String>>();

    public GuiSensorFullView(TileSensor sensor) {
        this.sensor = sensor;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.add(new GuiButton(0, width / 2 - 40, height - 30, 80, 20, "\u8fd4\u56de"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            backToSensorGui();
        }
    }

    private void backToSensorGui() {
        if (sensor != null && sensor.getWorldObj() != null) {
            Minecraft.getMinecraft().thePlayer.openGui(MinecraftIotMod.instance, GuiHandler.GUI_SENSOR,
                    sensor.getWorldObj(), sensor.xCoord, sensor.yCoord, sensor.zCoord);
        } else {
            Minecraft.getMinecraft().displayGuiScreen(null);
        }
    }

    /** 当服务端推送的新数据到达（rawResults 引用变化）时重新排版；否则复用缓存 */
    private void rebuildWrap(int wrapW) {
        wrappedCache.clear();
        if (sensor == null) return;
        String[] raw = sensor.getRawResults();
        if (raw == null) return;
        for (int i = 0; i < raw.length; i++) {
            List<String> dirList = new ArrayList<String>();
            if (raw[i] != null) {
                String line = "\u00a7e[" + DIR_LABEL[i % DIR_LABEL.length] + "]\u00a7r " + raw[i];
                List<String> w = this.fontRendererObj.listFormattedStringToWidth(line, wrapW);
                if (w.isEmpty()) w.add(line);
                dirList.addAll(w);
            }
            wrappedCache.add(dirList);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        drawCenteredString(this.fontRendererObj, "\u4f20\u611f\u5668 \u539f\u59cbNBT \u5168\u5c4f\u67e5\u770b\uff08\u6eda\u8f6e\u6eda\u52a8\uff09",
                width / 2, 12, 0xFFFFFFFF);

        // 只有服务端推送了新数据（引用变化）才重新排版，避免每帧重复换行饿死主线程
        String[] rawNow = sensor != null ? sensor.getRawResults() : null;
        int wrapW = width - LEFT * 2;
        if (rawNow != lastRawRef) {
            lastRawRef = rawNow;
            rebuildWrap(wrapW);
        }

        boolean hasData = false;
        for (List<String> dirList : wrappedCache) if (!dirList.isEmpty()) { hasData = true; break; }
        if (!hasData) {
            drawCenteredString(this.fontRendererObj, "\u65e0\u6570\u636e\uff08\u8bf7\u786e\u4fdd\u4f20\u611f\u5668\u65b9\u5757\u5df2\u52a0\u8f7d\u4e14\u65b9\u5411\u9760\u8fd1\u673a\u5668\uff09",
                    width / 2, height / 2, 0xFF888888);
        }

        // 按滚动偏移渲染已排版好的缓存行（渲染本身很轻量）
        int y = TOP - scroll;
        for (List<String> dirList : wrappedCache) {
            for (String seg : dirList) {
                if (y + LINE_H > TOP && y < height - 40) {
                    this.fontRendererObj.drawString(seg, LEFT, y, 0xFFFFFFFF);
                }
                y += LINE_H;
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dw = Mouse.getEventDWheel();
        if (dw != 0) {
            if (dw > 0) {
                scroll = Math.max(0, scroll - SCROLL_STEP);
            } else {
                scroll += SCROLL_STEP;
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false; // 保持游戏刻运行，方便观察动态值
    }
}
