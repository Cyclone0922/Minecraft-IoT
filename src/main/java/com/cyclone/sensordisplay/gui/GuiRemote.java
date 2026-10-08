package com.cyclone.sensordisplay.gui;

import com.cyclone.sensordisplay.util.DisplayFormatConfig;
import com.cyclone.sensordisplay.util.DisplayFormatter;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayList;
import java.util.List;

/**
 * 远程终端 GUI（客户端）：通用机器操作界面——顶部标题 + 通用进度条 + 能量/状态 + 简要模板字段，
 * 中部为机器物品槽（可放取），玩家不移动、无距离校验，完全安全。
 */
public class GuiRemote extends GuiContainer {
    private final ContainerRemote remote;
    private final World world;
    private final int tx, ty, tz;

    private String[] infoLines = new String[0];
    private int infoTimer = 0;
    private float progress = -1f;   // -1=无进度数据
    private String energyLine = null;

    public GuiRemote(ContainerRemote container, World world, int x, int y, int z) {
        super(container);
        this.remote = container;
        this.world = world;
        this.tx = x; this.ty = y; this.tz = z;
        this.xSize = 176;
        this.ySize = container.guiHeight();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawDefaultBackground();
        int i = (width - xSize) / 2, j = (height - ySize) / 2;
        drawRect(i, j, i + xSize, j + ySize, 0xFFC6C6C6);
        drawRect(i + 1, j + 1, i + xSize - 1, j + ySize - 1, 0xFFD6D6D6);
        // 通用进度条底
        drawRect(i + 30, j + 18, i + 162, j + 28, 0xFF000000);
        drawRect(i + 31, j + 19, i + 161, j + 27, 0xFF2B2B2B);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // 每 10 tick 刷新一次，保持动态值较新又不浪费
        if (--infoTimer <= 0) {
            infoTimer = 10;
            refresh();
        }
        // 标题
        TileEntity te = world.getTileEntity(tx, ty, tz);
        String title = "\u8fdc\u7a0b\u7ec8\u7aef"; // 远程终端
        if (te instanceof IInventory) {
            String n = ((IInventory) te).getInventoryName();
            if (n != null && !n.isEmpty()) title = n;
        }
        this.fontRendererObj.drawString(title, 8, 6, 0xFF333333);

        // 通用进度条
        this.fontRendererObj.drawString("\u8fdb\u5ea6", 8, 19, 0xFF333333); // 进度
        if (progress >= 0f) {
            int bw = 130;
            int fill = (int) (bw * progress);
            drawRect(31, 19, 31 + fill, 27, 0xFF2E8B57);
            this.fontRendererObj.drawString((int) (progress * 100) + "%", 164, 19, 0xFF333333);
        } else {
            this.fontRendererObj.drawString("\u2014", 164, 19, 0xFF777777);
        }
        // 能量 / 状态
        if (energyLine != null) {
            this.fontRendererObj.drawString(energyLine, 8, 30, 0xFF555555);
        }

        // 简要模板字段（最多 2 行，其余在显示器方块上看全）
        int y = 42;
        int shown = 0;
        for (String line : infoLines) {
            if (shown >= 2) break;
            for (String seg : wrap(line, xSize - 16)) {
                if (shown >= 2) break;
                this.fontRendererObj.drawString(seg, 8, y, 0xFF555555);
                y += 9;
                shown++;
            }
        }
    }

    private void refresh() {
        TileEntity te = world.getTileEntity(tx, ty, tz);
        infoLines = new String[0];
        progress = -1f;
        energyLine = null;
        if (te == null) return;

        NBTTagCompound nbt = new NBTTagCompound();
        try { te.writeToNBT(nbt); } catch (Throwable t) { /* ignore */ }

        // 进度（通用提取）
        progress = extractProgress(te, nbt);
        // 能量 / 状态
        energyLine = extractEnergy(te, nbt);

        // 模板字段（客户端可用 DisplayFormatter 的 3 参版本）
        DisplayFormatConfig.Template tpl = DisplayFormatConfig.lookup(te);
        if (tpl != null) {
            List<String> f = DisplayFormatter.format(te, ForgeDirection.SOUTH, tpl);
            List<String> lines = new ArrayList<String>();
            if (f != null) {
                for (String s : f) {
                    if (s == null) continue;
                    if (s.startsWith("[")) {
                        int close = s.indexOf("] ");
                        if (close >= 0) s = s.substring(close + 2);
                    }
                    lines.add(s);
                }
            }
            infoLines = lines.toArray(new String[lines.size()]);
        }
    }

    private float extractProgress(TileEntity te, NBTTagCompound nbt) {
        if (te instanceof TileEntityFurnace) {
            int cook = nbt.getInteger("CookTime");
            int burn = nbt.getInteger("BurnTime");
            if (cook > 0) return clamp(cook / 200f);
            return burn > 0 ? 0f : -1f; // 预热=0%，空闲=无
        }
        if (nbt.hasKey("ProcMax") && nbt.hasKey("ProcRem")) {
            int max = nbt.getInteger("ProcMax"), rem = nbt.getInteger("ProcRem");
            if (max > 0) return clamp((max - rem) / (float) max);
        }
        if (nbt.hasKey("progress")) {
            // IC2 progress（short，0..100 或 tick），取 0..1 近似
            int p = nbt.getShort("progress");
            if (p > 0) return clamp(p / 100f);
        }
        // IC2 getProgress() 反射（0..1）
        try {
            java.lang.reflect.Method m = te.getClass().getMethod("getProgress");
            Object r = m.invoke(te);
            if (r instanceof Number) return clamp(((Number) r).floatValue());
        } catch (Exception ignore) { }
        return -1f;
    }

    private String extractEnergy(TileEntity te, NBTTagCompound nbt) {
        StringBuilder sb = new StringBuilder();
        if (nbt.hasKey("Energy")) {
            sb.append("\u80fd\u91cf: ").append(nbt.getInteger("Energy")).append(" RF"); // 能量
        } else if (nbt.hasKey("energy")) {
            sb.append("\u7535\u91cf: ").append((long) nbt.getDouble("energy")).append(" EU"); // 电量
        } else if (nbt.hasKey("storage")) {
            sb.append("\u50a8\u80fd: ").append((long) nbt.getDouble("storage")).append(" EU"); // 储能
        }
        boolean active = nbt.hasKey("Active") ? nbt.getBoolean("Active")
                : (nbt.hasKey("active") ? nbt.getBoolean("active") : false);
        if (nbt.hasKey("Active") || nbt.hasKey("active")) {
            if (sb.length() > 0) sb.append("   ");
            sb.append(active ? "\u8fd0\u884c\u4e2d" : "\u505c\u6b62"); // 运行中/停止
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
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
