package com.nzothr.emcstoragebridge;

import com.nzothr.emcstoragebridge.config.EmcStorageBridgeConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(EmcStorageBridgeMod.MOD_ID)
public final class EmcStorageBridgeMod {
    public static final String MOD_ID = "emcstoragebridge";

    public EmcStorageBridgeMod(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, EmcStorageBridgeConfig.SPEC);
    }
}
