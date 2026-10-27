package com.mchoe.smarthome.client;

import com.mchoe.smarthome.ModRegistry;
import com.mchoe.smarthome.SmartHomeMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class ClientSetup {
    private ClientSetup() {}

    @Mod.EventBusSubscriber(modid = SmartHomeMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ModRegistry.DEVICE_BE.get(), DeviceRenderer::new);
            event.registerBlockEntityRenderer(ModRegistry.CONTROL_BE.get(), ControlRenderer::new);
        }
    }

    @Mod.EventBusSubscriber(modid = SmartHomeMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeBus {
        private static int ticks;

        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END && ++ticks % 100 == 0) CameraFeed.cleanup();
        }
    }
}
