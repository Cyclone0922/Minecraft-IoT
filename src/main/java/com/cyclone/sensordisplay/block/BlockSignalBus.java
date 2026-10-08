package com.cyclone.sensordisplay.block;

import com.cyclone.sensordisplay.tileentity.TileSignalBus;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 信号总线方块：技能0 信号域的线性中继（方向直通）。
 * 无 GUI、无配置，纯转发。暂不添加合成表（创造模式测试）。
 */
public class BlockSignalBus extends Block {

    public BlockSignalBus() {
        super(Material.iron);
        setHardness(1.5F);
        setResistance(8.0F);
        setBlockName("signal_bus");
        setCreativeTab(CreativeTabs.tabMisc);
        setBlockTextureName("sensordisplay:signal_bus");
    }

    @Override
    public boolean hasTileEntity(int metadata) { return true; }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileSignalBus();
    }
}
