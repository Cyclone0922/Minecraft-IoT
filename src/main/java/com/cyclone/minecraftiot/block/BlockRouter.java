package com.cyclone.minecraftiot.block;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.tileentity.TileRouter;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 路由器方块（P1 单方块）。
 * 网络根节点，持有 IE 能量缓存。右键打开 GUI（P1 暂用简单信息展示，后续扩展）。
 */
public class BlockRouter extends Block {

    public BlockRouter() {
        super(Material.iron);
        setHardness(3.0F);
        setResistance(15.0F);
        setBlockName("router");
        setCreativeTab(CreativeTabs.tabMisc);
        setBlockTextureName("minecraftiot:router");
    }

    @Override
    public boolean hasTileEntity(int metadata) { return true; }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileRouter();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
                                     int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            // P1：右键打印路由器信息到日志，后续做 GUI
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileRouter) {
                TileRouter r = (TileRouter) te;
                MinecraftIotMod.log.info("[Router@" + x + "," + y + "," + z + "]"
                        + " mac=" + r.getMac()
                        + " alias=" + r.getAlias()
                        + " IE=" + r.getEnergy().getIe() + "/" + r.getEnergy().getIeCapacity());
            }
        }
        return true;
    }
}
