package com.nzothr.emcstoragebridge.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class EmcStorageBridgeConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue DISPLAY_REFRESH_ROUND_TICKS = BUILDER
            .comment("Target duration of one complete learned-item display refresh round, in ticks.")
            .defineInRange("display.refreshRoundTicks", 200, 1, 72000);

    public static final ForgeConfigSpec.IntValue DISPLAY_REFRESH_INTERVAL_TICKS = BUILDER
            .comment("Ticks to wait between display refreshes within a round. 0 refreshes every tick.")
            .defineInRange("display.refreshIntervalTicks", 0, 0, 72000);

    public static final ForgeConfigSpec.IntValue MAX_DISPLAY_AMOUNT = BUILDER
            .comment("Maximum amount shown for one key in network storage views (items or mB of fluid).")
            .defineInRange("display.maxDisplayAmount", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.BooleanValue FLUID_ENABLED = BUILDER
            .comment("Expose ProjectE-valued fluids represented by learned filled buckets to AE2 and RS.")
            .define("fluid.enabled", true);

    public static final ForgeConfigSpec.ConfigValue<String> DEFAULT_NBT_POLICY = BUILDER
            .comment("Default policy for items with NBT data: REJECT or ALLOW.")
            .defineInList("entry.defaultNbtPolicy", "REJECT", List.of("REJECT", "ALLOW"));

    public static final ForgeConfigSpec.BooleanValue ENABLE_DEBUG_LOG = BUILDER
            .comment("Log EMC cell/interface registration and Knowledge/display cache lifecycle.")
            .define("debug.enableDebugLog", false);

    public static final ForgeConfigSpec.BooleanValue LOG_TRANSACTIONS = BUILDER
            .comment("Log executed EMC insert and extract attempts from AE2 and RS, including rejection reasons.")
            .define("debug.logTransactions", false);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private EmcStorageBridgeConfig() {
    }
}
