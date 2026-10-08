package com.cyclone.minecraftiot.block;

import com.cyclone.minecraftiot.SensorDisplayMod;
import com.cyclone.minecraftiot.tileentity.TileActuator;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 普通执行器方块（P1）。
 * 贴边摆放，插绑定卡 + 红石技能插件后，可在条件成立时从指定面输出红石。
 */
public class BlockActuator extends Block {

    public BlockActuator() {
        super(Material.iron);
        setHardness(2.5F);
        setResistance(12.0F);
        setBlockName("actuator");
        setCreativeTab(CreativeTabs.tabMisc);
        setBlockTextureName("minecraftiot:actuator");
    }

    @Override
    public boolean hasTileEntity(int metadata) { return true; }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileActuator();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
                                     int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            player.openGui(SensorDisplayMod.instance, 3, world, x, y, z); // GUI_ACTUATOR=3
        }
        return true;
    }

    // ===== 红石输出 =====

    /** 该方块可以提供强/弱红石信号 */
    @Override
    public boolean canProvidePower() { return true; }

    /**
     * 弱红石输出：从指定面输出。
     * side 参数是"查询方"——即相邻方块在本方块的哪一侧。
     * 执行器的第 i 面（ForgeDirection.VALID_DIRECTIONS[i]）输出时，
     * 相邻方块从相反方向查询到信号。
     */
    @Override
    public int isProvidingWeakPower(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileActuator)) return 0;
        TileActuator act = (TileActuator) te;
        // side 是查询方向（相邻方块相对于本方块的方向），
        // 本方块朝 side 的对面输出才会被这个相邻方块收到。
        // 例如：本方块朝 EAST 输出，EAST 侧的相邻方块从 WEST(side=4) 查询。
        // ForgeDirection.VALID_DIRECTIONS: 0=DOWN 1=UP 2=NORTH 3=SOUTH 4=WEST 5=EAST
        // 对面：0<->1, 2<->3, 4<->5
        int outputFace = oppositeSide(side);
        return act.getCurrentOutput(outputFace);
    }

    @Override
    public int isProvidingStrongPower(IBlockAccess world, int x, int y, int z, int side) {
        return isProvidingWeakPower(world, x, y, z, side);
    }

    /** 0<->1, 2<->3, 4<->5 */
    private static int oppositeSide(int side) {
        if (side < 0 || side > 5) return 0;
        return (side % 2 == 0) ? side + 1 : side - 1;
    }
}
