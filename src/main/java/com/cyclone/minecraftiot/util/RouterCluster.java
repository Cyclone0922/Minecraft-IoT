package com.cyclone.minecraftiot.util;

import com.cyclone.minecraftiot.tileentity.TileActuator;
import com.cyclone.minecraftiot.tileentity.TileRouter;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 路由器多方块组（Router Cluster）解析与容量判定。
 *
 * 规则（用户拍板）：
 *  - 多个路由器方块贴在一起组成一组，外形必须是立方体形（实心、无缺口）
 *  - 任意方向长度不超过 8 个方块
 *  - 每个路由器方块提供 16 个设备连接位，组容量 = 方块数 × 16
 *  - 距离计算遵循下东南定律：以组内【下东南角】方块（x 最大、y 最小、z 最大）为中心
 *
 * 非法形状（有缺口/非立方体/超长）的组整体不可组网（绑定会被拒绝，自动重扫也会跳过）。
 */
public class RouterCluster {

    public static final int MAX_LEN = 8;
    public static final int PER_BLOCK_CAPACITY = 16;

    public int minX, maxX, minY, maxY, minZ, maxZ; // 组包围盒
    public int blockCount;                          // 组内方块数
    public int cx, cy, cz;                          // 下东南角中心（maxX, minY, maxZ）
    public int capacity;                            // 设备连接上限 = blockCount × 16
    public List<int[]> blocks = new ArrayList<int[]>(); // 组内全部方块坐标

    /** 从任一路由器方块出发解析所在组；非法组返回 null */
    public static RouterCluster resolve(World world, int x, int y, int z) {
        TileEntity origin = world.getTileEntity(x, y, z);
        if (!(origin instanceof TileRouter)) return null;

        Set<Long> visited = new HashSet<Long>();
        Deque<int[]> queue = new ArrayDeque<int[]>();
        RouterCluster c = new RouterCluster();
        c.minX = x; c.maxX = x;
        c.minY = y; c.maxY = y;
        c.minZ = z; c.maxZ = z;
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
                // 只探已加载区块：未加载区块视为无邻居（玩家走近后重扫会重新解析）
                if (!world.getChunkProvider().chunkExists(nx >> 4, nz >> 4)) continue;
                TileEntity te = world.getTileEntity(nx, ny, nz);
                if (te instanceof TileRouter) {
                    visited.add(k);
                    queue.add(new int[]{nx, ny, nz});
                }
            }
        }

        // 任意方向长度 ≤ 8
        if (c.maxX - c.minX + 1 > MAX_LEN || c.maxY - c.minY + 1 > MAX_LEN || c.maxZ - c.minZ + 1 > MAX_LEN) {
            return null;
        }
        // 无缺口立方体：块数必须等于包围盒体积（任一缺块都会小于体积）
        int vol = (c.maxX - c.minX + 1) * (c.maxY - c.minY + 1) * (c.maxZ - c.minZ + 1);
        if (c.blocks.size() != vol) return null;

        c.blockCount = c.blocks.size();
        c.capacity = c.blockCount * PER_BLOCK_CAPACITY;
        c.cx = c.maxX;   // 下：y 最小
        c.cy = c.minY;   // 东：x 最大
        c.cz = c.maxZ;   // 南：z 最大
        return c;
    }

    /** 枚举本维度所有已加载区块中的合法路由器组（非法组整体跳过） */
    public static List<RouterCluster> findAll(World world) {
        Set<Long> visited = new HashSet<Long>();
        List<RouterCluster> out = new ArrayList<RouterCluster>();
        for (Object o : world.loadedTileEntityList) {
            if (!(o instanceof TileRouter)) continue;
            TileRouter r = (TileRouter) o;
            long k = key(r.xCoord, r.yCoord, r.zCoord);
            if (visited.contains(k)) continue;
            RouterCluster c = resolve(world, r.xCoord, r.yCoord, r.zCoord);
            if (c == null) {
                visited.add(k); // 非法组：标记该方块已处理（整组跳过）
                continue;
            }
            out.add(c);
            for (int[] b : c.blocks) visited.add(key(b[0], b[1], b[2]));
        }
        return out;
    }

    /** 已绑定本组的执行器数量（动态实时统计，供容量检查） */
    public int countBoundActuators(World world) {
        int n = 0;
        for (Object o : world.loadedTileEntityList) {
            if (o instanceof TileActuator) {
                TileActuator a = (TileActuator) o;
                if (a.isRouterBound() && a.getRouterX() == cx && a.getRouterY() == cy && a.getRouterZ() == cz) {
                    n++;
                }
            }
        }
        return n;
    }

    private static final int[] DX = {0, 0, 0, 0, -1, 1};
    private static final int[] DY = {-1, 1, 0, 0, 0, 0};
    private static final int[] DZ = {0, 0, -1, 1, 0, 0};

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) (y & 0xFFF)) << 26 | ((long) z & 0x3FFFFFF);
    }
}
