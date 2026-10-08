package com.cyclone.sensordisplay.block;

import com.cyclone.sensordisplay.SensorDisplayMod;
import com.cyclone.sensordisplay.tileentity.TileConnectorWorkbench;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 连接器工作台（MineCraft IoT 重构核心方块）。
 *
 * 连接器（绑定卡）的显示配置不再写入 sensordisplay.json 配置文件，
 * 而是通过工作台直接写入卡自身的 NBT：
 *   - 配置自定义表达式友好信息
 *   - 设置为默认友好显示方案
 *   - 清空为原始 NBT
 *   - 清空绑定信息（恢复出厂）
 *   - 重命名（含自动默认命名）
 *   - 拷贝配置 / 拷贝全部（两卡绑定同一类机器时）
 *
 * 传感器只负责读 NBT 与绑定方向，不再提供自定义表达式编辑。
 */
public class BlockConnectorWorkbench extends Block {

    public BlockConnectorWorkbench() {
        super(Material.iron);
        setHardness(2.5F);
        setResistance(12.0F);
        setBlockName("connector_workbench");
        setCreativeTab(CreativeTabs.tabMisc);
        setBlockTextureName("sensordisplay:connector_workbench");
    }

    @Override
    public boolean hasTileEntity(int metadata) { return true; }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileConnectorWorkbench();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
                                    int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            player.openGui(SensorDisplayMod.instance, 4, world, x, y, z); // GUI_WORKBENCH=4
        }
        return true;
    }
}
