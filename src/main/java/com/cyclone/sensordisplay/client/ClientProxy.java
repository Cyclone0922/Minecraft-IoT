package com.cyclone.sensordisplay.client;

import com.cyclone.sensordisplay.CommonProxy;
import com.cyclone.sensordisplay.client.renderer.DisplayRenderer;
import com.cyclone.sensordisplay.tileentity.TileDisplay;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;

public class ClientProxy extends CommonProxy {

    @Override
    public void registerRenderers() {
        // 注册显示屏的 TESR
        ClientRegistry.bindTileEntitySpecialRenderer(TileDisplay.class, new DisplayRenderer());
        // 首个客户端 tick 强制简体中文（绕过 1.7.10 dev 环境损坏的语言列表）
        FMLCommonHandler.instance().bus().register(new LanguageForcer());
        // 其他客户端渲染可在此添加
    }
}