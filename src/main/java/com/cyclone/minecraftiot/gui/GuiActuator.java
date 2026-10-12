package com.cyclone.minecraftiot.gui;

// ============================ 导入区 ============================
import com.cyclone.minecraftiot.MinecraftIotMod;          // 主类：拿 network 通道发网络包
import com.cyclone.minecraftiot.network.PacketActuatorConfig; // 保存配置的网络包（C→S）
import com.cyclone.minecraftiot.tileentity.TileActuator; // 执行器方块实体（读写六面配置）
import net.minecraft.client.Minecraft;                  // 客户端单例（切界面、发消息）
import net.minecraft.client.gui.GuiButton;              // 原版按钮控件
import net.minecraft.client.gui.GuiTextField;           // 原版单行文本输入框
import net.minecraft.client.gui.inventory.GuiContainer; // 带容器的 GUI 基类（本类继承它）
import net.minecraft.inventory.Container;               // 容器基类（仅类型引用）
import net.minecraft.inventory.Slot;                    // 物品槽（窗口号防御里用到）
import net.minecraft.util.ChatComponentText;            // 聊天栏文本消息
import net.minecraft.util.ResourceLocation;             // 资源路径（定位贴图）
import net.minecraftforge.common.util.ForgeDirection;   // 六方向枚举（D/U/N/S/W/E）

/**
 * 执行器 GUI（方向合一模型）——客户端渲染与交互全部在本类。
 *
 * 【坐标体系（手动改 GUI 前必读）】
 *  - 所有 *_X / *_Y / *_W / *_H 常量都是"相对 GUI 左上角"的像素坐标：x 向右增、y 向下增。
 *  - drawGuiContainerForegroundLayer（前景层）已由父类 glTranslate 到 (guiLeft, guiTop)，
 *    所以画文字/输入框直接用常量坐标，不要再加 guiLeft/guiTop。
 *  - 但 new GuiButton(...) 和 drawTexturedModalRect(...) 在背景层/initGui 中使用的是屏幕绝对坐标，
 *    必须写 guiLeft + 常量X、guiTop + 常量Y（本类已按此规则写好）。
 *  - guiLeft=(屏宽-xSize)/2、guiTop=(屏高-ySize)/2，即 GUI 窗口在屏幕居中；改 xSize/ySize 会改变居中位置。
 *
 * 【底图】
 *  - 贴图 assets/minecraftiot/textures/gui/actuator.png，256x256（原设计内容 247x238 在左上角，其余透明）。
 *  - 必须是 2 的幂尺寸：1.7.10 drawTexturedModalRect 按 1/256 归一化，非 2 幂贴图会被拉伸、导致槽位错位。
 *  - 贴图布局：y=0~150 上半大面板（编辑控件）；y=156 起下半 4 排 x 9 列格子（玩家背包）。
 *  - 格子本体（139 灰、16x16）左上角坐标：第 1 列 x=43，第 1 排 y=156；列间距/排间距均为 18px；
 *    四排 y 依次为 156 / 174 / 192 / 214。槽实例坐标见 ContainerActuator，必须与此逐像素对齐。
 *
 * 【界面分区】
 *  - 第一行(y≈6)：6 个方向按钮 N/U/W/E/D/S（切换当前编辑面）+ 绑定卡槽 + 技能插件槽。
 *  - 条件表达式输入框(y≈44) + 右侧"全屏"按钮；输出表达式输入框(y≈72) + 右侧"全屏"按钮。
 *  - 红石强度行(y≈90)："强度:" 标签、[-] [数值] [+]；保存按钮(y≈120)。
 *  - 状态行(y=108)：绑定卡 / 插件 / 路由 三段状态；未连路由时 y=122 显示红色门控提示。
 *  - 下半(y=164 起)：玩家背包 3 排 + 快捷栏 1 排（槽位由 Container 定义、Minecraft 自动渲染）。
 *
 * 【草稿机制（pending）】
 *  - 全屏表达式编辑器点"保存"只回填本类输入框（静态 pending 草稿），不写存档；
 *  - 主界面点"保存"才发 PacketActuatorConfig 到服务端落存档，
 *    服务端校验通过回 PacketActuatorSaveAck，收到后清 pending 并在聊天栏提示"保存成功"。
 */
