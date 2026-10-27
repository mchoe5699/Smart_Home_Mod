package com.mchoe.smarthome.network;

import com.mchoe.smarthome.SmartHomeMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class ModNetwork {
    private static final String VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SmartHomeMod.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);
    private static boolean registered;

    private ModNetwork() {}

    public static void register() {
        if (registered) return;
        registered = true;
        int id = 0;
        CHANNEL.registerMessage(id++, ScreenDataPacket.class, ScreenDataPacket::encode, ScreenDataPacket::decode,
                ScreenDataPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ActionPacket.class, ActionPacket::encode, ActionPacket::decode,
                ActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, MapDataPacket.class, MapDataPacket::encode, MapDataPacket::decode,
                MapDataPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
