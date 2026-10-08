package com.cyclone.minecraftiot.network;

import com.cyclone.minecraftiot.tileentity.TileSensor;
import com.cyclone.minecraftiot.util.BoilerAggregator.BoilerData;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.nio.charset.Charset;

/**
 * 服务端 -> 客户端：把传感器的扫描结果同步给客户端，
 * 供传感器 GUI 直接显示（不依赖 connector）。
 */
public class PacketSensorData implements IMessage {
    private int x, y, z;
    private String[] lines;   // scanResults（友好摘要，长度可变）
    private String[] raw;     // rawResults（原始 NBT 摘要）
    private String[] labels;  // 每方向注释（6 个）
    private BoilerData[] boiler; // 每方向 Railcraft 锅炉聚合数据（6 个，非锅炉为 null）
    private NBTTagCompound[] rawNbt; // 每方向服务端完整 NBT（供客户端模板编辑器拍平，非 IInventory 为 null）

    public PacketSensorData() {}

    public PacketSensorData(int x, int y, int z, String[] lines, String[] raw, String[] labels,
                            BoilerData[] boiler, NBTTagCompound[] rawNbt) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.lines = lines;
        this.raw = raw;
        this.labels = labels;
        this.boiler = boiler;
        this.rawNbt = rawNbt;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        int count = buf.readInt();
        lines = new String[count];
        for (int i = 0; i < count; i++) {
            if (buf.readBoolean()) {
                int length = buf.readInt();
                byte[] bytes = new byte[length];
                buf.readBytes(bytes);
                lines[i] = new String(bytes, Charset.forName("UTF-8"));
            } else {
                lines[i] = null;
            }
        }
        int rc = buf.readInt();
        raw = new String[rc];
        for (int i = 0; i < rc; i++) {
            if (buf.readBoolean()) {
                int length = buf.readInt();
                byte[] bytes = new byte[length];
                buf.readBytes(bytes);
                raw[i] = new String(bytes, Charset.forName("UTF-8"));
            } else {
                raw[i] = null;
            }
        }
        int lc = buf.readInt();
        labels = new String[lc];
        for (int i = 0; i < lc; i++) {
            if (buf.readBoolean()) {
                int length = buf.readInt();
                byte[] bytes = new byte[length];
                buf.readBytes(bytes);
                labels[i] = new String(bytes, Charset.forName("UTF-8"));
            } else {
                labels[i] = "";
            }
        }
        boiler = new BoilerData[6];
        for (int i = 0; i < 6; i++) {
            if (buf.readBoolean()) {
                BoilerData d = new BoilerData();
                float temp = buf.readFloat();
                d.temperature = Float.isNaN(temp) ? null : temp;
                d.waterAmt = (long) buf.readFloat();
                d.waterCap = (long) buf.readFloat();
                d.steamAmt = (long) buf.readFloat();
                d.steamCap = (long) buf.readFloat();
                boiler[i] = d;
            }
        }
        rawNbt = new NBTTagCompound[6];
        for (int i = 0; i < 6; i++) {
            if (buf.readBoolean()) {
                int length = buf.readInt();
                byte[] bytes = new byte[length];
                buf.readBytes(bytes);
                try {
                    rawNbt[i] = CompressedStreamTools.func_152457_a(bytes, NBTSizeTracker.field_152451_a);
                } catch (Exception e) {
                    rawNbt[i] = null;
                }
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        int count = lines == null ? 0 : lines.length;
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            if (lines[i] != null) {
                buf.writeBoolean(true);
                byte[] bytes = lines[i].getBytes(Charset.forName("UTF-8"));
                buf.writeInt(bytes.length);
                buf.writeBytes(bytes);
            } else {
                buf.writeBoolean(false);
            }
        }
        int rc = raw == null ? 0 : raw.length;
        buf.writeInt(rc);
        for (int i = 0; i < rc; i++) {
            if (raw[i] != null) {
                buf.writeBoolean(true);
                byte[] bytes = raw[i].getBytes(Charset.forName("UTF-8"));
                buf.writeInt(bytes.length);
                buf.writeBytes(bytes);
            } else {
                buf.writeBoolean(false);
            }
        }
        int lc = labels == null ? 0 : labels.length;
        buf.writeInt(lc);
        for (int i = 0; i < lc; i++) {
            String s = labels[i];
            if (s != null && !s.isEmpty()) {
                buf.writeBoolean(true);
                byte[] bytes = s.getBytes(Charset.forName("UTF-8"));
                buf.writeInt(bytes.length);
                buf.writeBytes(bytes);
            } else {
                buf.writeBoolean(false);
            }
        }
        for (int i = 0; i < 6; i++) {
            BoilerData d = (boiler != null && i < boiler.length) ? boiler[i] : null;
            if (d == null) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                buf.writeFloat(d.temperature == null ? Float.NaN : d.temperature);
                buf.writeFloat(d.waterAmt);
                buf.writeFloat(d.waterCap);
                buf.writeFloat(d.steamAmt);
                buf.writeFloat(d.steamCap);
            }
        }
        for (int i = 0; i < 6; i++) {
            NBTTagCompound n = (rawNbt != null && i < rawNbt.length) ? rawNbt[i] : null;
            if (n == null) {
                buf.writeBoolean(false);
                continue;
            }
            byte[] bytes;
            try {
                bytes = CompressedStreamTools.compress(n);
            } catch (Exception e) {
                buf.writeBoolean(false);
                continue;
            }
            buf.writeBoolean(true);
            buf.writeInt(bytes.length);
            buf.writeBytes(bytes);
        }
    }

    public static class Handler implements IMessageHandler<PacketSensorData, IMessage> {
        @Override
        public IMessage onMessage(PacketSensorData message, MessageContext ctx) {
            World world = Minecraft.getMinecraft().theWorld;
            if (world != null) {
                TileEntity te = world.getTileEntity(message.x, message.y, message.z);
                if (te instanceof TileSensor) {
                    ((TileSensor) te).setClientData(message.lines, message.raw, message.labels, message.boiler, message.rawNbt);
                }
            }
            return null;
        }
    }
}
