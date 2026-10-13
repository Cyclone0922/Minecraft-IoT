package com.cyclone.minecraftiot.block;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.tileentity.TileItemBuffer;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/**
 * 物品缓存器方块（0.5.0）。
 * 右键打开只读 GUI（AE2 面板风格，不能鼠标取放）。
 * 被破坏时：
 *  - 正常状态：只减容量，物品在路由器，不掉
 *  - 灾备状态：若组内仍有残余缓存器 → 溢出部分掉落；最后一个被拆 → 全部掉落
 */
public class BlockItemBuffer extends Block {

    public BlockItemBuffer() {
        super(Material.iron);
        setHardness(3.0F);
        setResistance(15.0F);
        setBlockName("item_buffer");
        setCreativeTab(CreativeTabs.tabMisc);
        setBlockTextureName("minecraftiot:item_buffer");
    }

    @Override
    public boolean hasTileEntity(int metadata) { return true; }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileItemBuffer();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
                                    int side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        player.openGui(MinecraftIotMod.instance, 5, world, x, y, z); // GUI_ITEM_BUFFER=5
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileItemBuffer) {
                TileItemBuffer buf = (TileItemBuffer) te;
                if (buf.isDrMode()) {
                    // 灾备：先算残余组容量；拆掉这一块后组内剩余容量
                    com.cyclone.minecraftiot.util.ItemBufferCluster c =
                            com.cyclone.minecraftiot.util.ItemBufferCluster.resolve(world, x, y, z);
                    if (c != null && c.blockCount > 1) {
                        // 仍有残余缓存器：溢出部分掉落（下东南主控还持有池）
                        TileItemBuffer master = buf.getDrMaster();
                        if (master != null) {
                            int remaining = (c.blockCount - 1) * com.cyclone.minecraftiot.util.ItemBufferCluster.PER_BLOCK_ITEM_CAP;
                            for (ItemStack s : master.drainDrPoolExcess(remaining)) {
                                dropStack(world, x, y, z, s);
                            }
                        }
                    } else {
                        // 最后一个灾备缓存器被拆：全部掉落
                        for (ItemStack s : buf.getDrPoolItems()) {
                            dropStack(world, x, y, z, s);
                        }
                    }
                }
                // 正常状态：物品在路由器，不掉（只减容量，由路由器灾备逻辑处理）
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    private static void dropStack(World world, int x, int y, int z, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return;
        float f = 0.7F;
        double dx = world.rand.nextFloat() * f + (1.0F - f) * 0.5D;
        double dy = world.rand.nextFloat() * f + (1.0F - f) * 0.5D;
        double dz = world.rand.nextFloat() * f + (1.0F - f) * 0.5D;
        net.minecraft.entity.item.EntityItem e =
                new net.minecraft.entity.item.EntityItem(world, x + dx, y + dy, z + dz, stack);
        e.delayBeforeCanPickup = 10;
        world.spawnEntityInWorld(e);
    }

    /** 是否可被漏斗输入（正常状态可，灾备不可）——由 TileEntity.isItemValidForSlot 控制 */
    @Override
    public boolean hasComparatorInputOverride() { return true; }

    @Override
    public int getComparatorInputOverride(World world, int x, int y, int z, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileItemBuffer) {
            TileItemBuffer buf = (TileItemBuffer) te;
            int total = buf.isDrMode() ? buf.getDrPoolTotal() : buf.getActivePoolTotal();
            // 0~15：按物品总量相对容量比例
            int cap = buf.isDrMode() ? buf.getItemCapacity() : buf.getActivePoolItemCapacity();
            if (cap <= 0) return 0;
            return Math.min(15, total * 15 / cap);
        }
        return 0;
    }
}
