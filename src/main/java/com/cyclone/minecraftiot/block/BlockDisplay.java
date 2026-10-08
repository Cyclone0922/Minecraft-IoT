package com.cyclone.minecraftiot.block;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.tileentity.TileDisplay;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class BlockDisplay extends Block {

    private IIcon iconDisplay;        // 显示面（屏幕，全边框，用于物品/无邻居渲染）
    private IIcon iconSide;           // 非显示面（外壳）
    private IIcon[] connIcons;        // 16 张连接变体（上下左右各边是否连接）

    public BlockDisplay() {
        super(Material.rock);
        setHardness(2.0F);
        setResistance(10.0F);
        setBlockName("display");
        setCreativeTab(CreativeTabs.tabMisc);
    }

    // 元数据 = 原版"面"索引：0=下(DOWN) 1=上(UP) 2=北(NORTH) 3=南(SOUTH) 4=西(WEST) 5=东(EAST)
    // 与 Block.getIcon 的 side 参数约定一致，因此 getIcon(side,meta) 可据此区分显示面。

    @Override
    public void registerBlockIcons(IIconRegister reg) {
        this.iconDisplay = reg.registerIcon("minecraftiot:display");
        this.iconSide = reg.registerIcon("minecraftiot:display_side");
        this.connIcons = new IIcon[16];
        for (int i = 0; i < 16; i++) {
            this.connIcons[i] = reg.registerIcon("minecraftiot:display_conn_" + i);
        }
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        return side == meta ? iconDisplay : iconSide;
    }

    // 世界内渲染：显示面根据相邻同朝向显示器选择连接变体，实现无边框拼接
    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        int meta = world.getBlockMetadata(x, y, z);
        if (side != meta) return iconSide;

        int index = 0;
        ForgeDirection face = dirFromMeta(meta);
        if (face == ForgeDirection.UP || face == ForgeDirection.DOWN) {
            // 水平面（上下朝向）：上下边=z±1，左右边=x±1
            if (isConn(world, x, y, z - 1, meta)) index |= 8;
            if (isConn(world, x, y, z + 1, meta)) index |= 4;
            if (isConn(world, x - 1, y, z, meta)) index |= 2;
            if (isConn(world, x + 1, y, z, meta)) index |= 1;
        } else {
            // 竖直面：上下边=y±1
            if (isConn(world, x, y + 1, z, meta)) index |= 8;
            if (isConn(world, x, y - 1, z, meta)) index |= 4;
            // 注意：Minecraft 会水平镜像北面和东面的贴图（南面/西面正常），
            // 因此北面/东面的左右连接判断要对调，拼接才正确。
            if (face == ForgeDirection.SOUTH) {          // 南面：正常方向
                if (isConn(world, x - 1, y, z, meta)) index |= 2;
                if (isConn(world, x + 1, y, z, meta)) index |= 1;
            } else if (face == ForgeDirection.NORTH) {   // 北面：水平镜像，左右对调
                if (isConn(world, x + 1, y, z, meta)) index |= 2;
                if (isConn(world, x - 1, y, z, meta)) index |= 1;
            } else if (face == ForgeDirection.WEST) {    // 西面：正常方向
                if (isConn(world, x, y, z - 1, meta)) index |= 2;
                if (isConn(world, x, y, z + 1, meta)) index |= 1;
            } else {                                     // EAST 东面：水平镜像，左右对调
                if (isConn(world, x, y, z + 1, meta)) index |= 2;
                if (isConn(world, x, y, z - 1, meta)) index |= 1;
            }
        }
        return connIcons[index];
    }

    /** 邻居是否是同朝向的显示器（可与本块拼接） */
    private boolean isConn(IBlockAccess world, int nx, int ny, int nz, int meta) {
        if (world == null) return false;
        Block b = world.getBlock(nx, ny, nz);
        return b instanceof BlockDisplay && world.getBlockMetadata(nx, ny, nz) == meta;
    }

    /** 元数据(原版面索引 0..5) -> ForgeDirection */
    private ForgeDirection dirFromMeta(int meta) {
        switch (meta) {
            case 0: return ForgeDirection.DOWN;
            case 1: return ForgeDirection.UP;
            case 2: return ForgeDirection.NORTH;
            case 4: return ForgeDirection.WEST;
            case 5: return ForgeDirection.EAST;
            case 3:
            default: return ForgeDirection.SOUTH;
        }
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileDisplay();
    }

    // 放置时根据玩家朝向保存显示面（水平四向；上下两面可由 GUI 调整）
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase player, ItemStack stack) {
        int l = MathHelper.floor_double((player.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
        int meta;
        switch (l) {
            case 0: meta = 2; break; // NORTH
            case 1: meta = 5; break; // EAST
            case 2: meta = 3; break; // SOUTH
            default: meta = 4; break; // WEST
        }
        world.setBlockMetadataWithNotify(x, y, z, meta, 2);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            player.openGui(MinecraftIotMod.instance, 1, world, x, y, z);
        }
        return true;
    }
}
