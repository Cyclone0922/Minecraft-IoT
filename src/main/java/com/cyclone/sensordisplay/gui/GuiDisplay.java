package com.cyclone.sensordisplay.gui;

import com.cyclone.sensordisplay.SensorDisplayMod;
import com.cyclone.sensordisplay.network.PacketOpenRemoteGui;
import com.cyclone.sensordisplay.network.PacketSetDisplayFace;
import com.cyclone.sensordisplay.network.PacketSetDisplaySettings;
import com.cyclone.sensordisplay.tileentity.TileDisplay;
import com.cyclone.sensordisplay.util.DisplayGroupUtil;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 显示器 GUI（操作主控方块数据）：两个选项卡
 * - 设置：6 卡槽、显示面、字号、对齐、文字朝向、展示模式(轮流/拼接/分栏)、溢出(截断/分页/滚动)、分栏数
 * - 卡片：展示所有已插入绑定卡套用表达式/白名单后的信息
 * 任意成员方块右键打开的都是主控的 GUI。
 */
public class GuiDisplay extends GuiContainer {

    private TileDisplay tileDisplay; // 主控

    // 选项卡
    private static final int TAB_Y = 6, TAB_H = 14;
    private static final int TAB_SET_X = 8, TAB_CARD_X = 54, TAB_W = 44;
    private int thisTab = 0; // 0=设置 1=卡片

    // 显示面按钮（原版面索引：0=下 1=上 2=北 3=南 4=西 5=东）
    private static final String[] DIR_LABEL = {"U", "W", "N", "S", "E", "D"};
    private static final int[] DIR_FACE = {1, 4, 2, 3, 5, 0};
    private static final int FACE_Y = 42, FACE_BTN_X0 = 28, FACE_BTN_W = 18;

    // 字号 / 对齐
    private static final int FS_Y = 62;
    private static final int FSLBL_X = 8, FSMINUS_X = 34, FSMINUS_W = 16, FSVAL_X = 52, FSVAL_W = 24, FSPLUS_X = 78, FSPLUS_W = 16;
    private static final int ALN_LBL_X = 100, ALN_BTN_X0 = 126, ALN_BTN_W = 15;
    private static final String[] ALN_LABEL = {"\u5de6", "\u4e2d", "\u53f3"};
    private static final float MIN_FS = 0.5f, MAX_FS = 2.0f, FS_STEP = 0.25f;

    // 朝向 / 分栏
    private static final int ROW3_Y = 82;
    private static final int ROT_LBL_X = 8, ROT_BTN_X = 34, ROT_BTN_W = 24;
    private static final int COL_LBL_X = 70, COLMINUS_X = 94, COLMINUS_W = 16, COLVAL_X = 112, COLVAL_W = 24, COLPLUS_X = 138, COLPLUS_W = 16;

    // 展示模式 / 溢出
    private static final int ROW4_Y = 102, ROW5_Y = 122;
    private static final int MODE_LBL_X = 8, MODE_BTN_X0 = 34, MODE_BTN_W = 34;
    private static final int OVF_LBL_X = 8, OVF_BTN_X0 = 34, OVF_BTN_W = 34;
    private static final String[] MODE_LABEL = {"\u8f6e\u6d41", "\u62fc\u63a5", "\u5206\u680f"}; // 轮流/拼接/分栏
    private static final String[] OVF_LABEL = {"\u622a\u65ad", "\u5206\u9875", "\u6eda\u52a8"};    // 截断/分页/滚动
    private static final String[] ROT_LABEL = {"\u2191", "\u2192", "\u2193", "\u2190"};           // 上/右/下/左

    private static final int BTN_H = 16;

    // 卡片选项卡：每张卡的"打开"按钮矩形 {slot, x, y, w, h}（随绘制重建）
    private static final int OPEN_BTN_X = 150, OPEN_BTN_W = 52, OPEN_BTN_H = 9;
    private final java.util.List<int[]> cardButtons = new java.util.ArrayList<int[]>();

    // 本地镜像
    private float guiFontSize = 1.0f;
    private int guiAlign = 0;
    private int guiTextRot = 0;
    private int guiMode = 0;
    private int guiOverflow = 0;
    private int guiColumns = 1;
    private int currentFace;

