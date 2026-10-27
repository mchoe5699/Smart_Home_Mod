package com.mchoe.smarthome.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 서버 → 클라이언트: 조작화면 열기/갱신 */
public class ScreenDataPacket {
    public final BlockPos pos;
    public final boolean refreshOnly;
    public final CompoundTag data;

    public ScreenDataPacket(BlockPos pos, boolean refreshOnly, CompoundTag data) {
        this.pos = pos;
        this.refreshOnly = refreshOnly;
        this.data = data;
    }

    public static void encode(ScreenDataPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeBoolean(p.refreshOnly);
        buf.writeNbt(p.data);
    }

    public static ScreenDataPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean refresh = buf.readBoolean();
        CompoundTag tag = buf.readNbt();
        return new ScreenDataPacket(pos, refresh, tag == null ? new CompoundTag() : tag);
    }

    public static void handle(ScreenDataPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.mchoe.smarthome.client.ClientHooks.onScreenData(p.pos, p.refreshOnly, p.data)));
        ctx.get().setPacketHandled(true);
    }
}
