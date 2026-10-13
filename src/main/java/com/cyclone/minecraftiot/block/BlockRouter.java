package com.cyclone.minecraftiot.block;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.tileentity.TileActuator;
import com.cyclone.minecraftiot.tileentity.TileItemBuffer;
import com.cyclone.minecraftiot.tileentity.TileRouter;
import com.cyclone.minecraftiot.util.ItemBufferCluster;
import com.cyclone.minecraftiot.util.ItemPool;
import com.cyclone.minecraftiot.util.RouterCluster;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 路由器方块。
 * 网络根节点，持有 IE 能量缓存 + 0.5.0 网络物品池。
 * 右键打开 GUI（打印信息）。
 *
 * 被破坏时的物品池处理：
 *  - 组内还有残余路由器：池转移到残余某块，下次访问自动迁移到新主控（不丢）
 *  - 组内最后一个路由器被拆（全毁）+ 有相邻缓存器：池转移到缓存器组（进入灾备）
 *  - 全毁 + 无缓存器：池全部掉落为物品实体
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
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileRouter) {
                TileRouter r = (TileRouter) te;
                MinecraftIotMod.log.info("[Router@" + x + "," + y + "," + z + "]"
                        + " mac=" + r.getMac()
                        + " alias=" + r.getAlias()
                        + " IE=" + r.getEnergy().getIe() + "/" + r.getEnergy().getIeCapacity()
                        + " pool=" + r.getPool().getTotal() + "/" + r.getPoolItemCapacity());
            }
        }
        return true;
    }

    /**
     * 路由器被破坏：
     *  1. 向绑定本路由器的执行器广播"路由器停止"（清空绑定，切换为未连接）
     *  2. 抢救物品池：
     *     - 组内仍有残余路由器 → 池转移到残余块（不丢，masterOf 会自动迁移到新主控）
     *     - 组内最后一个被拆（全毁）→ 转移相邻缓存器组（灾备）或全部掉落
     */
    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (!world.isRemote) {
            // 1. 广播执行器解绑
            for (Object o : world.loadedTileEntityList) {
                if (o instanceof TileActuator) {
                    TileActuator act = (TileActuator) o;
                    if (act.isRouterBound()
                            && act.getRouterX() == x && act.getRouterY() == y && act.getRouterZ() == z) {
                        act.clearRouter();
                    }
                }
            }

            // 2. 抢救物品池（在 super 移除本块 TE 之前）
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileRouter) {
                TileRouter r = (TileRouter) te;
                ItemPool pool = r.getPool();
                boolean hasPool = !pool.isEmpty();

                if (hasPool) {
                    // drMode=true（容量爆/无在线缓存器）：网络已瘫痪，池直接掉落，不再找缓存器灾备
                    if (r.isDrMode()) {
                        for (ItemStack s : pool.drainAll()) {
                            dropStack(world, x, y, z, s);
                        }
                    } else {
                        RouterCluster before = RouterCluster.resolve(world, x, y, z);
                        boolean lastRouter = (before == null || before.blockCount <= 1);
                        if (!lastRouter) {
                            // 组内还有残余路由器：把池转移到任一剩余块（masterOf 下次自动归位）
                            for (int[] b : before.blocks) {
                                if (b[0] == x && b[1] == y && b[2] == z) continue;
                                TileEntity o = world.getTileEntity(b[0], b[1], b[2]);
                                if (o instanceof TileRouter) {
                                    for (ItemStack s : pool.drainAll()) {
                                        ((TileRouter) o).addItemStack(s, s.stackSize);
                                    }
                                    break;
                                }
                            }
                        } else {
                            // 全毁：转移相邻缓存器组（灾备）或掉落
                            TileItemBuffer drTarget = findAdjacentBufferGroup(world, x, y, z);
                            if (drTarget != null) {
                                drTarget.enterDisasterModeFrom(pool);
                            } else {
                                for (ItemStack s : pool.drainAll()) {
                                    dropStack(world, x, y, z, s);
                                }
                            }
                        }
                    }
                }
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    /** 从被拆位置相邻 BFS（经缓存器/总线），找最近的缓存器组作为灾备目标；无则 null */
    private static TileItemBuffer findAdjacentBufferGroup(World world, int x, int y, int z) {
        Set<Long> visited = new HashSet<Long>();
        Deque<int[]> queue = new ArrayDeque<int[]>();
        queue.add(new int[]{x, y, z});
        visited.add(key(x, y, z));
        int[] DX = {0, 0, 0, 0, -1, 1}, DY = {-1, 1, 0, 0, 0, 0}, DZ = {0, 0, -1, 1, 0, 0};
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            for (int d = 0; d < 6; d++) {
                int nx = p[0] + DX[d], ny = p[1] + DY[d], nz = p[2] + DZ[d];
                long k = key(nx, ny, nz);
                if (visited.contains(k)) continue;
                if (!world.getChunkProvider().chunkExists(nx >> 4, nz >> 4)) continue;
                TileEntity te = world.getTileEntity(nx, ny, nz);
                if (te instanceof TileItemBuffer) {
                    return (TileItemBuffer) te;
                }
                if (te instanceof com.cyclone.minecraftiot.tileentity.TileSignalBus) {
                    visited.add(k);
                    queue.add(new int[]{nx, ny, nz});
                }
            }
        }
        return null;
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) (y & 0xFFF)) << 26 | ((long) z & 0x3FFFFFF);
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
}

