package com.cyclone.minecraftiot.network;

import com.cyclone.minecraftiot.gui.GuiActuatorSelect;
import com.cyclone.minecraftiot.tileentity.TileActuator;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

import java.util.List;

/**
 * 服务端 -> 客户端：同网络执行器列表，客户端弹 GuiActuatorSelect 让玩家选要绑定哪个做物品源。
 */
public class PacketActuatorListData implements IMessage {

    public int ax, ay, az;             // 请求方执行器坐标
    public int[] tx, ty, tz;           // 候选目标执行器坐标
    public String[] skillName;         // 目标已安装技能名
    public int[] takeBufSize;          // 目标 takeBuffer 当前物品数
    public boolean[] itemTransfer;     // 目标是否装有物品转移插件（take() 才有意义）

    public PacketActuatorListData() {}

    public PacketActuatorListData(int ax, int ay, int az, List<TileActuator> list) {
        this.ax = ax;
        this.ay = ay;
        this.az = az;
        int n = list.size();
        tx = new int[n];
        ty = new int[n];
        tz = new int[n];
        skillName = new String[n];
        takeBufSize = new int[n];
        itemTransfer = new boolean[n];
        for (int i = 0; i < n; i++) {
            TileActuator a = list.get(i);
            tx[i] = a.xCoord;
            ty[i] = a.yCoord;
            tz[i] = a.zCoord;
            String sn = a.getSkillName();
            skillName[i] = sn == null ? "" : sn;
            takeBufSize[i] = a.getTakeBufferSize();
            itemTransfer[i] = a.hasItemTransferSkill();
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        ax = buf.readInt();
        ay = buf.readInt();
        az = buf.readInt();
        int n = buf.readInt();
        tx = new int[n];
        ty = new int[n];
        tz = new int[n];
        skillName = new String[n];
        takeBufSize = new int[n];
        itemTransfer = new boolean[n];
        for (int i = 0; i < n; i++) {
            tx[i] = buf.readInt();
            ty[i] = buf.readInt();
            tz[i] = buf.readInt();
            skillName[i] = readString(buf);
            takeBufSize[i] = buf.readInt();
            itemTransfer[i] = buf.readBoolean();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(ax);
        buf.writeInt(ay);
        buf.writeInt(az);
        buf.writeInt(tx.length);
        for (int i = 0; i < tx.length; i++) {
            buf.writeInt(tx[i]);
            buf.writeInt(ty[i]);
            buf.writeInt(tz[i]);
            writeString(buf, skillName[i]);
            buf.writeInt(takeBufSize[i]);
            buf.writeBoolean(itemTransfer[i]);
        }
    }

    private static void writeString(ByteBuf buf, String s) {
        byte[] b = s == null ? new byte[0] : s.getBytes(java.nio.charset.Charset.forName("UTF-8"));
        buf.writeInt(b.length);
        buf.writeBytes(b);
    }

    private static String readString(ByteBuf buf) {
        int len = buf.readInt();
        byte[] b = new byte[Math.min(len, 256)];
        buf.readBytes(b);
        return new String(b, java.nio.charset.Charset.forName("UTF-8"));
    }

    public static class Handler implements IMessageHandler<PacketActuatorListData, IMessage> {
        @Override
        public IMessage onMessage(PacketActuatorListData msg, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                GuiActuatorSelect.show(msg);
            }
            return null;
        }
    }
}
