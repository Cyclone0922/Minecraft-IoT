package com.cyclone.minecraftiot.tileentity;

import com.cyclone.minecraftiot.util.SignalValue;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 信号总线：技能0 信号域的线性中继（方向直通）。
 * 每 2 tick 执行一次：
 *   1. 收信：读 6 个方向邻居（执行器/总线）朝本总线面的 outBuffer → 本机 inBuffer
 *   2. 出信：side 面输出 = 对面邻居传来的值（方向直通），实现 A→总线→B 中继
 * 多总线可串联；总线本身不存储、不求值。
 */
public class TileSignalBus extends TileEntity {

    private static final int SIGNAL_INTERVAL = 2;

    private SignalValue[] inBuffer = new SignalValue[6];
    private SignalValue[] outBuffer = new SignalValue[6];
    private boolean firstTick = true;

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) return;
        if (firstTick || worldObj.getTotalWorldTime() % SIGNAL_INTERVAL == 0) {
            firstTick = false;
            relay();
        }
    }

    private void relay() {
        // 收信：每面读邻居朝本总线面的输出
        for (int side = 0; side < 6; side++) {
            ForgeDirection dir = ForgeDirection.VALID_DIRECTIONS[side];
            TileEntity neighbor = worldObj.getTileEntity(
                    xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
            SignalValue v = null;
            if (neighbor instanceof TileActuator) {
                v = ((TileActuator) neighbor).getOutValue(oppositeSide(side));
            } else if (neighbor instanceof TileSignalBus) {
                v = ((TileSignalBus) neighbor).getOutValue(oppositeSide(side));
            }
            inBuffer[side] = v;
        }
        // 出信：方向直通
        boolean changed = false;
        for (int side = 0; side < 6; side++) {
            SignalValue v = inBuffer[oppositeSide(side)];
            if (!sameValue(outBuffer[side], v)) {
                outBuffer[side] = v;
                changed = true;
            }
        }
        if (changed) markDirty();
    }

    /** 某面最近输出值（供邻居执行器/总线读取） */
    public SignalValue getOutValue(int side) {
        if (side < 0 || side >= 6) return null;
        return outBuffer[side];
    }

    private static int oppositeSide(int side) {
        if (side == 0) return 1;
        if (side == 1) return 0;
        if (side == 2) return 3;
        if (side == 3) return 2;
        if (side == 4) return 5;
        return 4;
    }

    private static boolean sameValue(SignalValue a, SignalValue b) {
        if (a == null || b == null) return a == b;
        if (a.type != b.type) return false;
        if (a.type == SignalValue.TYPE_DOUBLE) return a.num == b.num;
        if (a.type == SignalValue.TYPE_BOOL) return a.bool == b.bool;
        return a.str.equals(b.str);
    }
}
