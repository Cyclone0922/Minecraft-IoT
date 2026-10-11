package com.cyclone.minecraftiot.block;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.network.PacketRouterListData;
import com.cyclone.minecraftiot.tileentity.TileActuator;
import com.cyclone.minecraftiot.util.RouterCluster;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.List;

/**
 * 普通执行器方块（P1）。
 * 贴边摆放，插绑定卡 + 技能插件后按配置工作。
 * 0.4.0：放置后自动搜索路由器组（当前范围=本维度已加载区块）；
 *   0 个组 → 暂不绑定（周期性自动重扫）；
 *   1 个组 → 容量未满则自动绑定；
 *   多个组 → 右键时弹出选择界面，选中后绑定并落存档。
 * 路由器门控：未连接路由器的执行器拒绝执行任何动作（值传递/红石/物品转移），
 * 防止绕过路由器能量系统作弊；放置后右键执行器即可重新扫描并绑定路由器。
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
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        if (world.isRemote) return;
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileActuator)) return;
        TileActuator act = (TileActuator) te;
        if (act.isRouterBound()) return;
        List<RouterCluster> clusters = RouterCluster.findAll(world);
        if (clusters.size() == 1) {
            RouterCluster c = clusters.get(0);
            if (c.countBoundActuators(world) < c.capacity) {
                act.setRouter(c.cx, c.cy, c.cz, c.blockCount);
            }
        }
        // 0 个：不绑定（周期性重扫）；多个：右键时选择
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
                                     int side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileActuator)) return true;
        TileActuator act = (TileActuator) te;
        if (!act.isRouterBound()) {
            List<RouterCluster> clusters = RouterCluster.findAll(world);
            if (clusters.size() == 1) {
                RouterCluster c = clusters.get(0);
                if (c.countBoundActuators(world) >= c.capacity) {
                    // 容量已满：提示，不绑定
                    if (player instanceof EntityPlayerMP) {
                        ((EntityPlayerMP) player).addChatComponentMessage(new ChatComponentText(
                                "\u00a7c[\u8def\u7531\u5668] \u8be5\u7ec4\u5df2\u6ee1\uff08" + c.capacity
                                        + "/" + c.capacity + "\uff09\uff0c\u8bf7\u589e\u52a0\u8def\u7531\u5668\u65b9\u5757\u6269\u5bb9"));
                    }
                } else {
                    act.setRouter(c.cx, c.cy, c.cz, c.blockCount);
                }
            } else if (clusters.size() > 1) {
                // 多个组：弹选择界面（服务端把列表发给客户端）
                MinecraftIotMod.network.sendTo(new PacketRouterListData(x, y, z, clusters, world),
                        (net.minecraft.entity.player.EntityPlayerMP) player);
                return true;
            }
        }
        player.openGui(MinecraftIotMod.instance, 3, world, x, y, z); // GUI_ACTUATOR=3
        return true;
    }

    /** 搜索本维度所有已加载区块中的合法路由器组（当前组网范围定义：所有加载范围） */
    public static List<RouterCluster> findRouterClusters(World world) {
        return RouterCluster.findAll(world);
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileActuator) {
                // 物品转移缓存：先就近放回相邻容器，剩余掉落为物品实体，避免物品凭空消失
                java.util.List<net.minecraft.item.ItemStack> left =
                        ((TileActuator) te).drainTakeBuffer();
                for (net.minecraft.item.ItemStack s : left) {
                    if (s != null && s.stackSize > 0) {
                        dropStack(world, x, y, z, s);
                    }
                }
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    private static void dropStack(World world, int x, int y, int z, net.minecraft.item.ItemStack stack) {
        float f = 0.7F;
        double dx = world.rand.nextFloat() * f + (1.0F - f) * 0.5D;
        double dy = world.rand.nextFloat() * f + (1.0F - f) * 0.5D;
        double dz = world.rand.nextFloat() * f + (1.0F - f) * 0.5D;
        net.minecraft.entity.item.EntityItem entityitem =
                new net.minecraft.entity.item.EntityItem(world, x + dx, y + dy, z + dz, stack);
        entityitem.delayBeforeCanPickup = 10;
        world.spawnEntityInWorld(entityitem);
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
