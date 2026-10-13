package com.cyclone.minecraftiot.gui;

import com.cyclone.minecraftiot.tileentity.TileItemBuffer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * 物品缓存器只读 GUI（AE2 面板风格）：
 *  - 一种物品一格，堆叠数量可 >64，右下角直接显示数量
 *  - 耐久条物品以无耐久之原始贴图显示 + 数量（本实现统一渲染原贴图 + 数量）
 *  - 深色面板 + 灰色物品格
 *  - 不暂停游戏刻，禁用所有鼠标放入/取出交互（Container 无槽）
 */
public class GuiItemBuffer extends GuiContainer {

    private static final ResourceLocation BG =
            new ResourceLocation("minecraftiot:textures/gui/item_buffer.png");
    private final TileItemBuffer te;

    // 面板布局（16:9 宽面板）
    private static final int PANEL_X = 14, PANEL_Y = 16, PANEL_W = 260, PANEL_H = 164;
    private static final int SLOT = 18;
    private static final int COLUMNS = 13;   // 每行格子数
    private static final int ROWS = 8;       // 行数（13×8=104 格，覆盖种类上限的展示）

    public GuiItemBuffer(ContainerItemBuffer container) {
        super(container);
        this.te = container.te;
        this.xSize = 288;
        this.ySize = 200;
    }

    @Override
    public boolean doesGuiPauseGame() { return false; } // 不暂停游戏刻（AE2 面板实时刷新）

    /** 鼠标悬停物品格：显示物品 tooltip + 实际完整数量（非 k/m 格式化） */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        int[] ids = te.getCIds();
        int[] metas = te.getCMetas();
        int[] counts = te.getCCounts();
        if (ids == null || counts == null) return;
        int n = Math.min(ids.length, COLUMNS * ROWS);
        for (int i = 0; i < n; i++) {
            int r = i / COLUMNS, c = i % COLUMNS;
            int sx = guiLeft + PANEL_X + c * SLOT;
            int sy = guiTop + PANEL_Y + 18 + r * SLOT;
            if (mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT) {
                Item item = Item.getItemById(ids[i]);
                if (item != null) {
                    ItemStack stack = new ItemStack(item, counts[i], metas[i]);
                    java.util.List list = stack.getTooltip(this.mc.thePlayer,
                            this.mc.gameSettings.advancedItemTooltips);
                    list.add("数量: " + counts[i]);
                    drawHoveringText(list, mouseX, mouseY, this.fontRendererObj);
                }
                break;
            }
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GL11.glColor4f(1f, 1f, 1f, 1f);
        // 主背景（深色面板）
        this.mc.renderEngine.bindTexture(BG);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

        // 物品区底色（AE2 面板深灰）
        drawRect(guiLeft + PANEL_X, guiTop + PANEL_Y,
                guiLeft + PANEL_X + PANEL_W, guiTop + PANEL_Y + PANEL_H,
                0xFF2A2A2E);

        // 顶部信息栏
        String title = te.getCDrMode()
                ? "网络存储器 [灾备]  " + te.getCTotal() + " / " + te.getCItemCap() + "  (" + te.getCKind() + " 种 / " + te.getCKindCap() + ")"
                : "网络存储器  " + te.getCTotal() + " / " + te.getCItemCap() + "  (" + te.getCKind() + " 种 / " + te.getCKindCap() + ")";
        this.fontRendererObj.drawString(title, guiLeft + PANEL_X, guiTop + 6, 0xE0E0E0);

        // 渲染物品格（背景灰格）
        int[] ids = te.getCIds();
        int[] metas = te.getCMetas();
        int[] counts = te.getCCounts();
        int n = Math.min(ids.length, COLUMNS * ROWS);
        int idx = 0;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLUMNS; c++) {
                int sx = guiLeft + PANEL_X + c * SLOT;
                int sy = guiTop + PANEL_Y + 18 + r * SLOT;
                if (idx < n) {
                    drawSlotBorder(sx, sy);
                } else if (idx < te.getCKindCap()) {
                    drawSlotEmpty(sx, sy);
                }
                idx++;
            }
        }

        // 渲染物品 + 数量（只渲染实际有物品的格）
        for (int i = 0; i < n; i++) {
            int r = i / COLUMNS, c = i % COLUMNS;
            int sx = guiLeft + PANEL_X + c * SLOT;
            int sy = guiTop + PANEL_Y + 18 + r * SLOT;
            ItemStack stack = new ItemStack(Item.getItemById(ids[i]), 1, metas[i]);
            drawItemAndCount(stack, counts[i], sx, sy);
        }
    }

    private void drawSlotEmpty(int x, int y) {
        drawRect(x, y, x + SLOT, y + SLOT, 0xFF3A3A40);
    }

    private void drawSlotBorder(int x, int y) {
        drawRect(x, y, x + SLOT, y + SLOT, 0xFF4A4A52); // 实心格（AE2 面板格底）
    }

    /** 数量格式化：<10000 原样；10000~999999 用 k（整数）；>=1000000 用 m（最多一位小数） */
    private static String formatCount(int count) {
        if (count >= 1000000) {
            double m = count / 1000000.0D;
            if (m == Math.floor(m)) return (long) m + "m";
            return String.format(java.util.Locale.ROOT, "%.1fm", m);
        }
        if (count >= 10000) {
            return (count / 1000) + "k";
        }
        return String.valueOf(count);
    }

    /** 渲染物品贴图 + 右下角数量（可 >64，用字体直接画数字；不画耐久条） */
    private void drawItemAndCount(ItemStack stack, int count, int x, int y) {
        RenderHelper.enableGUIStandardItemLighting();
        this.itemRender.renderItemAndEffectIntoGUI(this.mc.fontRenderer, this.mc.getTextureManager(), stack, x + 1, y + 1);
        RenderHelper.disableStandardItemLighting();
        // 数量文字：平移到 z=200 平面，确保在 3D 物品（z≈100）之上，不被圆石等不透明贴图挡住
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glPushMatrix();
        GL11.glTranslatef(0.0F, 0.0F, 200.0F);
        String s = formatCount(count);
        int tw = this.fontRendererObj.getStringWidth(s);
        this.fontRendererObj.drawStringWithShadow(s, x + 1 + 18 - 1 - tw, y + 1 + 18 - 10, 0xFFFFFF);
        GL11.glPopMatrix();
    }
}
