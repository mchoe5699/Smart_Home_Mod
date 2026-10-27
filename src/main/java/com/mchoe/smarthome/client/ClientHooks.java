package com.mchoe.smarthome.client;

import com.mchoe.smarthome.client.screen.ComputerScreen;
import com.mchoe.smarthome.client.screen.ControlScreen;
import com.mchoe.smarthome.client.screen.DashboardMapScreen;
import com.mchoe.smarthome.client.screen.DataScreen;
import com.mchoe.smarthome.client.screen.DoorGui;
import com.mchoe.smarthome.client.screen.GuardGui;
import com.mchoe.smarthome.client.screen.InterphoneGui;
import com.mchoe.smarthome.client.screen.LobbyGui;
import com.mchoe.smarthome.client.screen.VideophoneGui;
import com.mchoe.smarthome.client.screen.WallpadGui;
import com.mchoe.smarthome.logic.DeviceType;
import com.mchoe.smarthome.network.ActionPacket;
import com.mchoe.smarthome.network.MapDataPacket;
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
        Screen next = switch (data.getString("kind")) {
            case "dashboard" -> new DashboardMapScreen(pos);
            case "computer" -> new ComputerScreen(pos, data);
            case "control" -> new ControlScreen(pos, data);
            default -> switch (DeviceType.byId(data.getString("type"))) {
                case WALLPAD -> new WallpadGui(pos, data);
                case VIDEOPHONE, VIDEOPHONE_BASIC -> new VideophoneGui(pos, data);
                case INTERPHONE, INTERPHONE_BASIC -> new InterphoneGui(pos, data);
                case LOBBY_PHONE -> new LobbyGui(pos, data);
                case GUARD_STATION -> new GuardGui(pos, data);
                case DOOR_CAMERA, DOOR_PHONE -> new DoorGui(pos, data);
            };
        };
        mc.setScreen(next);
    }

    public static void onMapData(MapDataPacket p) {
        if (Minecraft.getInstance().screen instanceof DashboardMapScreen s) s.applyMap(p);
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
