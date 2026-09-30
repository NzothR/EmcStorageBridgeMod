package com.nzothr.emcstoragebridge.core;

import java.util.HashMap;
import java.util.Map;

import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.world.item.ItemStack;

public final class ProjectEValueCache {
    private static final Map<ItemInfo, Long> VALUES = new HashMap<>();
    private static final Map<ItemInfo, Long> SELL_VALUES = new HashMap<>();

    private ProjectEValueCache() {
    }

    public static long getValue(ItemInfo info) {
        return VALUES.computeIfAbsent(info, IEMCProxy.INSTANCE::getValue);
    }

    /** Applies the same persistent-NBT normalization ProjectE uses in its transmutation table. */
    public static ItemInfo getPersistentInfo(ItemInfo info) {
        return IEMCProxy.INSTANCE.getPersistentInfo(info);
    }

    public static long getSellValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        ItemInfo info = ItemInfo.fromStack(stack);
        return SELL_VALUES.computeIfAbsent(info, IEMCProxy.INSTANCE::getSellValue);
    }

    public static void clear() {
        VALUES.clear();
        SELL_VALUES.clear();
    }
}
