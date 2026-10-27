package com.mchoe.smarthome.client;

import com.mchoe.smarthome.ModRegistry;
import com.mchoe.smarthome.SmartHomeMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SmartHomeMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistry.DEVICE_BE.get(), DeviceRenderer::new);
        event.registerBlockEntityRenderer(ModRegistry.DASHBOARD_BE.get(), DashboardRenderer::new);
    }
}