public class GuiActuator extends GuiContainer {

    // 底图贴图路径：modid=minecraftiot 下的 textures/gui/actuator.png
    private static final ResourceLocation background = new ResourceLocation("minecraftiot", "textures/gui/actuator.png");

    // 本界面对应的执行器方块实体（读六面条件/输出表达式/红石强度、卡槽状态、路由状态）
    private TileActuator tile;

    // ===================== 第一行：方向按钮（id=0~5） =====================
    // 按钮上显示的文字，按下标与 DIR_IDX/DIR_X/DIR_Y 一一对应（显示顺序 N U W E D S）
    private static final String[] DIR_LABEL = {"N", "U", "W", "E", "D", "S"};
    // 按钮显示序 → ForgeDirection.VALID_DIRECTIONS 方向索引的映射：
    //   VALID_DIRECTIONS 固定顺序为 0=D 1=U 2=N 3=S 4=W 5=E；
    //   显示序 0(N)→2、1(U)→1、2(W)→4、3(E)→5、4(D)→0、5(S)→3
    private static final int[] DIR_IDX = {2, 1, 4, 5, 0, 3};
    // 六个方向按钮左上角 x（相对 GUI），每个宽 18，相邻间隔 2px（8,28,48,...）
    private static final int[] DIR_X = {8, 28, 48, 68, 88, 108};
    // 六个方向按钮左上角 y（全部同一行 y=6）
    private static final int[] DIR_Y = {6, 6, 6, 6, 6, 6};
    // 方向按钮统一宽/高（正方形 18x18）
    private static final int BTN = 18;

    // ===================== 第一行：两个执行器槽（手绘槽框） =====================
    // 绑定卡槽左上角 x=126；技能插件槽左上角 x=148；两槽同一行 y=6（槽内物品由 Container 渲染）
    private static final int SLOT_CONN_X = 126, SLOT_SKILL_X = 148, SLOT_Y = 6;

    // ===================== 条件表达式输入框 + 全屏按钮 =====================
    // 输入框左上角 (8,44)，宽 180、高 14（GuiTextField 标准高度）
    private static final int COND_X = 8, COND_Y = 44, COND_W = 180, COND_H = 14;
    // 条件框右侧"全屏"按钮：x=192（紧接输入框右边 188 后留 4px），宽 22、高 14，与输入框同高
    private static final int FS_COND_X = 192, FS_W = 22, FS_H = 14;
    // ===================== 输出表达式输入框 + 全屏按钮 =====================
    // 输出框左上角 (8,72)，尺寸同条件框
    private static final int OUT_X = 8, OUT_Y = 72, OUT_W = 180, OUT_H = 14;
    // 输出框右侧"全屏"按钮 x=192（y 跟随输出框，在 initGui 中用 OUT_Y）
    private static final int FS_OUT_X = 192;
    // ===================== 红石强度行 =====================
    // "强度:" 标签左上角 (8,90)（文字绘制时 y 再 +2 与按钮垂直居中）
    private static final int RS_LABEL_X = 8, RS_Y = 90;
    // [-] 按钮 x=48、[+] 按钮 x=86，均宽 18、高 14
    private static final int RS_MINUS_X = 48, RS_PLUS_X = 86, RS_BTN_W = 18, RS_BTN_H = 14;
    // 当前红石强度数值文字位置 (70,92)（画在 -/+ 两个按钮之间）
    private static final int RS_VAL_X = 70, RS_VAL_Y = 92;
    // ===================== 保存按钮（id=12） =====================
    // 保存按钮左上角 (185,120)，宽 52、高 16（位于面板右下区域）
    private static final int SAVE_X = 185, SAVE_Y = 120, SAVE_W = 52, SAVE_H = 16;

    // 玩家背包槽位不在本类定义：槽实例坐标（x=43+col*18、四排 y=156/174/192/214）在 ContainerActuator 中，
    // 贴图格子本体位置与之逐像素对齐；改槽位必须同时改 ContainerActuator 与贴图（或只改贴图后同步坐标）。

