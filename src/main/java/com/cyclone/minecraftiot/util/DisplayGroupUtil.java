package com.cyclone.minecraftiot.util;

import com.cyclone.minecraftiot.block.BlockDisplay;
import com.cyclone.minecraftiot.tileentity.TileDisplay;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 显示器拼接组工具：供渲染器与显示器 GUI 共用（客户端）。
 * 统一处理：分组检测、结构完整性（须为完整矩形）、四角判定。
 */
public class DisplayGroupUtil {

    public static class Pos {
        public int x, y, z;
        public Pos(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        public boolean equals(int ox, int oy, int oz) { return x == ox && y == oy && z == oz; }
    }

    public static class Info {
        public List<Pos> members = new ArrayList<Pos>();
        public int minU, maxU, minV, maxV;   // 面内平面坐标范围
        public int uAxis, vAxis, fixedAxis;  // 0=x 1=y 2=z
        public int W, H;
        public boolean structValid;          // 是否完整矩形
        public boolean thisIsCorner;         // 调用点所在块是否是四角之一
        public int thisX, thisY, thisZ;
        public int thisU, thisV;             // 调用点所在块的平面坐标
        public List<Pos> corners = new ArrayList<Pos>(); // 去重后的角（世界坐标）
        public Pos thisPos() { return new Pos(thisX, thisY, thisZ); }
    }

    /** 计算以 (x,y,z) 所在块为起点的拼接组信息 */
    public static Info compute(World world, int x, int y, int z, int meta) {
        Info info = new Info();
        info.thisX = x; info.thisY = y; info.thisZ = z;
        List<Pos> group = findGroup(world, x, y, z, meta);
        info.members = group;
        if (group.isEmpty()) return info;

        int[] plane = planeMapping(meta);
        info.uAxis = plane[0]; info.vAxis = plane[1];
        info.fixedAxis = 3 - info.uAxis - info.vAxis;

        int minU = Integer.MAX_VALUE, maxU = Integer.MIN_VALUE;
        int minV = Integer.MAX_VALUE, maxV = Integer.MIN_VALUE;
        for (Pos p : group) {
            int u = coord(p, info.uAxis), v = coord(p, info.vAxis);
            if (u < minU) minU = u;
            if (u > maxU) maxU = u;
            if (v < minV) minV = v;
            if (v > maxV) maxV = v;
        }
        info.minU = minU; info.maxU = maxU; info.minV = minV; info.maxV = maxV;
        info.W = maxU - minU + 1;
        info.H = maxV - minV + 1;
        info.structValid = (group.size() == info.W * info.H);

        int fixedVal = (info.fixedAxis == 0) ? x : ((info.fixedAxis == 1) ? y : z);
        int[][] cornerUVs = { {minU,minV}, {minU,maxV}, {maxU,minV}, {maxU,maxV} };
        Set<Long> seen = new HashSet<Long>();
        for (int[] uv : cornerUVs) {
            Pos cp = worldPosFromUV(uv[0], uv[1], fixedVal, meta);
            long k = key(cp.x, cp.y, cp.z);
            if (!seen.contains(k)) { seen.add(k); info.corners.add(cp); }
        }

        int thisU = coord(x, y, z, info.uAxis), thisV = coord(x, y, z, info.vAxis);
        info.thisU = thisU; info.thisV = thisV;
        info.thisIsCorner = (thisU == minU || thisU == maxU) && (thisV == minV || thisV == maxV);
        return info;
    }

    public static boolean tileHasConnector(World world, Pos p) {
        TileEntity te = world.getTileEntity(p.x, p.y, p.z);
        return te instanceof TileDisplay && ((TileDisplay) te).hasConnector();
    }

    /**
     * 解析拼接组的主控角位置（"下东南定律"，不要求结构完整）：
     * - 垂直布置（N/S/E/W）：取最下层（y 最小），再取其中最靠东南（x 最大，平局取 z 最大）
     * - 水平布置（UP/DOWN）：直接取最靠东南（x 最大，平局取 z 最大）
     * 组为空时返回 null。用于指示灯与 GUI 定位。
     */
    public static Pos resolveMasterPos(World world, int x, int y, int z, int meta) {
        Info info = compute(world, x, y, z, meta);
        if (info.members.isEmpty()) return null;
        ForgeDirection face = dirFromMeta(meta);
        boolean vertical = face != ForgeDirection.UP && face != ForgeDirection.DOWN;
        int yMin = Integer.MAX_VALUE;
        for (Pos p : info.members) if (p.y < yMin) yMin = p.y;
        Pos best = null;
        for (Pos p : info.members) {
            if (vertical && p.y != yMin) continue; // 垂直只考虑最下层
            if (best == null) { best = p; continue; }
            if (p.x > best.x || (p.x == best.x && p.z > best.z)) best = p; // 最靠东南
        }
        return best;
    }

