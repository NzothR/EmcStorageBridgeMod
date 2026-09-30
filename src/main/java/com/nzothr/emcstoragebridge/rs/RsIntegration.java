package com.nzothr.emcstoragebridge.rs;

import com.mojang.logging.LogUtils;
import com.refinedmods.refinedstorage.api.storage.StorageType;
import com.refinedmods.refinedstorage.apiimpl.API;
import com.nzothr.emcstoragebridge.config.EmcStorageBridgeConfig;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

public final class RsIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();

    private RsIntegration() {
    }

    public static void register(IEventBus modBus) {
        EmcInterfaceBlocks.BLOCKS.register(modBus);
        EmcInterfaceBlocks.BLOCK_ENTITIES.register(modBus);
        modBus.addListener(RsIntegration::onCommonSetup);
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            RsEmcExternalStorageProvider provider = new RsEmcExternalStorageProvider();
            RsEmcFluidExternalStorageProvider fluidProvider = new RsEmcFluidExternalStorageProvider();
            API.instance().addExternalStorageProvider(StorageType.ITEM, provider);
            API.instance().addExternalStorageProvider(StorageType.FLUID, fluidProvider);
            var providers = API.instance().getExternalStorageProviders(StorageType.ITEM);
            boolean registered = providers.stream().anyMatch(candidate -> (Object) candidate == provider);
            if (registered) {
                LOGGER.info("[EMCStorageBridge] Registered Refined Storage EMC Interface provider priority={} providers={} (default NBT policy={})",
                        provider.getPriority(), providers.stream()
                                .map(candidate -> candidate.getClass().getSimpleName() + ":" + candidate.getPriority()).toList(),
                        EmcStorageBridgeConfig.DEFAULT_NBT_POLICY.get());
            } else {
                LOGGER.error("[EMCStorageBridge] Refined Storage discarded the EMC Interface provider; registered providers={}",
                        providers.stream().map(candidate -> candidate.getClass().getName() + ":" + candidate.getPriority()).toList());
            }
            var fluidProviders = API.instance().getExternalStorageProviders(StorageType.FLUID);
            boolean fluidRegistered = fluidProviders.stream().anyMatch(candidate -> (Object) candidate == fluidProvider);
            if (fluidRegistered) {
                LOGGER.info("[EMCStorageBridge] Registered Refined Storage EMC fluid provider priority={} providers={}",
                        fluidProvider.getPriority(), fluidProviders.stream()
                                .map(candidate -> candidate.getClass().getSimpleName() + ":" + candidate.getPriority()).toList());
            } else {
                LOGGER.error("[EMCStorageBridge] Refined Storage discarded the EMC fluid provider; registered providers={}",
                        fluidProviders.stream().map(candidate -> candidate.getClass().getName() + ":" + candidate.getPriority()).toList());
            }
        });
    }
}
