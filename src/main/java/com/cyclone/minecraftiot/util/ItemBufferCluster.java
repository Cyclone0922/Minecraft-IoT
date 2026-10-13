package com.cyclone.minecraftiot.util;

import com.cyclone.minecraftiot.tileentity.TileItemBuffer;
import com.cyclone.minecraftiot.tileentity.TileRouter;
import com.cyclone.minecraftiot.tileentity.TileSignalBus;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 物品缓存器组（ItemBuffer Cluster）解析。
 *
 * 规则：
 *  - 缓存器可任意形状拼接（无立方体约束，与路由器组不同）
 *  - 同组所有缓存器容量叠加：总量 = blockCount × 4096，种类 = blockCount × 64
 *  - 下东南角（maxX, minY, maxZ）为该组主控方块（灾备时物品池存这里）
 *
 * 入网判定：缓存器必须紧贴路由器组，或经信号总线/缓存器链到达路由器组才算入网。
 */
public class ItemBufferCluster {

    public static final int PER_BLOCK_ITEM_CAP = ItemPool.DEFAULT_ITEM_CAP; // 4096
    public static final int PER_BLOCK_KIND_CAP = ItemPool.DEFAULT_KIND_CAP; // 64

    public int minX, maxX, minY, maxY, minZ, maxZ;
    public int blockCount;
    public int cx, cy, cz;       // 下东南角（灾备主控）
    public int itemCapacity;     // = blockCount × 4096
    public int kindCapacity;     // = blockCount × 64
    public List<int[]> blocks = new ArrayList<int[]>();

    /** 从任意缓存器方块解析所在组；非缓存器返回 null */
    public static ItemBufferCluster resolve(World world, int x, int y, int z) {
        TileEntity origin = world.getTileEntity(x, y, z);
        if (!(origin instanceof TileItemBuffer)) return null;

        Set<Long> visited = new HashSet<Long>();
        Deque<int[]> queue = new ArrayDeque<int[]>();
        ItemBufferCluster c = new ItemBufferCluster();
        c.minX = x; c.maxX = x; c.minY = y; c.maxY = y; c.minZ = z; c.maxZ = z;
        visited.add(key(x, y, z));
        queue.add(new int[]{x, y, z});

        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            c.blocks.add(p);
            c.minX = Math.min(c.minX, p[0]); c.maxX = Math.max(c.maxX, p[0]);
            c.minY = Math.min(c.minY, p[1]); c.maxY = Math.max(c.maxY, p[1]);
            c.minZ = Math.min(c.minZ, p[2]); c.maxZ = Math.max(c.maxZ, p[2]);
            for (int d = 0; d < 6; d++) {
                int nx = p[0] + DX[d], ny = p[1] + DY[d], nz = p[2] + DZ[d];
                long k = key(nx, ny, nz);
                if (visited.contains(k)) continue;
                if (!world.getChunkProvider().chunkExists(nx >> 4, nz >> 4)) continue;
                TileEntity te = world.getTileEntity(nx, ny, nz);
                if (te instanceof TileItemBuffer) {
                    visited.add(k);
                    queue.add(new int[]{nx, ny, nz});
                }
            }
        }
        c.blockCount = c.blocks.size();
        c.itemCapacity = c.blockCount * PER_BLOCK_ITEM_CAP;
        c.kindCapacity = c.blockCount * PER_BLOCK_KIND_CAP;
        c.cx = c.maxX;
        c.cy = c.minY;
        c.cz = c.maxZ;
        return c;
    }

    /** 枚举本维度所有已加载缓存器组 */
    public static List<ItemBufferCluster> findAll(World world) {
        Set<Long> visited = new HashSet<Long>();
        List<ItemBufferCluster> out = new ArrayList<ItemBufferCluster>();
        for (Object o : world.loadedTileEntityList) {
            if (!(o instanceof TileItemBuffer)) continue;
            TileItemBuffer b = (TileItemBuffer) o;
            long k = key(b.xCoord, b.yCoord, b.zCoord);
            if (visited.contains(k)) continue;
            ItemBufferCluster c = resolve(world, b.xCoord, b.yCoord, b.zCoord);
            if (c == null) continue;
            out.add(c);
            for (int[] bb : c.blocks) visited.add(key(bb[0], bb[1], bb[2]));
        }
        return out;
    }

    /**
     * 判断某缓存器是否入网：从该缓存器 BFS（经缓存器 + 信号总线），
     * 能否到达一个合法路由器组（RouterCluster.resolve 非 null）。
     * 贴路由器组任一块、或经总线链到达，均算入网。
     */
    public static boolean isConnected(World world, int x, int y, int z) {
        TileEntity origin = world.getTileEntity(x, y, z);
        if (!(origin instanceof TileItemBuffer)) return false;
        Set<Long> visited = new HashSet<Long>();
        Deque<int[]> queue = new ArrayDeque<int[]>();
        visited.add(key(x, y, z));
        queue.add(new int[]{x, y, z});
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            for (int d = 0; d < 6; d++) {
                int nx = p[0] + DX[d], ny = p[1] + DY[d], nz = p[2] + DZ[d];
                long k = key(nx, ny, nz);
                if (visited.contains(k)) continue;
                if (!world.getChunkProvider().chunkExists(nx >> 4, nz >> 4)) continue;
                TileEntity te = world.getTileEntity(nx, ny, nz);
                if (te instanceof TileRouter) {
                    // 贴到路由器：还需该路由器构成合法组才算入网
                    if (RouterCluster.resolve(world, nx, ny, nz) != null) return true;
                    continue;
                }
                if (te instanceof TileSignalBus || te instanceof TileItemBuffer) {
                    visited.add(k);
                    queue.add(new int[]{nx, ny, nz});
                }
            }
        }
        return false;
    }

    /** 找到某缓存器可到达的路由器组（isConnected 为真时非 null） */
    public static RouterCluster findRouter(World world, int x, int y, int z) {
        TileEntity origin = world.getTileEntity(x, y, z);
        if (!(origin instanceof TileItemBuffer)) return null;
        Set<Long> visited = new HashSet<Long>();
        Deque<int[]> queue = new ArrayDeque<int[]>();
        visited.add(key(x, y, z));
        queue.add(new int[]{x, y, z});
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            for (int d = 0; d < 6; d++) {
                int nx = p[0] + DX[d], ny = p[1] + DY[d], nz = p[2] + DZ[d];
                long k = key(nx, ny, nz);
                if (visited.contains(k)) continue;
                if (!world.getChunkProvider().chunkExists(nx >> 4, nz >> 4)) continue;
                TileEntity te = world.getTileEntity(nx, ny, nz);
                if (te instanceof TileRouter) {
                    RouterCluster rc = RouterCluster.resolve(world, nx, ny, nz);
                    if (rc != null) return rc;
                    continue;
                }
                if (te instanceof TileSignalBus || te instanceof TileItemBuffer) {
                    visited.add(k);
                    queue.add(new int[]{nx, ny, nz});
                }
            }
        }
        return null;
    }

    private static final int[] DX = {0, 0, 0, 0, -1, 1};
    private static final int[] DY = {-1, 1, 0, 0, 0, 0};
    private static final int[] DZ = {0, 0, -1, 1, 0, 0};

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) (y & 0xFFF)) << 26 | ((long) z & 0x3FFFFFF);
    }
}
