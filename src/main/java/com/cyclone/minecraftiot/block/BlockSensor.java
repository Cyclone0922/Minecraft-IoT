package com.cyclone.minecraftiot.block;

import com.cyclone.minecraftiot.SensorDisplayMod;
import com.cyclone.minecraftiot.tileentity.TileSensor;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockSensor extends Block {

    public BlockSensor() {
        super(Material.rock);
        setHardness(2.0F);
        setResistance(10.0F);
        setBlockName("sensor");
        setCreativeTab(CreativeTabs.tabMisc);
        setBlockTextureName("minecraftiot:sensor"); // 1.7.10 方块图集会自动拼 textures/blocks/，不要带 blocks/ 前缀
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileSensor();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            // 打开传感器 GUI
            player.openGui(SensorDisplayMod.instance, 0, world, x, y, z);
        }
        return true;
    }
}