    // ===================== 未保存草稿（静态，六面各一份） =====================
    // 全屏编辑器"保存"只写入这里（条件表达式草稿）；主界面保存并收到服务端确认后才置 null。
    // 用静态是因为打开/关闭全屏浮窗会重建本 GUI 实例，普通成员会丢草稿；1.7.10 同时只有一个 GUI，无并发问题。
    private static final String[] pendingCond = new String[6];
    // 输出表达式草稿，含义同 pendingCond
    private static final String[] pendingOut = new String[6];

    /** 全屏编辑器"保存"回调：把条件表达式回填到指定面的草稿（仅内存，不落存档） */
    public static void setPendingCond(int side, String expr) {
        // side 越界保护；null 统一转空串
        if (side >= 0 && side < 6) pendingCond[side] = expr == null ? "" : expr;
    }

    /** 全屏编辑器"保存"回调：把输出表达式回填到指定面的草稿（仅内存，不落存档） */
    public static void setPendingOut(int side, String expr) {
        // side 越界保护；null 统一转空串
        if (side >= 0 && side < 6) pendingOut[side] = expr == null ? "" : expr;
    }

    /** 清空全部六个面的草稿（玩家真正退出执行器 GUI 时调用） */
    public static void clearPending() {
        // 逐面把条件/输出草稿都置 null
        for (int i = 0; i < 6; i++) {
            pendingCond[i] = null;
            pendingOut[i] = null;
        }
    }

    // 全屏编辑器跳转标志：true 表示当前正在"主界面→全屏编辑器"的切换过程中，
    // 此时旧主界面的 onGuiClosed 会被触发，但不能清草稿（草稿还要带回主界面）。
    private static boolean editorOpen = false;

    /** 由全屏编辑器在打开前置 true、主界面 initGui 完成后置 false */
    public static void setEditorOpen(boolean open) {
        editorOpen = open;
    }

    // 全屏编辑器返回时要恢复的"当前编辑面"（一次性：主界面 initGui 读取后立即复位为 -1）
    private static int pendingSide = -1;

    /** 全屏编辑器返回主界面前调用：记录应保持选中的面，保证返回后仍停留在该面 */
    public static void setPendingSide(int side) {
        pendingSide = side;
    }

    // 条件表达式输入框实例（initGui 创建）
    private GuiTextField condField;
    // 输出表达式输入框实例
    private GuiTextField outField;
    // 当前正在编辑的面（ForgeDirection.VALID_DIRECTIONS 索引，0=D 1=U 2=N 3=S 4=W 5=E）；默认 DOWN
    private int selectedSide = 0;

    /**
     * 构造方法：Minecraft 在打开 GUI 时 new GuiActuator(container)。
     * 这里设定 GUI 窗口逻辑尺寸 xSize/ySize（决定居中位置与鼠标判定区域）。
     *
     * 【贴图尺寸说明（1.7.10 GUI 贴图必须是 2 的幂）】
     *  - 贴图文件 actuator.png 现为 256x256：原设计内容 247x238 位于左上角，
     *    右侧与下方已用透明像素补全到 256x256。
     *  - 原因：drawTexturedModalRect 内部按 1/256 归一化纹理坐标，若贴图非 2 幂（如 247x238），
     *    内容会被拉伸到 256x256 显示，导致 Photoshop 量到的格子坐标与实际渲染位置差
     *    纵向约 11px、横向约 2~3px。补成 256x256 后 1:1 映射，PS 量到的值直接可用。
     *  - xSize=256 / ySize=255 为实测窗口尺寸，采样整个 256x256 纹理，底图无拉伸显示。
     *  - 手动改 GUI 时：Photoshop 量到的格子坐标（x=43 起、四排 y=156/174/192/214）
     *    直接写进 ContainerActuator 的 Slot 即可，无需比例换算。
     */
    public GuiActuator(Container container) {
        super(container);                                              // 交给父类保存容器引用
        this.tile = ((ContainerActuator) container).tileActuator;     // 从容器取出执行器 TileEntity
        this.xSize = 246;                                             // GUI 窗口宽（像素）→ 影响水平居中
        this.ySize = 238;                                             // GUI 窗口高（像素）→ 影响垂直居中
    }

