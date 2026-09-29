package com.nzothr.emcstoragebridge.ae2;

import com.nzothr.emcstoragebridge.EmcStorageBridgeMod;
import com.nzothr.emcstoragebridge.core.ProjectEValueCache;
import moze_intel.projecte.api.event.PlayerKnowledgeChangeEvent;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.util.profiling.ProfilerFiller;

@Mod.EventBusSubscriber(modid = EmcStorageBridgeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EmcStorageBridgeEvents {
    private EmcStorageBridgeEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) EmcDisplayCache.tick();
    }

    @SubscribeEvent
    public static void onKnowledgeChanged(PlayerKnowledgeChangeEvent event) {
        EmcDisplayCache.knowledgeChanged(event.getPlayerUUID());
    }

    @SubscribeEvent
    public static void onReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void ignored, ResourceManager resourceManager, ProfilerFiller profiler) {
                ProjectEValueCache.clear();
                EmcDisplayCache.clear();
            }
        });
    }
}
