package com.cyclone.minecraftiot.client.renderer;

import com.cyclone.minecraftiot.tileentity.TileDisplay;
import com.cyclone.minecraftiot.util.DisplayGroupUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * 多显示器拼接渲染 + 结构完整性 + 主控指示灯。
 * 大改版后：
 * - 主控角固定（下东南定律），只有主控持卡、渲染文字。
 * - 多页数据按展示模式（轮流/拼接/分栏）与溢出行为（截断/分页/滚动）布局。
 * - 文字支持 4 向旋转（0=上 1=右 2=下 3=左）。
 */
public class DisplayRenderer extends TileEntitySpecialRenderer {

    private static final float SCALE = 0.02f;
    private static final int ROTATE_TICKS = 40;  // 轮流：每张卡停留时长(2s)
    private static final int PAGED_TICKS = 80;   // 分页：每页停留时长(4s)
    private static final int SCROLL_TICKS = 15;  // 滚动：每行推进时长(0.75s)

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        TileDisplay display = (TileDisplay) te;
        if (display == null) return;
        int meta = te.getBlockMetadata();
        World world = te.getWorldObj();
        if (world == null) return;

        DisplayGroupUtil.Info gi = DisplayGroupUtil.compute(world, te.xCoord, te.yCoord, te.zCoord, meta);
        if (gi.members.isEmpty()) return;
        boolean structValid = gi.structValid;
        DisplayGroupUtil.Pos masterPos = DisplayGroupUtil.resolveMasterPos(world, te.xCoord, te.yCoord, te.zCoord, meta);
        if (masterPos == null) return;

        // 主控数据（客户端 tile 上的多页数据 + 设置）
        TileEntity mte = world.getTileEntity(masterPos.x, masterPos.y, masterPos.z);
        TileDisplay master = (mte instanceof TileDisplay) ? (TileDisplay) mte : null;
        String[][] pages = master != null ? master.getPages() : null;
        List<String[]> active = activePages(pages);

        // 指示灯：仅主控角（持有数据）绘制。绿=结构完整有效 / 红=结构不完整
        if (masterPos.equals(te.xCoord, te.yCoord, te.zCoord) && master != null && !active.isEmpty()) {
            int color = structValid ? 0xFF00FF00 : 0xFFFF0000;
            int half = (int) Math.round(1.0f / SCALE) / 2;
            // 按主控在组内的 u/v 极值 + 各朝向镜像规则，把灯固定到屏幕对应角点
            int mu = (gi.uAxis == 0) ? masterPos.x : (gi.uAxis == 1 ? masterPos.y : masterPos.z);
            int mv = (gi.vAxis == 0) ? masterPos.x : (gi.vAxis == 1 ? masterPos.y : masterPos.z);
            boolean leftEdge = mu == gi.minU;   // 靠组平面 u 最小侧
            boolean topEdge  = mv == gi.maxV;   // 靠组平面 v 最大侧(屏幕上方)
            int lx = (meta == 2) ? (leftEdge ? half : -half) : (leftEdge ? -half : half); // NORTH 左右镜像
            int ly = (meta == 1) ? (topEdge ? half : -half) : (topEdge ? -half : half);   // UP 上下镜像
            drawPixel(x, y, z, meta, color, lx, ly);
        }

        // 数据渲染：结构完整 + 本块为主控 + 主控有数据
        if (!structValid) return;
        if (!masterPos.equals(te.xCoord, te.yCoord, te.zCoord)) return;
        if (master == null || active.isEmpty()) return;

        int W = gi.W, H = gi.H;
        int uAxis = gi.uAxis, vAxis = gi.vAxis, fixedAxis = gi.fixedAxis;

