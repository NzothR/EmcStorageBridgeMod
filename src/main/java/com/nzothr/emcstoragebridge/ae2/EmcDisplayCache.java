package com.nzothr.emcstoragebridge.ae2;

import java.util.UUID;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import moze_intel.projecte.api.ItemInfo;

/** AE2 stack-counter adapter over the shared ProjectE display cache. */
public final class EmcDisplayCache {
    private EmcDisplayCache() {}

    public static void tick() { com.nzothr.emcstoragebridge.core.EmcDisplayCache.tick(); }
    public static boolean isEmpty(UUID owner) { return com.nzothr.emcstoragebridge.core.EmcDisplayCache.isEmpty(owner); }
    public static long revision(UUID owner) { return com.nzothr.emcstoragebridge.core.EmcDisplayCache.revision(owner); }
    public static void refreshKey(UUID owner, ItemInfo info) { com.nzothr.emcstoragebridge.core.EmcDisplayCache.refreshKey(owner, info); }
    public static void knowledgeChanged(UUID owner) { com.nzothr.emcstoragebridge.core.EmcDisplayCache.knowledgeChanged(owner); }
    public static void knownItemAdded(UUID owner, ItemInfo info) { com.nzothr.emcstoragebridge.core.EmcDisplayCache.knownItemAdded(owner, info); }
    public static void clear() { com.nzothr.emcstoragebridge.core.EmcDisplayCache.clear(); }

    public static void addAvailable(UUID owner, KeyCounter out) {
        for (var stack : com.nzothr.emcstoragebridge.core.EmcDisplayCache.getAvailableStacks(owner)) {
            out.add(AEItemKey.of(stack), stack.getCount());
        }
    }
}
