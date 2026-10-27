package com.mchoe.smarthome.client;

import com.mchoe.smarthome.client.screen.DashboardScreen;
import com.mchoe.smarthome.client.screen.DataScreen;
import com.mchoe.smarthome.client.screen.DeviceScreen;
import com.mchoe.smarthome.network.ActionPacket;
import com.mchoe.smarthome.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public final class ClientHooks {
    private ClientHooks() {}

    public static void onScreenData(BlockPos pos, boolean refreshOnly, CompoundTag data) {
        Minecraft mc = Minecraft.getInstance();
        Screen cur = mc.screen;
        if (cur instanceof DataScreen ds && ds.getPos().equals(pos)) {
            ds.updateData(data);
            return;
        }
        if (refreshOnly) return;
        if ("dashboard".equals(data.getString("kind"))) {
            mc.setScreen(new DashboardScreen(pos, data));
        } else {
            mc.setScreen(new DeviceScreen(pos, data));
        }
    }

    public static void send(BlockPos pos, String action, CompoundTag args) {
        ModNetwork.CHANNEL.sendToServer(new ActionPacket(pos, action, args == null ? new CompoundTag() : args));
    }

    public static ResourceLocation skin(UUID id) {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            PlayerInfo info = conn.getPlayerInfo(id);
            if (info != null) return info.getSkinLocation();
        }
        return DefaultPlayerSkin.getDefaultSkin(id);
    }
}
