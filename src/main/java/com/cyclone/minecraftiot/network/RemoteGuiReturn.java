package com.cyclone.minecraftiot.network;

import com.cyclone.minecraftiot.MinecraftIotMod;
import com.cyclone.minecraftiot.gui.GuiHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * "Esc 返回显示器"跟踪：
 * 远程终端 GUI 关闭（openContainer 离开远程容器）后，自动重新打开显示器 GUI。
 * 玩家从不移动，无任何传送。一次性触发后清除标记。
 */
public class RemoteGuiReturn {
    private static final Map<UUID, Pending> pending = new HashMap<UUID, Pending>();

    private static class Pending {
        final EntityPlayerMP player;
        final int dx, dy, dz;   // 显示器主控坐标
        final Container remote; // 远程容器引用
        Pending(EntityPlayerMP player, int dx, int dy, int dz, Container remote) {
            this.player = player; this.dx = dx; this.dy = dy; this.dz = dz; this.remote = remote;
        }
    }

    public static void arm(EntityPlayerMP player, int dx, int dy, int dz, Container remote) {
        pending.put(player.getUniqueID(), new Pending(player, dx, dy, dz, remote));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (pending.isEmpty()) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) { pending.clear(); return; }
        Iterator<Map.Entry<UUID, Pending>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> en = it.next();
            Pending p = en.getValue();
            if (p.player.playerNetServerHandler == null || p.player.openContainer != p.remote) {
                it.remove();
                if (p.player.playerNetServerHandler != null) {
                    MinecraftIotMod.log.info("[RemoteGUI] returning to display at (" + p.dx + "," + p.dy + "," + p.dz + ")");
                    p.player.openGui(MinecraftIotMod.MODID, GuiHandler.GUI_DISPLAY,
                            p.player.worldObj, p.dx, p.dy, p.dz);
                }
            }
        }
    }
}
