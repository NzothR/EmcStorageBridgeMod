package com.nzothr.emcstoragebridge.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class EmcStorageBridgeConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue DISPLAY_REFRESH_BUDGET_PER_TICK = BUILDER
            .comment("Maximum number of learned items recalculated for display per tick.")
            .defineInRange("display.refreshBudgetPerTick", 32, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue MAX_DISPLAY_AMOUNT = BUILDER
            .comment("Maximum amount shown for one item in network storage views.")
            .defineInRange("display.maxDisplayAmount", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.ConfigValue<String> DEFAULT_NBT_POLICY = BUILDER
            .comment("Default policy for items with NBT data: REJECT or ALLOW.")
            .defineInList("entry.defaultNbtPolicy", "REJECT", List.of("REJECT", "ALLOW"));

    public static final ForgeConfigSpec.BooleanValue ENABLE_DEBUG_LOG = BUILDER
            .comment("Log EMC cell registration and Knowledge/display cache lifecycle.")
            .define("debug.enableDebugLog", true);

    public static final ForgeConfigSpec.BooleanValue LOG_TRANSACTIONS = BUILDER
            .comment("Log executed EMC insert and extract attempts, including rejection reasons.")
            .define("debug.logTransactions", true);

    public static final ForgeConfigSpec.BooleanValue ENABLE_RS_INTEGRATION_WORKAROUND = BUILDER
            .comment("Enable targeted compatibility workarounds for RS Integration.")
            .define("compatibility.enableRsIntegrationWorkaround", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private EmcStorageBridgeConfig() {
    }
}
