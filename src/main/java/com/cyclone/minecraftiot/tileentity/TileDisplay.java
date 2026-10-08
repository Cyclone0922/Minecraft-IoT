package com.cyclone.minecraftiot.tileentity;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.item.ItemConnector;
import com.cyclone.minecraftiot.network.PacketDisplayData;
import com.cyclone.minecraftiot.util.ConnectorConfig;
import com.cyclone.minecraftiot.util.TemplateExpr;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 显示器（主控）方块。
 *
 * 大改版后的架构：
 * - 一个显示器拼接组有唯一的"主控"角（下东南定律：垂直取下层最东南，水平取最东南）。
 *   只有主控持有数据与绑定卡，任意成员右键打开 GUI 都操作主控。
 * - 主控可插入最多 MAX_CARDS(6) 张绑定卡，每张卡绑定一个传感器+方向，生成一页显示数据。
 * - 展示模式：轮流 / 拼接 / 分栏；溢出行为：截断 / 分页清屏 / 逐行滚动（渲染端执行）。
 * - 服务端负责按卡生成"页"并连同所有显示设置推送给客户端，客户端渲染端做布局/动画。
 */
public class TileDisplay extends TileEntity implements IInventory {
    public static final int MAX_CARDS = 6;

    // 绑定卡槽位（最多 6 张）
    private ItemStack[] cards = new ItemStack[MAX_CARDS];

    // 显示设置
    private float fontSize = 1.0f; // 0.5..2.0
    private int alignMode = 0;     // 0=左 1=中 2=右
    private int textRot = 0;       // 文字朝向：0=上(N) 1=右(E) 2=下(S) 3=左(W)
    private int displayMode = 0;   // 0=轮流 1=拼接 2=分栏
    private int overflowMode = 0;  // 0=截断 1=分页清屏 2=逐行滚动
    private int columns = 1;       // 分栏数（仅分栏模式有意义，1..maxCols）

    // 每张卡一页（服务端算好推给客户端）；客户端据此渲染/切换/拼接/分栏
    private String[][] pages = new String[MAX_CARDS][];

    private int updateCounter = 0;
    private boolean hasAnyCard = false;
    private boolean pendingInitialSync = false; // 加载后首个 tick 立即推送一次

    // ============ 数据页 ============

    public String[][] getPages() { return pages; }

    /** 客户端接收服务端推送的多页数据 */
    public void setPages(String[][] p) { this.pages = p; }

    /** 客户端应用全部显示设置（渲染端读取，不触发服务端持久化标记） */
    public void applySettingsClient(float fs, int align, int rot, int mode, int overflow, int cols) {
        this.fontSize = fs;
        this.alignMode = align;
        this.textRot = rot;
        this.displayMode = mode;
        this.overflowMode = overflow;
        this.columns = cols;
    }

    // ============ 显示设置 getter/setter ============

    public float getFontSize() { return fontSize; }
    public int getAlignMode() { return alignMode; }
    public int getTextRot() { return textRot; }
    public int getDisplayMode() { return displayMode; }
    public int getOverflowMode() { return overflowMode; }
    public int getColumns() { return columns; }

    /** 服务端保存全部显示设置并持久化（主控 GUI 的"设置"卡使用） */
    public void applyDisplaySettingsAll(float fs, int align, int rot, int mode, int overflow, int cols) {
        this.fontSize = fs;
        this.alignMode = align;
        this.textRot = rot;
        this.displayMode = mode;
        this.overflowMode = overflow;
        this.columns = cols;
        markDirty();
    }

    public boolean hasAnyCard() { return hasAnyCard; }

    /** 槽位内是否有绑定卡（客户端渲染主控指示灯用） */
    public boolean hasConnector() { return hasAnyCard; }

    /** 把当前所有页 + 显示设置推送给客户端（设置变更/卡片变更后调用，保证渲染同步） */
    public void resyncDisplay() {
        pushToClient();
    }

    // ============ 服务端数据计算与推送 ============

    @Override
    public void updateEntity() {
        if (!worldObj.isRemote) {
            if (pendingInitialSync) {
                pendingInitialSync = false;
                recomputePages();
                pushToClient();
            }
            updateCounter++;
            if (hasAnyCard && updateCounter % 40 == 0) {
                recomputePages();
                pushToClient();
            }
        }
    }

