package com.cyclone.minecraftiot.item;

import net.minecraft.item.Item;

/**
 * 技能插件基类。
 * 执行器空装出厂，必须插入对应技能插件才能获得该能力。
 * 每种技能有唯一 skillId，执行器按 skillId 判定已安装的技能。
 */
public abstract class ItemSkill extends Item {

    private final String skillId;

    protected ItemSkill(String skillId) {
        this.skillId = skillId;
        setMaxStackSize(16);
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.tabMisc);
    }

    public String getSkillId() { return skillId; }

    /** 技能的可读名称（用于 GUI 显示） */
    public abstract String getSkillName();
}