    /** 解析拼接组的唯一主控角（下东南定律）；结构无效时返回 null。 */
    public static Pos resolveMaster(World world, int x, int y, int z, int meta) {
        Info info = compute(world, x, y, z, meta);
        if (info.members.isEmpty() || !info.structValid) return null;
        return resolveMasterPos(world, x, y, z, meta);
    }

    /** 取某坐标所在组的全部成员（供改朝向等整组操作） */
    public static List<Pos> group(World world, int x, int y, int z, int meta) {
        return compute(world, x, y, z, meta).members;
    }

    // ============ 内部 ============

    private static List<Pos> findGroup(World world, int x, int y, int z, int meta) {
        List<Pos> result = new ArrayList<Pos>();
        ArrayDeque<Pos> stack = new ArrayDeque<Pos>();
        Set<Long> visited = new HashSet<Long>();
        visited.add(key(x, y, z));
        stack.push(new Pos(x, y, z));
        while (!stack.isEmpty()) {
            Pos cur = stack.pop();
            result.add(cur);
            for (int[] nb : inPlaneNeighbors(cur.x, cur.y, cur.z, meta)) {
                long k = key(nb[0], nb[1], nb[2]);
                if (visited.contains(k)) continue;
                if (isSameDisplay(world, nb[0], nb[1], nb[2], meta)) {
                    visited.add(k);
                    stack.push(new Pos(nb[0], nb[1], nb[2]));
                }
            }
        }
        return result;
    }

    private static int[][] inPlaneNeighbors(int x, int y, int z, int meta) {
        ForgeDirection face = dirFromMeta(meta);
        switch (face) {
            case NORTH:
            case SOUTH:
                return new int[][] { {x+1,y,z}, {x-1,y,z}, {x,y+1,z}, {x,y-1,z} };
            case EAST:
            case WEST:
                return new int[][] { {x,y+1,z}, {x,y-1,z}, {x,y,z+1}, {x,y,z-1} };
            default:
                return new int[][] { {x+1,y,z}, {x-1,y,z}, {x,y,z+1}, {x,y,z-1} };
        }
    }

    private static boolean isSameDisplay(World world, int nx, int ny, int nz, int meta) {
        Block b = world.getBlock(nx, ny, nz);
        return b instanceof BlockDisplay && world.getBlockMetadata(nx, ny, nz) == meta;
    }

    private static Pos worldPosFromUV(int u, int v, int fixedVal, int meta) {
        ForgeDirection face = dirFromMeta(meta);
        switch (face) {
            case EAST:
            case WEST:
                return new Pos(fixedVal, v, u); // u=z, v=y
            case UP:
            case DOWN:
                return new Pos(u, fixedVal, v); // u=x, v=z
            default:
                return new Pos(u, v, fixedVal); // u=x, v=y
        }
    }

    private static int[] planeMapping(int meta) {
        ForgeDirection face = dirFromMeta(meta);
        switch (face) {
            case EAST:
            case WEST:
                return new int[] { 2, 1 };
            case UP:
            case DOWN:
                return new int[] { 0, 2 };
            default:
                return new int[] { 0, 1 };
        }
    }

    private static int coord(Pos p, int axis) {
        return axis == 0 ? p.x : (axis == 1 ? p.y : p.z);
    }

    private static int coord(int x, int y, int z, int axis) {
        return axis == 0 ? x : (axis == 1 ? y : z);
    }

    private static long key(int x, int y, int z) {
        return (((long) x & 0xFFFFF) << 40) | (((long) y & 0xFFFFF) << 20) | ((long) z & 0xFFFFF);
    }

    private static ForgeDirection dirFromMeta(int meta) {
        switch (meta) {
            case 0: return ForgeDirection.DOWN;
            case 1: return ForgeDirection.UP;
            case 2: return ForgeDirection.NORTH;
            case 4: return ForgeDirection.WEST;
            case 5: return ForgeDirection.EAST;
            default: return ForgeDirection.SOUTH;
        }
    }
}
