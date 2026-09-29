package com.nzothr.emcstoragebridge.rs;

import com.mojang.logging.LogUtils;
import com.refinedmods.refinedstorage.api.storage.StorageType;
import com.refinedmods.refinedstorage.apiimpl.API;
import com.nzothr.emcstoragebridge.config.EmcStorageBridgeConfig;
import com.nzothr.emcstoragebridge.core.EmcDisplayCache;
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
            API.instance().addExternalStorageProvider(StorageType.ITEM, new RsEmcExternalStorageProvider());
            EmcDisplayCache.addNetworkRefresher(RsEmcEntryRegistry::refreshOwnerNetworks);
            LOGGER.info("[EMCStorageBridge] Registered Refined Storage EMC Interface provider (default NBT policy={})",
                    EmcStorageBridgeConfig.DEFAULT_NBT_POLICY.get());
        });
    }
}