    /** 初始化/重建界面：创建输入框、注册全部按钮（每次打开 GUI 及窗口缩放都会调用） */
    @Override
    public void initGui() {
        super.initGui();                                               // 必须先调父类（生成按钮列表、计算 guiLeft/guiTop）
        // 若从全屏编辑器返回，恢复之前选中的面；读取后立即复位（一次性消费）
        if (pendingSide >= 0 && pendingSide < 6) {
            selectedSide = pendingSide;
            pendingSide = -1;
        }
        // 开启 LWJGL 按键重复事件：按住 Backspace/Delete/左右方向键可持续删除/移动光标（原版默认关闭）
        org.lwjgl.input.Keyboard.enableRepeatEvents(true);
        // 创建条件表达式输入框（坐标用 GUI 相对坐标，绘制时父类已 translate，切勿在此加 guiLeft/guiTop）
        this.condField = new GuiTextField(this.fontRendererObj, COND_X, COND_Y, COND_W, COND_H);
        this.condField.setMaxStringLength(500);                        // 表达式最长 500 字符
        // 创建输出表达式输入框（打开 GUI 不自动聚焦，玩家点击后才进入编辑，避免误触键盘）
        this.outField = new GuiTextField(this.fontRendererObj, OUT_X, OUT_Y, OUT_W, OUT_H);
        this.outField.setMaxStringLength(500);                         // 表达式最长 500 字符
        loadSideConfig(selectedSide);                                  // 把当前面已有配置/草稿载入两个输入框

        // 注册六个方向按钮：id=0~5；按钮坐标必须加 guiLeft/guiTop（屏幕绝对坐标）
        for (int i = 0; i < 6; i++) {
            this.buttonList.add(new GuiButton(i, guiLeft + DIR_X[i], guiTop + DIR_Y[i], BTN, BTN, DIR_LABEL[i]));
        }
        // 条件框"全屏"按钮：id=13，y 与条件框对齐（COND_Y）
        this.buttonList.add(new GuiButton(13, guiLeft + FS_COND_X, guiTop + COND_Y, FS_W, FS_H, "全屏"));
        // 输出框"全屏"按钮：id=14，y 与输出框对齐（OUT_Y）
        this.buttonList.add(new GuiButton(14, guiLeft + FS_OUT_X, guiTop + OUT_Y, FS_W, FS_H, "全屏"));
        // 红石强度 [-] 按钮：id=10
        this.buttonList.add(new GuiButton(10, guiLeft + RS_MINUS_X, guiTop + RS_Y, RS_BTN_W, RS_BTN_H, "-"));
        // 红石强度 [+] 按钮：id=11
        this.buttonList.add(new GuiButton(11, guiLeft + RS_PLUS_X, guiTop + RS_Y, RS_BTN_W, RS_BTN_H, "+"));
        // 保存按钮：id=12
        this.buttonList.add(new GuiButton(12, guiLeft + SAVE_X, guiTop + SAVE_Y, SAVE_W, SAVE_H, "保存"));
        // 绑定远程执行器按钮：id=15（保存按钮下方），点击列出同网络执行器供选择
        this.buttonList.add(new GuiButton(15, guiLeft + SAVE_X, guiTop + SAVE_Y + SAVE_H + 4, SAVE_W, SAVE_H, "绑定源"));
        // 已回到主界面：复位跳转标志，此后玩家真正关闭 GUI 时才允许清空草稿
        editorOpen = false;
    }

    /**
     * 把指定面的配置载入两个输入框。
     * 优先级：未保存草稿 pending（玩家在全屏编辑器里改过但还没点主界面保存）> 执行器已落档配置。
     */
    private void loadSideConfig(int side) {
        if (condField != null) {                                       // 条件框已创建才操作
            // 有草稿用草稿，否则读 TileEntity 中已保存的条件表达式
            String t = pendingCond[side] != null ? pendingCond[side] : tile.getCondition(side);
            condField.setText(t == null ? "" : t);                    // null 安全转空串
        }
        if (outField != null) {                                        // 输出框已创建才操作
            // 有草稿用草稿，否则读已保存的输出表达式
            String t = pendingOut[side] != null ? pendingOut[side] : tile.getOutputExpr(side);
            outField.setText(t == null ? "" : t);                      // null 安全转空串
        }
    }

