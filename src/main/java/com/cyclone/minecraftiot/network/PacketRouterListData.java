package com.cyclone.minecraftiot.network;

import com.cyclone.minecraftiot.gui.GuiRouterSelect;
import com.cyclone.minecraftiot.tileentity.TileRouter;
import com.cyclone.minecraftiot.util.RouterCluster;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.world.World;

import java.util.List;

/**
 * 服务端 -> 客户端：执行器附近存在多个路由器组时，下发组列表，
 * 客户端弹出 GuiRouterSelect 供玩家选择要绑定的组。
 * 坐标使用组中心（下东南角：x 最大、y 最小、z 最大）。
 */
public class PacketRouterListData implements IMessage {

    public int ax, ay, az;            // 执行器坐标
    public int[] rx, ry, rz;          // 路由器组中心坐标（下东南角）
    public int[] blocks;              // 组方块数
    public int[] used;                // 组已用设备数
    public String[] alias;            // 组代表路由器别名
    public String[] mac;              // 组代表路由器 MAC

    public PacketRouterListData() {}

    public PacketRouterListData(int ax, int ay, int az, List<RouterCluster> clusters, World world) {
        this.ax = ax;
        this.ay = ay;
        this.az = az;
        int n = clusters.size();
        rx = new int[n];
        ry = new int[n];
        rz = new int[n];
        blocks = new int[n];
        used = new int[n];
        alias = new String[n];
        mac = new String[n];
        for (int i = 0; i < n; i++) {
            RouterCluster c = clusters.get(i);
            rx[i] = c.cx;
            ry[i] = c.cy;
            rz[i] = c.cz;
            blocks[i] = c.blockCount;
            used[i] = c.countBoundActuators(world);
            TileRouter r = (world.getTileEntity(c.cx, c.cy, c.cz) instanceof TileRouter)
                    ? (TileRouter) world.getTileEntity(c.cx, c.cy, c.cz) : null;
            alias[i] = r != null ? r.getAlias() : "";
            mac[i] = r != null ? r.getMac() : "";
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        ax = buf.readInt();
        ay = buf.readInt();
        az = buf.readInt();
        int n = buf.readInt();
        rx = new int[n];
        ry = new int[n];
        rz = new int[n];
        blocks = new int[n];
        used = new int[n];
        alias = new String[n];
        mac = new String[n];
        for (int i = 0; i < n; i++) {
            rx[i] = buf.readInt();
            ry[i] = buf.readInt();
            rz[i] = buf.readInt();
            blocks[i] = buf.readInt();
            used[i] = buf.readInt();
            alias[i] = readString(buf);
            mac[i] = readString(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(ax);
        buf.writeInt(ay);
        buf.writeInt(az);
        buf.writeInt(rx.length);
        for (int i = 0; i < rx.length; i++) {
            buf.writeInt(rx[i]);
            buf.writeInt(ry[i]);
            buf.writeInt(rz[i]);
            buf.writeInt(blocks[i]);
            buf.writeInt(used[i]);
            writeString(buf, alias[i]);
            writeString(buf, mac[i]);
        }
    }

    private static void writeString(ByteBuf buf, String s) {
        byte[] b = s == null ? new byte[0] : s.getBytes(java.nio.charset.Charset.forName("UTF-8"));
        buf.writeInt(b.length);
        buf.writeBytes(b);
    }

    private static String readString(ByteBuf buf) {
        int len = buf.readInt();
        byte[] b = new byte[Math.min(len, 512)];
        buf.readBytes(b);
        return new String(b, java.nio.charset.Charset.forName("UTF-8"));
    }

    public static class Handler implements IMessageHandler<PacketRouterListData, IMessage> {
        @Override
        public IMessage onMessage(PacketRouterListData message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                GuiRouterSelect.show(message);
            }
            return null;
        }
    }
}
