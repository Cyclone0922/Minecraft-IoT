package com.cyclone.sensordisplay.gui;

import com.cyclone.sensordisplay.tileentity.TileActuator;
import com.cyclone.sensordisplay.tileentity.TileConnectorWorkbench;
import com.cyclone.sensordisplay.tileentity.TileDisplay;
import com.cyclone.sensordisplay.tileentity.TileSensor;
import com.cyclone.sensordisplay.util.DisplayGroupUtil;
import cpw.mods.fml.common.network.IGuiHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class GuiHandler implements IGuiHandler {

    // 定义 GUI ID（与主类中 openGui 的 ID 对应）
    public static final int GUI_SENSOR = 0;
    public static final int GUI_DISPLAY = 1;
    public static final int GUI_REMOTE = 2;
    public static final int GUI_ACTUATOR = 3;
    public static final int GUI_WORKBENCH = 4;

    /**
     * 解析任意显示块所属组的"主控"TileDisplay。
     * 结构无效时回退到点击块本身，保证 GUI 至少能打开、卡可取出。
     */
    private static TileDisplay masterDisplayAt(World world, int x, int y, int z, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileDisplay) {
            DisplayGroupUtil.Pos master = DisplayGroupUtil.resolveMaster(world, x, y, z, meta);
            if (master != null) {
                TileEntity mte = world.getTileEntity(master.x, master.y, master.z);
                if (mte instanceof TileDisplay) return (TileDisplay) mte;
            }
            return (TileDisplay) te; // 结构无效回退
        }
        return null;
    }

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        switch (ID) {
            case GUI_SENSOR:
                if (te instanceof TileSensor) {
                    return new ContainerSensor(player.inventory, (TileSensor) te);
                }
                break;
            case GUI_DISPLAY:
                TileDisplay master = masterDisplayAt(world, x, y, z, world.getBlockMetadata(x, y, z));
                if (master != null) {
                    return new ContainerDisplay(player.inventory, master);
                }
                break;
            case GUI_REMOTE:
                if (te instanceof IInventory) {
                    return new ContainerRemote(player.inventory, (IInventory) te);
                }
                break;
            case GUI_ACTUATOR:
                if (te instanceof TileActuator) {
                    return new ContainerActuator(player.inventory, (TileActuator) te);
                }
                break;
            case GUI_WORKBENCH:
                if (te instanceof TileConnectorWorkbench) {
                    return new ContainerConnectorWorkbench(player.inventory, (TileConnectorWorkbench) te);
                }
                break;
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        switch (ID) {
            case GUI_SENSOR:
                if (te instanceof TileSensor) {
                    return new GuiSensor(new ContainerSensor(player.inventory, (TileSensor) te));
                }
                break;
            case GUI_DISPLAY:
                TileDisplay master = masterDisplayAt(world, x, y, z, world.getBlockMetadata(x, y, z));
                if (master != null) {
                    return new GuiDisplay(new ContainerDisplay(player.inventory, master), master);
                }
                break;
            case GUI_REMOTE:
                if (te instanceof IInventory) {
                    return new GuiRemote(new ContainerRemote(player.inventory, (IInventory) te), world, x, y, z);
                }
                break;
            case GUI_ACTUATOR:
                if (te instanceof TileActuator) {
                    return new GuiActuator(new ContainerActuator(player.inventory, (TileActuator) te));
                }
                break;
            case GUI_WORKBENCH:
                if (te instanceof TileConnectorWorkbench) {
                    return new GuiConnectorWorkbench(new ContainerConnectorWorkbench(player.inventory, (TileConnectorWorkbench) te), (TileConnectorWorkbench) te);
                }
                break;
        }
        return null;
    }
}