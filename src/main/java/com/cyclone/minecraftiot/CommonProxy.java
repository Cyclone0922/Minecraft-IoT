package com.cyclone.minecraftiot;

import com.cyclone.minecraftiot.gui.GuiHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;

public class CommonProxy {

    /**
     * 服务端和客户端共用的注册方法
     * 在主类 preInit/init 中调用
     */
    public void registerBlocks(Block block, String name) {
        GameRegistry.registerBlock(block, name);
    }

    public void registerItem(Item item, String name) {
        GameRegistry.registerItem(item, name);
    }

    public void registerTileEntity(Class<? extends TileEntity> clazz, String id) {
        GameRegistry.registerTileEntity(clazz, id);
    }

    /**
     * 注册 GUI 处理器（通用）
     */
    public void registerGuiHandler() {
        NetworkRegistry.INSTANCE.registerGuiHandler(SensorDisplayMod.instance, new GuiHandler());
    }

    /**
     * 客户端特有的注册（在客户端代理中覆写）
     */
    public void registerRenderers() {
        // 客户端渲染注册，留空由 ClientProxy 实现
    }
}