    /** 服务端：重新生成数据页（供设置包等外部调用；客户端忽略） */
    public void recomputePagesIfServer() {
        if (worldObj == null || worldObj.isRemote) return;
        recomputePages();
    }

    /** 服务端：按每张卡重新生成数据页 */
    private void recomputePages() {
        hasAnyCard = false;
        for (int i = 0; i < MAX_CARDS; i++) {
            pages[i] = buildPageFromCard(cards[i]);
            if (pages[i] != null && pages[i].length > 0) hasAnyCard = true;
        }
    }

    /**
     * 按一张绑定卡的显示配置生成数据页。
     * 卡配置（连接器工作台写入）决定渲染来源：
     *   MODE_RAW(0)     → 传感器的原始 NBT 摘要
     *   MODE_DEFAULT(1) → 传感器的"预设模板"友好摘要（自动组合/聚合）
     *   MODE_CUSTOM(2)  → 用卡上的自定义表达式对服务端完整 NBT 求值（TemplateExpr 语法）
     */
    private String[] buildPageFromCard(ItemStack card) {
        if (card == null || !(card.getItem() instanceof ItemConnector)) return null;
        NBTTagCompound tag = card.getTagCompound();
        if (tag == null || !tag.hasKey("sensorX")) return null;
        int sx = tag.getInteger("sensorX"), sy = tag.getInteger("sensorY"), sz = tag.getInteger("sensorZ");
        int dim = tag.getInteger("dimension");
        int dir = tag.hasKey("dirIndex") ? tag.getInteger("dirIndex") : -1;
        World tw = worldForDim(dim);
        if (tw == null) return null;
        TileEntity te = tw.getTileEntity(sx, sy, sz);
        if (!(te instanceof TileSensor)) return null;
        TileSensor sensor = (TileSensor) te;
        String label = (dir >= 0) ? sensor.getLabel(dir) : "";
        String cardName = ConnectorConfig.getName(card);
        if (!cardName.isEmpty() && label.isEmpty()) label = cardName;

        int mode = ConnectorConfig.getMode(card);
        if (mode == ConnectorConfig.MODE_RAW) {
            // 原始 NBT：直接显示传感器的原始 NBT 摘要
            return formatLines(sensor.getRawResults(), label, dir);
        }
        if (mode == ConnectorConfig.MODE_CUSTOM) {
            String expr = ConnectorConfig.getExpr(card);
            NBTTagCompound nbt = (dir >= 0) ? sensor.getRawNbt(dir) : firstNonEmptyNbt(sensor);
            if (expr != null && !expr.isEmpty() && nbt != null) {
                String text = TemplateExpr.evaluate(expr, nbt);
                if (text != null && !text.isEmpty()) {
                    String[] fake = new String[6];
                    fake[(dir >= 0) ? dir : 0] = text;
                    return formatLines(fake, label, dir);
                }
            }
            // 表达式为空或无 NBT：退回默认友好方案
        }
        // 默认友好方案
        return formatLines(sensor.getScanResults(), label, dir);
    }

    /** 取传感器六个面中第一个有完整 NBT 的方向（绑定"全部方向"+自定义表达式时用） */
    private NBTTagCompound firstNonEmptyNbt(TileSensor sensor) {
        if (sensor == null) return null;
        for (int i = 0; i < 6; i++) {
            NBTTagCompound n = sensor.getRawNbt(i);
            if (n != null) return n;
        }
        return null;
    }

    private World worldForDim(int dim) {
        if (dim == worldObj.provider.dimensionId) return worldObj;
        if (MinecraftServer.getServer() == null) return null;
        return MinecraftServer.getServer().worldServerForDimension(dim);
    }

    private String[] formatLines(String[] results, String label, int dir) {
        List<String> lines = new ArrayList<String>();
        if (label != null && !label.isEmpty()) lines.add(label);
        if (dir >= 0 && dir < results.length) {
            addLines(lines, results[dir]);
        } else {
            for (int i = 0; i < results.length; i++) addLines(lines, results[i]);
        }
        return lines.toArray(new String[16]);
    }

    private void addLines(List<String> lines, String s) {
        if (s == null || s.isEmpty()) return;
        for (String seg : s.split("\n", -1)) {
            if (!seg.isEmpty()) lines.add(seg);
        }
    }

