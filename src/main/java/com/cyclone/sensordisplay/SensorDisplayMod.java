package com.cyclone.sensordisplay;

import com.cyclone.sensordisplay.block.BlockActuator;
import com.cyclone.sensordisplay.block.BlockSignalBus;
import com.cyclone.sensordisplay.block.BlockConnectorWorkbench;
import com.cyclone.sensordisplay.block.BlockDisplay;
import com.cyclone.sensordisplay.block.BlockRouter;
import com.cyclone.sensordisplay.block.BlockSensor;
import com.cyclone.sensordisplay.gui.GuiHandler;
import com.cyclone.sensordisplay.item.ItemConnector;
import com.cyclone.sensordisplay.item.ItemSkillRedstone;
import com.cyclone.sensordisplay.network.PacketActuatorConfig;
import com.cyclone.sensordisplay.network.PacketBindConnector;
import com.cyclone.sensordisplay.network.PacketDisplayData;
import com.cyclone.sensordisplay.network.PacketOpenRemoteGui;
import com.cyclone.sensordisplay.network.PacketSensorData;
import com.cyclone.sensordisplay.network.PacketSetDisplayFace;
import com.cyclone.sensordisplay.network.PacketSetDisplaySettings;
import com.cyclone.sensordisplay.network.PacketSetSensorLabel;
import com.cyclone.sensordisplay.network.PacketWorkbenchAction;
import com.cyclone.sensordisplay.network.PacketWorkbenchData;
import com.cyclone.sensordisplay.network.PacketWorkbenchRequest;
import com.cyclone.sensordisplay.network.RemoteGuiReturn;
import com.cyclone.sensordisplay.tileentity.TileActuator;
import com.cyclone.sensordisplay.tileentity.TileConnectorWorkbench;
import com.cyclone.sensordisplay.tileentity.TileDisplay;
import com.cyclone.sensordisplay.tileentity.TileRouter;
import com.cyclone.sensordisplay.tileentity.TileSensor;
import com.cyclone.sensordisplay.tileentity.TileSignalBus;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import io.netty.channel.ChannelHandler;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = "sensordisplay", name = "Sensor Display", version = "1.0")
public class SensorDisplayMod {
    public static final String MODID = "sensordisplay";
    public static final String VERSION = "1.0";
    // SensorDisplayMod.java
    public static final SimpleNetworkWrapper network = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
    // 诊断日志
    public static final Logger log = LogManager.getLogger("sensordisplay");

    @Instance(MODID)
    public static SensorDisplayMod instance;

    @SidedProxy(clientSide = "com.cyclone.sensordisplay.client.ClientProxy",
            serverSide = "com.cyclone.sensordisplay.CommonProxy")
    public static CommonProxy proxy;

    public static Block sensorBlock;
    public static Block displayBlock;
    public static Block actuatorBlock;
    public static Block routerBlock;
    public static Block workbenchBlock;
    public static Block signalBusBlock;
    public static Item connectorItem;
    public static Item skillRedstoneItem;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // 注：sensordisplay.json 显示格式配置文件已退役。
        // 显示配置改为写入连接器卡 NBT（连接器工作台编辑），传感器只读 NBT + 内置预设模板。
        // 此处不再加载 DisplayFormatConfig。

        // 注册方块、物品
        sensorBlock = new BlockSensor().setBlockName("sensor").setCreativeTab(CreativeTabs.tabMisc);
        GameRegistry.registerBlock(sensorBlock, "sensor");
        displayBlock = new BlockDisplay().setBlockName("display").setCreativeTab(CreativeTabs.tabMisc);
        GameRegistry.registerBlock(displayBlock, "display");
        connectorItem = new ItemConnector().setUnlocalizedName("connector");
        GameRegistry.registerItem(connectorItem, "connector");
        connectorItem.setCreativeTab(CreativeTabs.tabMisc);

        // P1 新方块：执行器、路由器
        actuatorBlock = new BlockActuator().setBlockName("actuator").setCreativeTab(CreativeTabs.tabMisc);
        GameRegistry.registerBlock(actuatorBlock, "actuator");
        routerBlock = new BlockRouter().setBlockName("router").setCreativeTab(CreativeTabs.tabMisc);
        GameRegistry.registerBlock(routerBlock, "router");

