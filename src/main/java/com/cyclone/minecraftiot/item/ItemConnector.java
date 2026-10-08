package com.cyclone.minecraftiot.item;

import com.cyclone.minecraftiot.util.ConnectorConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

public class ItemConnector extends Item {
    public ItemConnector() {
        setUnlocalizedName("connector");
        setTextureName("minecraftiot:connector"); // 物品图集自动拼 textures/items/，贴图位于 textures/items/connector.png
    }

    // 默认无特殊行为，只是一个存储NBT的容器
    @Override
    public boolean hasEffect(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().hasKey("sensorX");
    }

    /**
     * shift+右键（对方块）：
     *   第一次 → 清空显示配置（恢复"原始NBT"模式，不再套用任何友好方案）；
     *   第二次 → 清空全部绑定信息，恢复出厂设置。
     * 返回 true 阻止默认行为（不放置/不触发方块交互）。
     */
    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z,
                                  int side, float hitX, float hitY, float hitZ) {
        if (world.isRemote || stack == null || stack.getItem() != this) return false;
        if (!player.isSneaking()) return false;
        return factoryResetStep(stack, player);
    }

    /**
     * shift+右键（对空气）：同上逻辑。
     * 有配置/绑定时执行并返回该 stack（不触发其他行为），否则正常返回。
     */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote || stack == null || stack.getItem() != this) return stack;
        if (player.isSneaking()) {
            factoryResetStep(stack, player);
        }
        return stack;
    }

    /** 一步重置逻辑：先清配置，再清绑定；两者皆无可做时提示玩家 */
    private boolean factoryResetStep(ItemStack stack, EntityPlayer player) {
        if (ConnectorConfig.hasConfig(stack)) {
            ConnectorConfig.clearConfig(stack);
            player.addChatMessage(new ChatComponentText("\u00a7e[\u8fde\u63a5\u5668] \u5df2\u6e05\u7a7a\u663e\u793a\u914d\u7f6e\uff0c\u53ea\u663e\u793a\u539f\u59cb NBT\u3002\u518d\u6b21 shift+\u53f3\u952e\u53ef\u6e05\u7a7a\u7ed1\u5b9a\u3002"));
            return true;
        }
        if (ConnectorConfig.hasBinding(stack)) {
            ConnectorConfig.clearBinding(stack);
            player.addChatMessage(new ChatComponentText("\u00a7e[\u8fde\u63a5\u5668] \u5df2\u6e05\u7a7a\u5168\u90e8\u7ed1\u5b9a\uff0c\u6062\u590d\u51fa\u5382\u8bbe\u7f6e\u3002"));
            return true;
        }
        player.addChatMessage(new ChatComponentText("\u00a77[\u8fde\u63a5\u5668] \u672a\u7ed1\u5b9a\u4e14\u65e0\u914d\u7f6e\uff0c\u65e0\u9700\u91cd\u7f6e\u3002"));
        return true;
    }
}
