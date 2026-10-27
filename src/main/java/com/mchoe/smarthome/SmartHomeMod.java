package com.mchoe.smarthome;

import com.mchoe.smarthome.logic.CallManager;
import com.mchoe.smarthome.network.ModNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod(SmartHomeMod.MODID)
public class SmartHomeMod {
    public static final String MODID = "smart_home_mod";

    public SmartHomeMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModRegistry.register(modBus);
        // 패킷 채널은 생성자에서 바로 등록 (우클릭 조작화면이 항상 열리도록)
        ModNetwork.register();

        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onChat);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && ServerLifecycleHooks.getCurrentServer() != null) CallManager.tick(ServerLifecycleHooks.getCurrentServer());
    }

    private void onChat(ServerChatEvent event) {
        CallManager.relayChat(event.getPlayer(), event.getRawText());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        CallManager.clear();
    }
}
