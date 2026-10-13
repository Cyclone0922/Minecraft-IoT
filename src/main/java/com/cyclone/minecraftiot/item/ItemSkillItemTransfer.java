package com.cyclone.minecraftiot.item;

/**
 * 物品转移技能插件。
 * 安装到执行器后，输出表达式可调用 take()/insert()：
 *  - take()  从当前求值面（输出面）相邻容器取出物品，存入执行器内部缓存
 *  - insert() 把缓存中的物品放入当前求值面相邻容器
 * 实现"箱子A → 箱子B 全部转移"等组合。
 */
public class ItemSkillItemTransfer extends ItemSkill {

    public static final String SKILL_ID = "item_transfer";

    public ItemSkillItemTransfer() {
        super(SKILL_ID);
        setUnlocalizedName("mciot_skill_item_transfer");
        setTextureName("minecraftiot:skill_item_transfer");
    }

    @Override
    public String getSkillName() { return "物品转移"; }
}