        double[] c = new double[3];
        double[] base = { te.xCoord + 0.5, te.yCoord + 0.5, te.zCoord + 0.5 };
        c[uAxis] = (gi.minU + gi.maxU + 1) / 2.0;
        c[vAxis] = (gi.minV + gi.maxV + 1) / 2.0;
        c[fixedAxis] = base[fixedAxis];
        double dx = c[0] - base[0], dy = c[1] - base[1], dz = c[2] - base[2];

        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5 + dx, y + 0.5 + dy, z + 0.5 + dz);
        rotateForDirection(dirFromMeta(meta));
        GL11.glTranslated(0, 0, 0.5);
        GL11.glScalef(SCALE, -SCALE, -SCALE);
        drawText(master, active, gi, world.getTotalWorldTime());
        GL11.glPopMatrix();
    }

    /** 文字绘制：展示模式 / 溢出行为 / 分栏 / 对齐 / 字号 / 文字旋转 */
    private void drawText(TileDisplay master, List<String[]> active, DisplayGroupUtil.Info gi, long time) {
        int mode = master.getDisplayMode();
        int overflow = master.getOverflowMode();
        int cols = master.getColumns();
        int rot = master.getTextRot();
        float fs = master.getFontSize();
        int align = master.getAlignMode();
        if (fs < 0.5f) fs = 0.5f;
        if (fs > 2.0f) fs = 2.0f;

        int facePx = (int) Math.round(1.0f / SCALE);
        int canvasW = gi.W * facePx, canvasH = gi.H * facePx;

        // 决定列数与每列内容
        List<String[]> columns = new ArrayList<String[]>();
        if (mode == 2 && cols > 1) {
            int cc = Math.min(cols, active.size());
            for (int i = 0; i < cc; i++) columns.add(active.get(i));
        } else if (mode == 1) {
            List<String> merged = new ArrayList<String>();
            for (String[] pg : active) {
                for (String l : pg) if (l != null && !l.isEmpty()) merged.add(l);
            }
            columns.add(merged.toArray(new String[0]));
        } else {
            int idx = (int) ((time / ROTATE_TICKS) % active.size());
            columns.add(active.get(idx));
        }
        if (columns.isEmpty()) return;

        // 文字旋转：90/270 时有效宽高互换
        int effW = canvasW, effH = canvasH;
        if (rot == 1 || rot == 3) { int t = effW; effW = effH; effH = t; }

        int colCount = columns.size();
        int colW = effW / colCount;
        int inset = 8; // 面板边框留白，让文字保持在黑色显示区域内
        int colInner = colW - 2 * inset;
        if (colInner < 1) colInner = 1;
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int fontH = font.FONT_HEIGHT;
        float fh = fontH * fs;
        int maxLinesPerCol = (int) Math.floor((effH - 2 * inset) / fh);
        if (maxLinesPerCol < 1) maxLinesPerCol = 1;
        int wrapLimit = Math.max(1, (int) (colInner / fs));

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        GL11.glPushMatrix(); // 内层缩放实现字号 + 文字旋转
        GL11.glScalef(fs, fs, 1.0f);
        float rotDeg = rot == 0 ? 0f : rot == 1 ? 90f : rot == 2 ? 180f : 270f;
        GL11.glRotatef(rotDeg, 0, 0, 1);

        float topY = -(effH / 2.0f) + inset;
        float colLeftBase = -(effW / 2.0f) + inset;
        for (int c = 0; c < colCount; c++) {
            String[] raw = columns.get(c);
            // 换行
            List<String> wrapped = new ArrayList<String>();
            for (String l : raw) {
                if (l == null) continue;
                for (String seg : wrap(font, l, wrapLimit)) wrapped.add(seg);
            }
            // 溢出行为
            List<String> visible = applyOverflow(wrapped, overflow, maxLinesPerCol, time);

            float colX0 = colLeftBase + c * colW;            // 本列可用左边缘(有效坐标)
            float colX1 = colX0 + (colW - 2 * inset);         // 本列可用右边缘
            float lineY = topY;
            for (String seg : visible) {
                float segW = font.getStringWidth(seg);
                float drawX;
                if (align == 1) drawX = colX0 + (colInner - segW * fs) / 2.0f;   // 居中
                else if (align == 2) drawX = colX1 - segW * fs;                  // 右对齐
                else drawX = colX0;                                              // 左对齐
                font.drawString(seg, (int) (drawX / fs), (int) (lineY / fs), 0xFFFFFFFF);
                lineY += fh;
            }
        }

        GL11.glPopMatrix();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LIGHTING);
    }

    /** 溢出行为：截断 / 分页清屏 / 逐行滚动 */
    private List<String> applyOverflow(List<String> lines, int overflow, int maxLines, long time) {
        List<String> out = new ArrayList<String>();
        if (lines == null || lines.isEmpty()) return out;
        int total = lines.size();
        if (overflow == 2) { // 逐行滚动：到末尾后跳回首行
            int maxOff = Math.max(0, total - maxLines);
            int off = (int) ((time / SCROLL_TICKS) % (maxOff + 1));
            for (int i = off; i < off + maxLines && i < total; i++) out.add(lines.get(i));
        } else if (overflow == 1) { // 分页清屏：每隔一段时间切到下一页
            int pageN = Math.max(1, (int) Math.ceil(total / (double) maxLines));
            int pg = (int) ((time / PAGED_TICKS) % pageN);
            int start = pg * maxLines;
            for (int i = start; i < start + maxLines && i < total; i++) out.add(lines.get(i));
        } else { // 截断
            for (int i = 0; i < maxLines && i < total; i++) out.add(lines.get(i));
        }
        return out;
    }

    private List<String[]> activePages(String[][] pages) {
        List<String[]> out = new ArrayList<String[]>();
        if (pages == null) return out;
        for (String[] pg : pages) {
            if (pg == null) continue;
            boolean has = false;
            for (String l : pg) if (l != null && !l.isEmpty()) { has = true; break; }
            if (has) out.add(pg);
        }
        return out;
    }

    /** 在方块显示面角内侧画一个指示灯（主控用，2 纹理像素）；z 略向外偏移以免与方块面深度冲突 */
    private void drawPixel(double x, double y, double z, int meta, int color, int lx, int ly) {
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
        rotateForDirection(dirFromMeta(meta));
        GL11.glTranslated(0, 0, 0.5);
        GL11.glScalef(SCALE, -SCALE, -SCALE);

        int px = 6; // 与显示器正面白色边框同宽，落在白边框上不压到黑色内芯
        float x0 = lx > 0 ? lx - px : lx;
        float y0 = ly > 0 ? ly - px : ly;
        float x1 = lx < 0 ? lx + px : lx;
        float y1 = ly < 0 ? ly + px : ly;

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        float r = ((color >> 16) & 255) / 255f;
        float g = ((color >> 8) & 255) / 255f;
        float b = (color & 255) / 255f;
        GL11.glColor4f(r, g, b, 1.0f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(x0, y0, -1);
        GL11.glVertex3f(x1, y0, -1);
        GL11.glVertex3f(x1, y1, -1);
        GL11.glVertex3f(x0, y1, -1);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

    // ============ 工具 ============

    private ForgeDirection dirFromMeta(int meta) {
        switch (meta) {
            case 0: return ForgeDirection.DOWN;
            case 1: return ForgeDirection.UP;
            case 2: return ForgeDirection.NORTH;
            case 4: return ForgeDirection.WEST;
            case 5: return ForgeDirection.EAST;
            default: return ForgeDirection.SOUTH;
        }
    }

    private void rotateForDirection(ForgeDirection dir) {
        switch (dir) {
            case NORTH: GL11.glRotatef(180, 0, 1, 0); break;
            case SOUTH: break;
            case WEST: GL11.glRotatef(-90, 0, 1, 0); break;
            case EAST: GL11.glRotatef(90, 0, 1, 0); break;
            case UP: GL11.glRotatef(-90, 1, 0, 0); break;
            case DOWN: GL11.glRotatef(90, 1, 0, 0); break;
            default: break;
        }
    }

    private List<String> wrap(FontRenderer font, String text, int maxWidth) {
        List<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String trial = cur.toString() + c;
            if (font.getStringWidth(trial) > maxWidth && cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
}