        // 连接器工作台（配置进卡架构核心方块）
        workbenchBlock = new BlockConnectorWorkbench().setBlockName("connector_workbench").setCreativeTab(CreativeTabs.tabMisc);
        GameRegistry.registerBlock(workbenchBlock, "connector_workbench");

        // 技能0 信号总线（方向直通中继）
        signalBusBlock = new BlockSignalBus().setBlockName("signal_bus").setCreativeTab(CreativeTabs.tabMisc);
        GameRegistry.registerBlock(signalBusBlock, "signal_bus");

        // P1 新物品：红石技能插件
        skillRedstoneItem = new ItemSkillRedstone().setUnlocalizedName("skill_redstone");
        GameRegistry.registerItem(skillRedstoneItem, "skill_redstone");
        skillRedstoneItem.setCreativeTab(CreativeTabs.tabMisc);

        // 注册TileEntity（id 带 mod 前缀，避免与其他 mod 的通用短 id 冲突，如 "TileSensor" 被整合包其他 mod 抢占）
        GameRegistry.registerTileEntity(TileSensor.class, "sensordisplay:TileSensor");
        GameRegistry.registerTileEntity(TileDisplay.class, "sensordisplay:TileDisplay");
        GameRegistry.registerTileEntity(TileActuator.class, "sensordisplay:TileActuator");
        GameRegistry.registerTileEntity(TileRouter.class, "sensordisplay:TileRouter");
        GameRegistry.registerTileEntity(TileConnectorWorkbench.class, "sensordisplay:TileConnectorWorkbench");
        GameRegistry.registerTileEntity(TileSignalBus.class, "sensordisplay:TileSignalBus");

        // 注册网络包
        network.registerMessage(PacketDisplayData.Handler.class, PacketDisplayData.class, 0, Side.CLIENT);   // 显示屏数据 -> 客户端
        network.registerMessage(PacketBindConnector.Handler.class, PacketBindConnector.class, 1, Side.SERVER); // 绑定按钮 -> 服务端
        network.registerMessage(PacketSensorData.Handler.class, PacketSensorData.class, 2, Side.CLIENT);      // 传感器扫描数据 -> 客户端
        network.registerMessage(PacketSetDisplayFace.Handler.class, PacketSetDisplayFace.class, 3, Side.SERVER); // 调整显示面 -> 服务端
        network.registerMessage(PacketSetSensorLabel.Handler.class, PacketSetSensorLabel.class, 4, Side.SERVER); // 设置传感器注释 -> 服务端
        network.registerMessage(PacketSetDisplaySettings.Handler.class, PacketSetDisplaySettings.class, 5, Side.SERVER); // 设置显示器字号/对齐 -> 服务端
        network.registerMessage(PacketOpenRemoteGui.Handler.class, PacketOpenRemoteGui.class, 6, Side.SERVER); // 远程打开目标机器GUI -> 服务端
        network.registerMessage(PacketActuatorConfig.Handler.class, PacketActuatorConfig.class, 7, Side.SERVER); // 执行器面配置 -> 服务端
        network.registerMessage(PacketWorkbenchAction.Handler.class, PacketWorkbenchAction.class, 8, Side.SERVER); // 工作台动作 -> 服务端
        network.registerMessage(PacketWorkbenchRequest.Handler.class, PacketWorkbenchRequest.class, 9, Side.SERVER); // 工作台状态请求 -> 服务端
        network.registerMessage(PacketWorkbenchData.Handler.class, PacketWorkbenchData.class, 10, Side.CLIENT); // 工作台状态数据 -> 客户端

        // Esc 关闭目标机器GUI后返回显示器GUI（ServerTickEvent 发在 FML bus 上）
        FMLCommonHandler.instance().bus().register(new RemoteGuiReturn());

        // 注册GUI处理器
        proxy.registerGuiHandler();  // 或在CommonProxy中调用 注册 GUI 处理器
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.registerRenderers(); // 客户端渲染注册
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // 跨模组交互等
    }
}