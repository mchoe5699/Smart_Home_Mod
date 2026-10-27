package com.mchoe.smarthome.network;

import com.mchoe.smarthome.logic.DeviceLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 클라이언트 → 서버: 조작화면 버튼 */
public class ActionPacket {
    public final BlockPos pos;
    public final String action;
    public final CompoundTag args;

    public ActionPacket(BlockPos pos, String action, CompoundTag args) {
        this.pos = pos;
        this.action = action;
        this.args = args;
    }

    public static void encode(ActionPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeUtf(p.action, 32);
        buf.writeNbt(p.args);
    }

    public static ActionPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String action = buf.readUtf(32);
        CompoundTag tag = buf.readNbt();
        return new ActionPacket(pos, action, tag == null ? new CompoundTag() : tag);
    }

    public static void handle(ActionPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (player != null) DeviceLogic.handleAction(player, p.pos, p.action, p.args);
        });
        ctx.get().setPacketHandled(true);
    }
}
