package com.nzothr.emcstoragebridge;

import com.nzothr.emcstoragebridge.config.EmcStorageBridgeConfig;
import com.nzothr.emcstoragebridge.item.EmcStorageBridgeItems;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

@Mod(EmcStorageBridgeMod.MOD_ID)
public final class EmcStorageBridgeMod {
    public static final String MOD_ID = "emcstoragebridge";

    public EmcStorageBridgeMod(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, EmcStorageBridgeConfig.SPEC);
        IEventBus modBus = context.getModEventBus();
        EmcStorageBridgeItems.ITEMS.register(modBus);
        EmcStorageBridgeItems.CREATIVE_TABS.register(modBus);
        if (ModList.get().isLoaded("ae2")) {
            com.nzothr.emcstoragebridge.ae2.Ae2Integration.register(modBus);
        }
        if (ModList.get().isLoaded("refinedstorage")) {
            com.nzothr.emcstoragebridge.rs.RsIntegration.register(modBus);
        }
    }
}
