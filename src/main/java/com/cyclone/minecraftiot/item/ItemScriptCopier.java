package com.cyclone.minecraftiot.item;

import com.cyclone.minecraftiot.tileentity.TileActuator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

/**
 * 脚本拷贝器。
 *  - 右键执行器：拷贝该执行器的全部设置脚本（六面条件表达式 / 输出表达式 / 红石强度）到本物品 NBT
 *  - Shift + 右键执行器：把本物品携带的脚本写入目标执行器（覆盖对应面配置）
 */
public class ItemScriptCopier extends Item {

    public ItemScriptCopier() {
        setMaxStackSize(1);
        setUnlocalizedName("script_copier");
        setTextureName("minecraftiot:script_copier");
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.tabMisc);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world,
                             int x, int y, int z, int side,
                             float hitX, float hitY, float hitZ) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileActuator)) return false;
        if (world.isRemote) return true;
        TileActuator act = (TileActuator) te;
        if (player.isSneaking()) {
            // Shift 右键：写入脚本
            if (stack.getTagCompound() == null || !stack.getTagCompound().hasKey("Script")) {
                player.addChatMessage(new ChatComponentText(
                        "\u00a7c[\u6267\u884c\u5668] \u4e0d\u662f\u4e00\u4efd\u5df2\u590d\u5236\u7684\u811a\u672c\uff08\u5148\u53f3\u952e\u590d\u5236\uff09"));
                return true;
            }
            NBTTagCompound script = stack.getTagCompound().getCompoundTag("Script");
            for (int i = 0; i < 6; i++) {
                act.setCondition(i, script.hasKey("Cond" + i) ? script.getString("Cond" + i) : "");
                act.setOutputExpr(i, script.hasKey("OutExpr" + i) ? script.getString("OutExpr" + i) : "");
                act.setRedstoneLevel(i, script.hasKey("RS" + i) ? script.getInteger("RS" + i) : 15);
            }
            player.addChatMessage(new ChatComponentText(
                    "\u00a7a[\u6267\u884c\u5668] \u811a\u672c\u5df2\u5199\u5165\uff08\u8986\u76d6\u516d\u9762\u914d\u7f6e\uff09"));
        } else {
            // 右键：拷贝脚本
            NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
            NBTTagCompound script = new NBTTagCompound();
            for (int i = 0; i < 6; i++) {
                String c = act.getCondition(i);
                if (c != null && !c.isEmpty()) script.setString("Cond" + i, c);
                String o = act.getOutputExpr(i);
                if (o != null && !o.isEmpty()) script.setString("OutExpr" + i, o);
                script.setInteger("RS" + i, act.getRedstoneLevel(i));
            }
            tag.setTag("Script", script);
            stack.setTagCompound(tag);
            player.addChatMessage(new ChatComponentText(
                    "\u00a7a[\u6267\u884c\u5668] \u5df2\u590d\u5236\u5f53\u524d\u6267\u884c\u5668\u811a\u672c\uff0cShift\u53f3\u952e\u53ef\u5199\u5165\u5176\u4ed6\u6267\u884c\u5668"));
        }
        return true;
    }
}
