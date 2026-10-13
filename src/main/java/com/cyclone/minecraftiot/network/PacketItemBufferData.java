package com.cyclone.minecraftiot.network;

import com.cyclone.minecraftiot.tileentity.TileItemBuffer;
import com.cyclone.minecraftiot.util.ItemPool;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.List;

/**
 * 服务端 -> 客户端：物品缓存器池快照（AE2 面板只读显示用）。
 * 包含容量上限、总量、种类数，以及每种物品 (itemId, meta, count) 列表。
 */
public class PacketItemBufferData implements IMessage {
    private int x, y, z;
    private boolean drMode;
    private int kindCap, itemCap, totalCount, kindCount;
    private int[] itemIds, metas, counts;

    public PacketItemBufferData() {}

    /** 服务端：从缓存器 tile 组装快照 */
    public static PacketItemBufferData build(TileItemBuffer b) {
        PacketItemBufferData p = new PacketItemBufferData();
        p.x = b.xCoord;
        p.y = b.yCoord;
        p.z = b.zCoord;
        p.drMode = b.isDrMode();
        p.kindCap = b.getActivePoolKindCapacity();
        p.itemCap = b.getActivePoolItemCapacity();
        ItemPool pool = b.getActivePool();
        p.totalCount = pool == null ? 0 : pool.getTotal();
        List<Object[]> pairs = pool == null ? null : pool.snapshotPairs();
        // 设计要求：池内按 NBT/耐久分开存储，但 GUI 合并显示同种物品——
        // 因此按 item id 聚合不同 damage/tag 的条目，贴图统一用 damage=0 的完整形态，数量为总和。
        java.util.LinkedHashMap<Integer, long[]> merged = new java.util.LinkedHashMap<Integer, long[]>();
        // key=itemId, value={countSum, firstMeta}
        if (pairs != null) {
            for (Object[] pr : pairs) {
                ItemStack s = (ItemStack) pr[0];
                int c = ((Integer) pr[1]).intValue();
                int id = Item.getIdFromItem(s.getItem());
                long[] v = merged.get(id);
                if (v == null) {
                    merged.put(id, new long[]{c, s.getItemDamage()});
                } else {
                    v[0] += c;
                }
            }
        }
        p.kindCount = merged.size();
        p.itemIds = new int[p.kindCount];
        p.metas = new int[p.kindCount];
        p.counts = new int[p.kindCount];
        int i = 0;
        for (java.util.Map.Entry<Integer, long[]> e : merged.entrySet()) {
            p.itemIds[i] = e.getKey().intValue();
            p.metas[i] = 0; // 合并显示统一用完整形态（无耐久条）
            p.counts[i] = (int) e.getValue()[0];
            i++;
        }
        return p;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        drMode = buf.readBoolean();
        kindCap = buf.readInt();
        itemCap = buf.readInt();
        totalCount = buf.readInt();
        kindCount = buf.readInt();
        itemIds = new int[kindCount];
        metas = new int[kindCount];
        counts = new int[kindCount];
        for (int i = 0; i < kindCount; i++) {
            itemIds[i] = buf.readInt();
            metas[i] = buf.readInt();
            counts[i] = buf.readInt();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeBoolean(drMode);
        buf.writeInt(kindCap);
        buf.writeInt(itemCap);
        buf.writeInt(totalCount);
        buf.writeInt(kindCount);
        for (int i = 0; i < kindCount; i++) {
            buf.writeInt(itemIds[i]);
            buf.writeInt(metas[i]);
            buf.writeInt(counts[i]);
        }
    }

    public static class Handler implements IMessageHandler<PacketItemBufferData, IMessage> {
        @Override
        public IMessage onMessage(PacketItemBufferData message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                World world = Minecraft.getMinecraft().theWorld;
                if (world == null) return null;
                TileEntity te = world.getTileEntity(message.x, message.y, message.z);
                if (te instanceof TileItemBuffer) {
                    ((TileItemBuffer) te).applyClientData(message);
                }
            }
            return null;
        }
    }

    // ===== 客户端 tile 应用 =====

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public boolean getDrMode() { return drMode; }
    public int getKindCap() { return kindCap; }
    public int getItemCap() { return itemCap; }
    public int getTotalCount() { return totalCount; }
    public int getKindCount() { return kindCount; }
    public int[] getItemIds() { return itemIds; }
    public int[] getMetas() { return metas; }
    public int[] getCounts() { return counts; }
}