    public GuiDisplay(Container container, TileDisplay tileDisplay) {
        super(container);
        this.tileDisplay = tileDisplay;
        this.xSize = 208;
        this.ySize = 236;
        if (tileDisplay != null) {
            this.guiFontSize = tileDisplay.getFontSize();
            this.guiAlign = tileDisplay.getAlignMode();
            this.guiTextRot = tileDisplay.getTextRot();
            this.guiMode = tileDisplay.getDisplayMode();
            this.guiOverflow = tileDisplay.getOverflowMode();
            this.guiColumns = tileDisplay.getColumns();
            this.currentFace = tileDisplay.getBlockMetadata();
            if (currentFace < 0 || currentFace > 5) currentFace = 3;
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawDefaultBackground();
        int i = (width - xSize) / 2, j = (height - ySize) / 2;
        // 浅灰面板（接近原版容器观感）
        drawRect(i, j, i + xSize, j + ySize, 0xFFC6C6C6);
        drawRect(i + 1, j + 1, i + xSize - 1, j + ySize - 1, 0xFFD6D6D6);
        // 选项卡按钮
        drawTab(i + TAB_SET_X, j + TAB_Y, thisTab == 0);
        drawTab(i + TAB_CARD_X, j + TAB_Y, thisTab == 1);
    }

    private void drawTab(int x, int y, boolean active) {
        drawRect(x, y, x + TAB_W, y + TAB_H, active ? 0xFF2E8B57 : 0xFF3B6EA5);
        drawRect(x + 1, y + 1, x + TAB_W - 1, y + TAB_H - 1, active ? 0xFF2E8B57 : 0xFF3B6EA5);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // 选项卡文字
        this.fontRendererObj.drawString("\u8bbe\u7f6e", TAB_SET_X + 18, TAB_Y + 3, 0xFFFFFFFF); // 设置
        this.fontRendererObj.drawString("\u5361\u7247", TAB_CARD_X + 18, TAB_Y + 3, 0xFFFFFFFF); // 卡片

        if (thisTab == 0) {
            drawSettingsTab();
        } else {
            drawCardsTab();
        }
    }

    private void drawSettingsTab() {
        int text = 0xFF333333;
        // 绑定卡标题
        this.fontRendererObj.drawString("\u7ed1\u5b9a\u5361", 8, 25, text); // 绑定卡
        // 显示面
        this.fontRendererObj.drawString("\u9762:", FACE_BTN_X0 - 18, FACE_Y + 4, text); // 面
        for (int d = 0; d < 6; d++) {
            boolean isCur = (currentFace == DIR_FACE[d]);
            btn(FACE_BTN_X0 + d * FACE_BTN_W, FACE_Y, FACE_BTN_W, BTN_H, isCur ? 0xFF2E8B57 : 0xFF3B6EA5, DIR_LABEL[d]);
        }
        // 字号
        this.fontRendererObj.drawString("\u5b57\u53f7:", FSLBL_X, FS_Y + 4, text); // 字号
        btn(FSMINUS_X, FS_Y, FSMINUS_W, BTN_H, 0xFF3B6EA5, "-");
        this.fontRendererObj.drawString("x" + formatFs(guiFontSize), FSVAL_X, FS_Y + 4, text);
        btn(FSPLUS_X, FS_Y, FSPLUS_W, BTN_H, 0xFF3B6EA5, "+");
        // 对齐
        this.fontRendererObj.drawString("\u5bf9\u9f50:", ALN_LBL_X, FS_Y + 4, text); // 对齐
        for (int a = 0; a < 3; a++) {
            boolean sel = (guiAlign == a);
            btn(ALN_BTN_X0 + a * ALN_BTN_W, FS_Y, ALN_BTN_W, BTN_H, sel ? 0xFF2E8B57 : 0xFF3B6EA5, ALN_LABEL[a]);
        }
        // 朝向（单按钮循环切换）
        this.fontRendererObj.drawString("\u671d\u5411:", ROT_LBL_X, ROW3_Y + 4, text); // 朝向
        btn(ROT_BTN_X, ROW3_Y, ROT_BTN_W, BTN_H, 0xFF3B6EA5, ROT_LABEL[guiTextRot]);
        // 分栏数
        this.fontRendererObj.drawString("\u5206\u680f:", COL_LBL_X, ROW3_Y + 4, text); // 分栏
        btn(COLMINUS_X, ROW3_Y, COLMINUS_W, BTN_H, 0xFF3B6EA5, "-");
        this.fontRendererObj.drawString("" + guiColumns, COLVAL_X, ROW3_Y + 4, text);
        btn(COLPLUS_X, ROW3_Y, COLPLUS_W, BTN_H, 0xFF3B6EA5, "+");
        // 展示模式
        this.fontRendererObj.drawString("\u5c55\u793a:", MODE_LBL_X, ROW4_Y + 4, text); // 展示
        for (int m = 0; m < 3; m++) {
            boolean sel = (guiMode == m);
            btn(MODE_BTN_X0 + m * MODE_BTN_W, ROW4_Y, MODE_BTN_W, BTN_H, sel ? 0xFF2E8B57 : 0xFF3B6EA5, MODE_LABEL[m]);
        }
        // 溢出行为
        this.fontRendererObj.drawString("\u6ea2\u51fa:", OVF_LBL_X, ROW5_Y + 4, text); // 溢出
        for (int o = 0; o < 3; o++) {
            boolean sel = (guiOverflow == o);
            btn(OVF_BTN_X0 + o * OVF_BTN_W, ROW5_Y, OVF_BTN_W, BTN_H, sel ? 0xFF2E8B57 : 0xFF3B6EA5, OVF_LABEL[o]);
        }
    }

    private void drawCardsTab() {
        int text = 0xFF333333;
        cardButtons.clear();
        int y = 44; // 避开上方 6 个卡槽(22..40)
        World world = tileDisplay != null ? tileDisplay.getWorldObj() : null;
        for (int s = 0; s < TileDisplay.MAX_CARDS; s++) {
            if (y > 146) break;
            ItemStack card = tileDisplay != null ? tileDisplay.getStackInSlot(s) : null;
            String name = cardName(card);
            // 卡名截断，避免与右侧"打开"按钮重叠
            String header = "\u5361" + (s + 1) + ": " + name; // 卡x:
            this.fontRendererObj.drawString(clipTo(header, OPEN_BTN_X - 12), 8, y, 0xFF000000);
            if (cardHasBinding(card)) {
                cardButtons.add(new int[]{s, OPEN_BTN_X, y, OPEN_BTN_W, OPEN_BTN_H});
                btn(OPEN_BTN_X, y, OPEN_BTN_W, OPEN_BTN_H, 0xFF3B6EA5, "\u6253\u5f00"); // 打开
            }
            y += 9;
            if (card != null && world != null) {
                String[] page = tileDisplay.getPages()[s];
                if (page == null || page.length == 0) {
                    this.fontRendererObj.drawString("(\u65e0\u6570\u636e)", 8, y, 0xFF666666); // (无数据)
                    y += 9;
                } else {
                    int maxWidth = xSize - 16;
                    for (String l : page) {
                        if (y > 146) break;
                        if (l == null || l.isEmpty()) continue;
                        for (String seg : wrap(l, maxWidth)) {
                            if (y > 146) break;
                            this.fontRendererObj.drawString(seg, 8, y, 0xFF555555);
                            y += 9;
                        }
                    }
                }
            } else {
                this.fontRendererObj.drawString("(\u7a7a)", 8, y, 0xFF777777); // (空)
                y += 9;
            }
            y += 3;
        }
    }

    private boolean cardHasBinding(ItemStack card) {
        return card != null && card.getTagCompound() != null && card.getTagCompound().hasKey("sensorX");
    }

    /** 按像素宽度截断字符串，超长加 "..." */
    private String clipTo(String s, int maxWidth) {
        if (fontRendererObj.getStringWidth(s) <= maxWidth) return s;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            String t = sb.toString() + s.charAt(i);
            if (fontRendererObj.getStringWidth(t) > maxWidth - 6) {
                return sb.toString() + "...";
            }
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    // ============ 交互 ============

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        int i = (width - xSize) / 2, j = (height - ySize) / 2;
        // 选项卡切换
        if (inRect(mouseX, mouseY, i + TAB_SET_X, j + TAB_Y, TAB_W, TAB_H)) { thisTab = 0; return; }
        if (inRect(mouseX, mouseY, i + TAB_CARD_X, j + TAB_Y, TAB_W, TAB_H)) { thisTab = 1; return; }
        if (thisTab == 0) {
            // 显示面（整组改朝向）
            for (int d = 0; d < 6; d++) {
                if (inRect(mouseX, mouseY, i + FACE_BTN_X0 + d * FACE_BTN_W, j + FACE_Y, FACE_BTN_W, BTN_H)) {
                    currentFace = DIR_FACE[d];
                    if (tileDisplay != null) {
                        SensorDisplayMod.network.sendToServer(new PacketSetDisplayFace(
                                tileDisplay.xCoord, tileDisplay.yCoord, tileDisplay.zCoord, currentFace));
                    }
                    return;
                }
            }
            // 字号
            if (inRect(mouseX, mouseY, i + FSMINUS_X, j + FS_Y, FSMINUS_W, BTN_H)) {
                guiFontSize = Math.max(MIN_FS, guiFontSize - FS_STEP); applySettings(); return;
            }
            if (inRect(mouseX, mouseY, i + FSPLUS_X, j + FS_Y, FSPLUS_W, BTN_H)) {
                guiFontSize = Math.min(MAX_FS, guiFontSize + FS_STEP); applySettings(); return;
            }
            // 对齐
            for (int a = 0; a < 3; a++) {
                if (inRect(mouseX, mouseY, i + ALN_BTN_X0 + a * ALN_BTN_W, j + FS_Y, ALN_BTN_W, BTN_H)) {
                    guiAlign = a; applySettings(); return;
                }
            }
            // 朝向（循环）
            if (inRect(mouseX, mouseY, i + ROT_BTN_X, j + ROW3_Y, ROT_BTN_W, BTN_H)) {
                guiTextRot = (guiTextRot + 1) % 4; applySettings(); return;
            }
            // 分栏数
            int maxCols = maxColsFor();
            if (inRect(mouseX, mouseY, i + COLMINUS_X, j + ROW3_Y, COLMINUS_W, BTN_H)) {
                guiColumns = Math.max(1, guiColumns - 1); applySettings(); return;
            }
            if (inRect(mouseX, mouseY, i + COLPLUS_X, j + ROW3_Y, COLPLUS_W, BTN_H)) {
                guiColumns = Math.min(maxCols, guiColumns + 1); applySettings(); return;
            }
            // 展示模式
            for (int m = 0; m < 3; m++) {
                if (inRect(mouseX, mouseY, i + MODE_BTN_X0 + m * MODE_BTN_W, j + ROW4_Y, MODE_BTN_W, BTN_H)) {
                    guiMode = m;
                    if (m == 2 && guiColumns < 2) guiColumns = 2; // 分栏需至少 2 栏
                    applySettings(); return;
                }
            }
            // 溢出
            for (int o = 0; o < 3; o++) {
                if (inRect(mouseX, mouseY, i + OVF_BTN_X0 + o * OVF_BTN_W, j + ROW5_Y, OVF_BTN_W, BTN_H)) {
                    guiOverflow = o; applySettings(); return;
                }
            }
        } else {
            // 卡片选项卡：点击某张卡的"打开"按钮 -> 远程打开目标机器 GUI
            for (int[] b : cardButtons) {
                if (inRect(mouseX, mouseY, i + b[1], j + b[2], b[3], b[4])) {
                    if (tileDisplay != null) {
                        SensorDisplayMod.network.sendToServer(new PacketOpenRemoteGui(
                                tileDisplay.xCoord, tileDisplay.yCoord, tileDisplay.zCoord, b[0]));
                    }
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    /** 应用设置：写回客户端 tile（立即渲染）+ 发送服务端持久化并回推 */
    private void applySettings() {
        if (tileDisplay == null) return;
        tileDisplay.applySettingsClient(guiFontSize, guiAlign, guiTextRot, guiMode, guiOverflow, guiColumns);
        SensorDisplayMod.network.sendToServer(new PacketSetDisplaySettings(
                tileDisplay.xCoord, tileDisplay.yCoord, tileDisplay.zCoord,
                guiFontSize, guiAlign, guiTextRot, guiMode, guiOverflow, guiColumns));
    }

    /** 最大分栏数 = ceil(有效显示宽度(字符数)/2) + 1 */
    private int maxColsFor() {
        if (tileDisplay == null) return 1;
        World world = tileDisplay.getWorldObj();
        if (world == null) return 1;
        int meta = tileDisplay.getBlockMetadata();
        DisplayGroupUtil.Info gi = DisplayGroupUtil.compute(world, tileDisplay.xCoord, tileDisplay.yCoord, tileDisplay.zCoord, meta);
        int W = gi.W, H = gi.H;
        int facePx = 50; // 1/SCALE
        int canvasW = W * facePx, canvasH = H * facePx;
        int effW = (guiTextRot == 1 || guiTextRot == 3) ? canvasH : canvasW;
        int fontH = fontRendererObj.FONT_HEIGHT;
        int usable = effW - 16;
        if (usable < 1) return 1;
        int w = usable / fontH; // 有效字符数
        int maxCols = (int) Math.ceil(w / 2.0) + 1;
        return Math.max(1, maxCols);
    }

    private void btn(int x, int y, int w, int h, int bg, String label) {
        drawRect(x, y, x + w, y + h, bg);
        int tx = x + (w - fontRendererObj.getStringWidth(label)) / 2;
        this.fontRendererObj.drawString(label, tx, y + 4, 0xFFFFFFFF);
    }

    private String cardName(ItemStack card) {
        if (card == null) return "(\u7a7a)"; // (空)
        NBTTagCompound tag = card.getTagCompound();
        if (tag != null && tag.hasKey("display") && tag.getCompoundTag("display").hasKey("Name")) {
            return tag.getCompoundTag("display").getString("Name");
        }
        int dir = (tag != null && tag.hasKey("dirIndex")) ? tag.getInteger("dirIndex") : -1;
        if (dir >= 0 && dir < 6) return "\u7ed1\u5b9a\u5361-" + DIR_SENSOR_LABEL[dir];
        return "\u7ed1\u5b9a\u5361-\u5168\u90e8"; // 绑定卡-全部
    }

    private static final String[] DIR_SENSOR_LABEL = {"\u4e0b", "\u4e0a", "\u5317", "\u5357", "\u897f", "\u4e1c"}; // 下上北南西东

    private String formatFs(float f) {
        String s = String.format("%.2f", f);
        if (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

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
}
