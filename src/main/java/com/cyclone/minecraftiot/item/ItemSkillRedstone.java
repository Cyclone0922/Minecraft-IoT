package com.cyclone.minecraftiot.item;

/**
 * 红石信号技能插件。
 * 安装到执行器后，执行器可在条件成立时从指定面输出红石信号。
 * 红石信号操作不消耗能量（决策）。
 */
public class ItemSkillRedstone extends ItemSkill {

    public static final String SKILL_ID = "redstone";

    public ItemSkillRedstone() {
        super(SKILL_ID);
        setUnlocalizedName("skill_redstone");
        setTextureName("minecraftiot:skill_redstone");
    }

    @Override
    public String getSkillName() { return "红石信号"; }
}
