package com.nzothr.emcstoragebridge.ae2;

import appeng.api.storage.StorageCells;
import com.mojang.logging.LogUtils;
import com.nzothr.emcstoragebridge.item.EmcStorageBridgeItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/** Loaded only when AE2 is present, keeping optional AE2 APIs out of the common bootstrap. */
public final class Ae2Integration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private Ae2Integration() {}

    public static void register(IEventBus modBus) {
        EmcStorageBridgeItems.registerAe2Item();
        com.nzothr.emcstoragebridge.core.EmcDisplayCache.addNetworkRefresher(EmcEntryRegistry::refreshOwnerNetworks);
        modBus.addListener(Ae2Integration::onCommonSetup);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            StorageCells.addCellHandler(EmcStorageCellHandler.INSTANCE);
            LOGGER.info("[EMCStorageBridge] Registered AE2 EMC Storage Cell handler");
        });
    }
}