    /**
     * 服务端保存成功确认回调（由 PacketActuatorSaveAck 在客户端网络线程→主线程调用）：
     * 清掉该面草稿，并在聊天栏提示"保存成功（面: 方向）"。保存失败时服务端不回包，故无提示。
     */
    public static void onSaveAck(int x, int y, int z, int side) {
        if (side >= 0 && side < 6) {                                   // 合法面才清草稿
            pendingCond[side] = null;
            pendingOut[side] = null;
        }
        Minecraft mc = Minecraft.getMinecraft();                       // 取客户端单例
        if (mc.thePlayer != null) {                                    // 玩家已进世界才发消息
            // 方向名（DOWN/UP/NORTH/SOUTH/WEST/EAST），非法时显示 "?"
            String dirName = (side >= 0 && side < 6)
                    ? ForgeDirection.VALID_DIRECTIONS[side].name() : "?";
            // §a=绿色；中文为 Unicode 转义（"[执行器] 保存成功 (面: XXX)"）
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    "\u00a7a[\u6267\u884c\u5668] \u4fdd\u5b58\u6210\u529f (\u9762: " + dirName + ")"));
        }
    }

    /** 按钮点击分发（id 含义见 initGui 注册处） */
    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id >= 0 && button.id < 6) {
            // id 0~5：切换当前编辑面（方向合一：当前面即输出面，条件/输出表达式/红石强度都挂这一面）
            selectedSide = DIR_IDX[button.id];                         // 显示序映射为真实方向索引
            loadSideConfig(selectedSide);                              // 载入该面配置；不自动聚焦输入框
        } else if (button.id == 10) {
            // [-]：红石强度 -1，下限 0
            int cur = tile.getRedstoneLevel(selectedSide);
            tile.setRedstoneLevel(selectedSide, Math.max(0, cur - 1));
        } else if (button.id == 11) {
            // [+]：红石强度 +1，上限 15（红石信号满值）
            int cur = tile.getRedstoneLevel(selectedSide);
            tile.setRedstoneLevel(selectedSide, Math.min(15, cur + 1));
        } else if (button.id == 12) {
            // [保存]：收集两个输入框文本与当前红石强度，打包发服务端；服务端校验通过才落档并回确认
            String expr = condField.getText();                        // 条件表达式
            String outExpr = outField.getText();                      // 输出表达式
            int rs = tile.getRedstoneLevel(selectedSide);             // 红石强度（已由 +/- 改到 tile 客户端实例）
            MinecraftIotMod.network.sendToServer(                      // 通过 SimpleNetworkWrapper 发 C→S 包
                    new PacketActuatorConfig(tile.xCoord, tile.yCoord, tile.zCoord,
                            selectedSide, expr, rs, outExpr));
        } else if (button.id == 13) {
            // 条件框[全屏]：先标记跳转（防止旧 GUI 关闭时误清草稿），再打开全屏表达式编辑器
            setEditorOpen(true);
            // 参数：tile、容器、当前面、编辑类型 0=条件、初始文本
            Minecraft.getMinecraft().displayGuiScreen(new GuiActuatorExprEditor(
                    tile, (ContainerActuator) this.inventorySlots, selectedSide, 0, condField.getText()));
        } else if (button.id == 14) {
            // 输出框[全屏]：同上，编辑类型 1=输出表达式
            setEditorOpen(true);
            Minecraft.getMinecraft().displayGuiScreen(new GuiActuatorExprEditor(
                    tile, (ContainerActuator) this.inventorySlots, selectedSide, 1, outField.getText()));
        } else if (button.id == 15) {
            // [绑定源]：请求同网络内执行器列表，服务端下发后弹 GuiActuatorSelect 选择
            MinecraftIotMod.network.sendToServer(
                    new com.cyclone.minecraftiot.network.PacketActuatorListRequest(
                            tile.xCoord, tile.yCoord, tile.zCoord));
        }
    }

    /**
     * 背景层绘制（每帧、在槽位/文字之前画）：本层坐标为屏幕绝对坐标。
     * 绘制顺序：先画底图 → 再画两个执行器槽的手绘槽框（槽内物品由父类在其后统一渲染）。
     */
    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        // 绑定底图纹理
        this.mc.getTextureManager().bindTexture(background);
        // 从贴图 (0,0) 起取 xSize*ySize 区域，1:1 画到屏幕 (guiLeft, guiTop)；
        // 贴图已把格子区下移/右移做好，代码里不再做任何偏移。
        this.drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
        // 绑定卡槽的 18x18 凹陷槽框（比槽位左上各扩 1px）
        drawSlotBox(guiLeft + SLOT_CONN_X, guiTop + SLOT_Y);
        // 技能插件槽的 18x18 凹陷槽框
        drawSlotBox(guiLeft + SLOT_SKILL_X, guiTop + SLOT_Y);
    }

    /**
     * 手绘一个 18x18 物品槽凹陷框（中心 16x16 放物品）。
     * @param x 槽左上角屏幕绝对 x（调用处已加 guiLeft）
     * @param y 槽左上角屏幕绝对 y（调用处已加 guiTop）
     */
    private void drawSlotBox(int x, int y) {
        // 外层 18x18：深蓝灰边框 (x-1,y-1)~(x+17,y+17)
        drawRect(x - 1, y - 1, x + 17, y + 17, 0xFF3A3A44);
        // 内层 16x16：近黑凹槽底 (x,y)~(x+16,y+16)，物品图标将盖在其上
        drawRect(x, y, x + 16, y + 16, 0xFF101014);
    }

    /**
     * 前景层绘制（每帧、在背景层与槽位之后画）：本层已 translate，直接用 GUI 相对坐标。
     * 负责：槽位标签、条件/输出标签、红石强度数值、三段状态行、门控红字、两个输入框。
     */
    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // 绑定卡槽下方标签"卡"（槽 y=6，标签放其下 y=24）
        this.fontRendererObj.drawString("卡", SLOT_CONN_X, SLOT_Y + 18, 0x404040);
        // 技能插件槽下方标签"插件"
        this.fontRendererObj.drawString("插件", SLOT_SKILL_X, SLOT_Y + 18, 0x404040);
        // 条件框标签，括号内是当前编辑面方向名（如 DOWN），位置 (8,34)
        this.fontRendererObj.drawString("条件 (" + ForgeDirection.VALID_DIRECTIONS[selectedSide].name() + "):", 8, 34, 0x404040);
        // 输出框标签，位置 (8,62)
        this.fontRendererObj.drawString("输出表达式:", 8, 62, 0x404040);
        // "强度:" 标签（y+2 与 14px 高按钮视觉居中）
        this.fontRendererObj.drawString("强度:", RS_LABEL_X, RS_Y + 2, 0x404040);
        // 当前红石强度数字（0~15）
        this.fontRendererObj.drawString(String.valueOf(tile.getRedstoneLevel(selectedSide)), RS_VAL_X, RS_VAL_Y + 2, 0x404040);
        // ---- 状态行 y=108：三段，绿=正常/红=缺失/蓝=已连 ----
        // 第 1 段 x=8：绑定卡状态
        String connStatus = tile.hasConnector() ? "绑定卡: 已插" : "绑定卡: 未插";
        this.fontRendererObj.drawString(connStatus, 8, 108, tile.hasConnector() ? 0x228B22 : 0xAA0000);
        // 第 2 段 x=66：技能插件状态（已装时显示插件名）
        String skillName = tile.getSkillName();
        String skillStatus = skillName == null ? "插件: 未装" : "插件: " + skillName;
        this.fontRendererObj.drawString(skillStatus, 66, 108, skillName != null ? 0x228B22 : 0xAA0000);
        // 第 3 段 x=126：路由器连接状态
        String routerStatus = tile.isRouterBound() ? "路由: 已连" : "路由: 未连";
        this.fontRendererObj.drawString(routerStatus, 126, 108, tile.isRouterBound() ? 0x2266BB : 0xAA0000);
        // 未连接路由器时，状态行下方 y=122 显示红色门控提示（连上后不画）
        if (!tile.isRouterBound()) {
            this.fontRendererObj.drawString("未连路由: 不执行任何动作", 8, 122, 0xAA0000);
        } else {
            // 已连路由：显示远程物品源绑定状态（insert() 的物品来源）
            String srcStatus = tile.isRemoteActBound()
                    ? ("物品源: (" + tile.getRemoteActX() + ", " + tile.getRemoteActY() + ", " + tile.getRemoteActZ() + ")")
                    : "物品源: 本机";
            this.fontRendererObj.drawString(srcStatus, 8, 122, tile.isRemoteActBound() ? 0x2266BB : 0x666666);
        }

        // 两个文本输入框自身绘制（含边框、文本、光标）；放最后画以盖在面板底纹之上
        condField.drawTextBox();
        outField.drawTextBox();
    }

    /**
     * 键盘字符事件路由：焦点在哪个输入框就把按键交给谁；都没聚焦时交给父类
     * （父类处理 E 关闭、Shift 点击等默认行为）。
     */
    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (condField.isFocused()) {
            // 焦点在条件框：字符/退格/左右键等交给条件框
            condField.textboxKeyTyped(typedChar, keyCode);
        } else if (outField.isFocused()) {
            // 焦点在输出框：交给输出框
            outField.textboxKeyTyped(typedChar, keyCode);
        } else {
            // 无焦点：走容器默认（Esc/E 关闭 GUI 等）
            super.keyTyped(typedChar, keyCode);
        }
    }

    /**
     * 鼠标点击：先让父类处理按钮/槽位，再做输入框聚焦判定。
     * 输入框用 GUI 相对坐标创建，故点击坐标要减去 guiLeft/guiTop 才能正确命中。
     */
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);                    // 按钮、槽位点击
        // 点中条件框则聚焦，点其外则失焦（GuiTextField 内部判定）
        condField.mouseClicked(mouseX - guiLeft, mouseY - guiTop, mouseButton);
        // 同上，输出框
        outField.mouseClicked(mouseX - guiLeft, mouseY - guiTop, mouseButton);
    }

    /** 每 tick 刷新：驱动输入框光标闪烁（不调用则光标不闪/不更新） */
    @Override
    public void updateScreen() {
        super.updateScreen();
        condField.updateCursorCounter();   // 条件框光标计时
        outField.updateCursorCounter();    // 输出框光标计时
    }

    /**
     * GUI 关闭回调（Esc/E/打开其他界面都会触发）。
     * 默认清空未保存草稿；但若正跳往全屏编辑器（editorOpen=true）则保留——草稿需跨界面往返。
     */
    @Override
    public void onGuiClosed() {
        if (!editorOpen) {                 // 非跳转编辑器 = 玩家真正退出 → 清草稿
            clearPending();
        }
        super.onGuiClosed();
    }

    /**
     * 槽位点击的底层入口。这里做"窗口号对齐"防御：
     * 经 displayGuiScreen 重开 GUI 时容器可能被重建、windowId 与玩家当前 openContainer 分叉，
     * 服务端会因窗口号不匹配丢弃点击包，表现为"槽里的卡第一次拿不出来"。
     * 每次点击前把本容器窗口号强制对齐到 thePlayer.openContainer.windowId 即可规避。
     */
    @Override
    protected void handleMouseClick(Slot slot, int slotId, int clickedButton, int clickType) {
        // 玩家与容器存在、且窗口号不一致时，以玩家当前 openContainer 的窗口号为准
        if (mc.thePlayer != null && mc.thePlayer.openContainer != null
                && this.inventorySlots.windowId != mc.thePlayer.openContainer.windowId) {
            this.inventorySlots.windowId = mc.thePlayer.openContainer.windowId;
        }
        super.handleMouseClick(slot, slotId, clickedButton, clickType);   // 再执行正常点击逻辑
    }
}