    private void pushToClient() {
        if (worldObj == null || worldObj.isRemote) return;
        PacketDisplayData packet = new PacketDisplayData(xCoord, yCoord, zCoord, pages,
                fontSize, alignMode, textRot, displayMode, overflowMode, columns);
        MinecraftIotMod.network.sendToDimension(packet, worldObj.provider.dimensionId);
        int n = 0;
        for (int i = 0; i < MAX_CARDS; i++) if (pages[i] != null && pages[i].length > 0) n++;
        MinecraftIotMod.log.info("[TileDisplay@" + xCoord + "," + yCoord + "," + zCoord + "] pushed " + n + " page(s)");
    }

    /** 服务端：某张卡槽位内容变化后，重算数据并推送 */
    public void onCardSlotChanged(int slot) {
        if (worldObj == null || worldObj.isRemote) return;
        recomputePages();
        pushToClient();
    }

    // ============ IInventory（6 个绑定卡槽位）============

    @Override
    public int getSizeInventory() {
        return MAX_CARDS;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= MAX_CARDS) return null;
        return cards[slot];
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slot < 0 || slot >= MAX_CARDS || cards[slot] == null) return null;
        ItemStack stack;
        if (cards[slot].stackSize <= amount) {
            stack = cards[slot];
            cards[slot] = null;
            markDirty();
            onCardSlotChanged(slot);
            return stack;
        } else {
            stack = cards[slot].splitStack(amount);
            if (cards[slot].stackSize == 0) cards[slot] = null;
            markDirty();
            onCardSlotChanged(slot);
            return stack;
        }
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        if (slot < 0 || slot >= MAX_CARDS) return null;
        ItemStack stack = cards[slot];
        cards[slot] = null;
        return stack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot < 0 || slot >= MAX_CARDS) return;
        cards[slot] = stack;
        if (stack != null && stack.stackSize > getInventoryStackLimit()) {
            stack.stackSize = getInventoryStackLimit();
        }
        markDirty();
        onCardSlotChanged(slot);
    }

    @Override
    public String getInventoryName() {
        return "container.display";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
                && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64.0;
    }

    @Override
    public void openInventory() {
    }

    @Override
    public void closeInventory() {
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return slot >= 0 && slot < MAX_CARDS && stack != null && stack.getItem() instanceof ItemConnector;
    }

    // ============ 持久化 ============

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setFloat("fontSize", fontSize);
        tag.setInteger("alignMode", alignMode);
        tag.setInteger("textRot", textRot);
        tag.setInteger("displayMode", displayMode);
        tag.setInteger("overflowMode", overflowMode);
        tag.setInteger("columns", columns);
        for (int i = 0; i < MAX_CARDS; i++) {
            if (cards[i] != null) {
                NBTTagCompound itemTag = new NBTTagCompound();
                cards[i].writeToNBT(itemTag);
                tag.setTag("Card" + i, itemTag);
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("fontSize")) fontSize = tag.getFloat("fontSize");
        if (tag.hasKey("alignMode")) alignMode = tag.getInteger("alignMode");
        if (tag.hasKey("textRot")) textRot = tag.getInteger("textRot");
        if (tag.hasKey("displayMode")) displayMode = tag.getInteger("displayMode");
        if (tag.hasKey("overflowMode")) overflowMode = tag.getInteger("overflowMode");
        if (tag.hasKey("columns")) columns = tag.getInteger("columns");
        for (int i = 0; i < MAX_CARDS; i++) {
            if (tag.hasKey("Card" + i)) {
                cards[i] = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("Card" + i));
            }
        }
        // 旧版单卡迁移：老存档的单个 ConnectorStack 迁移到 0 号槽
        if (tag.hasKey("ConnectorStack") && cards[0] == null) {
            cards[0] = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("ConnectorStack"));
        }
        // 重进存档后若有关联卡，则首个 tick 立即请求数据并推送，保证一进存档即显示
        boolean any = false;
        for (int i = 0; i < MAX_CARDS; i++) {
            if (cards[i] != null && cards[i].getTagCompound() != null && cards[i].getTagCompound().hasKey("sensorX")) {
                any = true;
            }
        }
        hasAnyCard = any;
        if (hasAnyCard) {
            pendingInitialSync = true;
        }
    }
}
