package com.nzothr.emcstoragebridge;

import com.nzothr.emcstoragebridge.config.EmcStorageBridgeConfig;
import com.nzothr.emcstoragebridge.ae2.EmcStorageCellHandler;
import com.nzothr.emcstoragebridge.item.EmcStorageBridgeItems;
import com.nzothr.emcstoragebridge.rs.EmcInterfaceBlocks;
import com.nzothr.emcstoragebridge.rs.RsIntegration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.ModList;

@Mod(EmcStorageBridgeMod.MOD_ID)
public final class EmcStorageBridgeMod {
    public static final String MOD_ID = "emcstoragebridge";

    public EmcStorageBridgeMod(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, EmcStorageBridgeConfig.SPEC);
        IEventBus modBus = context.getModEventBus();
        EmcStorageBridgeItems.ITEMS.register(modBus);
        EmcStorageBridgeItems.CREATIVE_TABS.register(modBus);
        EmcInterfaceBlocks.BLOCKS.register(modBus);
        EmcInterfaceBlocks.BLOCK_ENTITIES.register(modBus);
        modBus.addListener(this::commonSetup);
        if (ModList.get().isLoaded("refinedstorage")) {
            modBus.addListener(RsIntegration::onCommonSetup);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> appeng.api.storage.StorageCells.addCellHandler(EmcStorageCellHandler.INSTANCE));
    }
}
