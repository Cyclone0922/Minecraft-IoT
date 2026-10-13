package com.cyclone.minecraftiot.gui;

import com.cyclone.minecraftiot.tileentity.TileRouter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/**
 * 路由器 GUI 容器（0.5.1 测试用）：
 *  - 无物品槽位（路由器不存物品，物品在网络池）
 *  - 仅用于打开 GUI 显示 IE 存量 + 充电输入框
 */
public class ContainerRouter extends Container {
    public final TileRouter router;
    public final EntityPlayer player;

    public ContainerRouter(EntityPlayer player, TileRouter router) {
        this.player = player;
        this.router = router;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return router != null && !router.isInvalid();
    }
}
