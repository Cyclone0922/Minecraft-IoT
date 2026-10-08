package com.cyclone.minecraftiot.client;

import com.cyclone.minecraftiot.SensorDisplayMod;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.Language;
import net.minecraft.client.resources.LanguageManager;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * 在 1.7.10 dev 环境强制简体中文。
 *
 * 背景：此环境 LanguageManager 的"语言列表"（parseLanguageMetadata 读各资源包 pack.mcmeta 的 language 段）
 * 连默认 en_US 都没注册，导致选项-语言界面一直为空、无法手动切中文。
 * 但"翻译加载"不依赖该列表：onResourceManagerReload 直接按 currentLanguage 字符串
 * 加载 en_US+当前语言（Locale 走直接资源路径），英文兜底、中文覆盖。
 * 因此这里在首个客户端 tick：把 zh_CN 注入语言表（让界面有"简体中文"项）+
 * 设为当前语言 + 重载翻译，游戏直接变中文。
 */
public class LanguageForcer {
    private static boolean applied = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (applied) return;
        if (event.phase != TickEvent.Phase.END) return;
        applied = true;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) return;
            LanguageManager lm = mc.getLanguageManager();
            if (lm == null) return;
            Language zh = new Language("zh_CN", "\u7b80\u4f53\u4e2d\u6587", "\u4e2d\u56fd", false);

            // 1) 把 zh_CN 注入语言表，让 选项-语言 界面出现"简体中文"、getCurrentLanguage 正常返回
            try {
                Field f = LanguageManager.class.getDeclaredField("languageMap");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, Language> map = (Map<String, Language>) f.get(lm);
                if (map != null) map.put("zh_CN", zh);
            } catch (Throwable t) {
                SensorDisplayMod.log.warn("[LanguageForcer] inject languageMap failed (non-fatal): " + t);
            }

            // 2) 设为当前语言并重载翻译（en_US 兜底 + zh_CN 覆盖）
            lm.setCurrentLanguage(zh);
            lm.onResourceManagerReload(mc.getResourceManager());
            if (mc.gameSettings != null) {
                mc.gameSettings.language = "zh_CN";
                mc.gameSettings.saveOptions();
            }
            SensorDisplayMod.log.info("[LanguageForcer] forced current language to zh_CN, translations reloaded");
        } catch (Throwable t) {
            SensorDisplayMod.log.error("[LanguageForcer] failed: ", t);
        }
    }
}
