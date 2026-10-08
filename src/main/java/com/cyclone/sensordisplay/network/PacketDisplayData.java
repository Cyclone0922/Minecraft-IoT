package com.cyclone.sensordisplay.network;

import com.cyclone.sensordisplay.SensorDisplayMod;
import com.cyclone.sensordisplay.tileentity.TileDisplay;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.nio.charset.Charset;

/**
 * 服务端 -> 客户端：显示器主控的多页显示数据 + 全部显示设置。
 * 每张绑定卡生成一页 String[]（套用表达式/白名单后的内容），渲染端按展示模式/溢出行为布局。
 */
public class PacketDisplayData implements IMessage {
    private int x, y, z;
    private float fontSize = 1.0f;
    private int alignMode = 0;
    private int textRot = 0;
    private int displayMode = 0;
    private int overflowMode = 0;
    private int columns = 1;
    private String[][] pages = new String[0][];

    public PacketDisplayData() {}

    public PacketDisplayData(int x, int y, int z, String[][] pages,
                             float fontSize, int alignMode, int textRot,
                             int displayMode, int overflowMode, int columns) {
        this.x = x; this.y = y; this.z = z;
        this.pages = pages;
        this.fontSize = fontSize;
        this.alignMode = alignMode;
        this.textRot = textRot;
        this.displayMode = displayMode;
        this.overflowMode = overflowMode;
        this.columns = columns;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt(); y = buf.readInt(); z = buf.readInt();
        fontSize = buf.readFloat();
        alignMode = buf.readInt();
        textRot = buf.readInt();
        displayMode = buf.readInt();
        overflowMode = buf.readInt();
        columns = buf.readInt();
        int pageCount = buf.readInt();
        pages = new String[pageCount][];
        for (int p = 0; p < pageCount; p++) {
            if (buf.readBoolean()) {
                int lineCount = buf.readInt();
                String[] page = new String[lineCount];
                for (int i = 0; i < lineCount; i++) {
                    if (buf.readBoolean()) {
                        int len = buf.readInt();
                        byte[] bytes = new byte[len];
                        buf.readBytes(bytes);
                        page[i] = new String(bytes, Charset.forName("UTF-8"));
                    } else {
                        page[i] = null;
                    }
                }
                pages[p] = page;
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
        buf.writeFloat(fontSize);
        buf.writeInt(alignMode);
        buf.writeInt(textRot);
        buf.writeInt(displayMode);
        buf.writeInt(overflowMode);
        buf.writeInt(columns);
        int pageCount = pages == null ? 0 : pages.length;
        buf.writeInt(pageCount);
        for (int p = 0; p < pageCount; p++) {
            String[] page = pages[p];
            if (page != null) {
                buf.writeBoolean(true);
                buf.writeInt(page.length);
                for (int i = 0; i < page.length; i++) {
                    if (page[i] != null) {
                        buf.writeBoolean(true);
                        byte[] bytes = page[i].getBytes(Charset.forName("UTF-8"));
                        buf.writeInt(bytes.length);
                        buf.writeBytes(bytes);
                    } else {
                        buf.writeBoolean(false);
                    }
                }
            } else {
                buf.writeBoolean(false);
            }
        }
    }

    public static class Handler implements IMessageHandler<PacketDisplayData, IMessage> {
        @Override
        public IMessage onMessage(PacketDisplayData message, MessageContext ctx) {
            World world = Minecraft.getMinecraft().theWorld;
            if (world != null) {
                TileEntity te = world.getTileEntity(message.x, message.y, message.z);
                if (te instanceof TileDisplay) {
                    TileDisplay display = (TileDisplay) te;
                    display.setPages(message.pages);
                    display.applySettingsClient(message.fontSize, message.alignMode, message.textRot,
                            message.displayMode, message.overflowMode, message.columns);
                    int n = 0;
                    if (message.pages != null) {
                        for (String[] pg : message.pages) {
                            if (pg != null && pg.length > 0) n++;
                        }
                    }
                    SensorDisplayMod.log.info("[DisplayPacket@client] (" + message.x + "," + message.y + "," + message.z + ") " + n + " page(s)");
                }
            }
            return null;
        }
    }
}
