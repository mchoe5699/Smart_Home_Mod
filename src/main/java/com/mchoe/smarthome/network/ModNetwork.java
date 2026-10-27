package com.mchoe.smarthome.network;

import com.mchoe.smarthome.SmartHomeMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class ModNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SmartHomeMod.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, ScreenDataPacket.class, ScreenDataPacket::encode, ScreenDataPacket::decode,
                ScreenDataPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ActionPacket.class, ActionPacket::encode, ActionPacket::decode,
                ActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
