package com.mchoe.smarthome.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 서버 → 클라이언트: 대시보드 지도(지형 색 + 구획) */
public class MapDataPacket {
    public final int originX, originZ, size;
    /** RGB 3바이트씩. 비어 있으면 구획 정보만 갱신 */
    public final byte[] rgb;
    public final CompoundTag zones;

    public MapDataPacket(int originX, int originZ, int size, byte[] rgb, CompoundTag zones) {
        this.originX = originX;
        this.originZ = originZ;
        this.size = size;
        this.rgb = rgb;
        this.zones = zones;
    }

    public static void encode(MapDataPacket p, FriendlyByteBuf buf) {
        buf.writeInt(p.originX);
        buf.writeInt(p.originZ);
        buf.writeVarInt(p.size);
        buf.writeByteArray(p.rgb);
        buf.writeNbt(p.zones);
    }

    public static MapDataPacket decode(FriendlyByteBuf buf) {
        int ox = buf.readInt(), oz = buf.readInt(), size = buf.readVarInt();
        byte[] rgb = buf.readByteArray(1 << 20);
        CompoundTag zones = buf.readNbt();
        return new MapDataPacket(ox, oz, size, rgb, zones == null ? new CompoundTag() : zones);
    }

    public static void handle(MapDataPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.mchoe.smarthome.client.ClientHooks.onMapData(p)));
        ctx.get().setPacketHandled(true);
    }
}
