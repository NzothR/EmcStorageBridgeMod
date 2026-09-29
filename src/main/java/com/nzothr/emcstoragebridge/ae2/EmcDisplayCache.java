package com.nzothr.emcstoragebridge.ae2;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import com.nzothr.emcstoragebridge.config.EmcStorageBridgeConfig;
import com.nzothr.emcstoragebridge.core.EmcMath;
import com.nzothr.emcstoragebridge.core.ProjectEAccountService;
import com.nzothr.emcstoragebridge.core.ProjectEValueCache;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;

/** Eventually consistent item counts; mutations always go through EmcTransactionCore. */
public final class EmcDisplayCache {
    private static final Map<UUID, State> STATES = new HashMap<>();

    private EmcDisplayCache() {
    }

    public static void tick() {
        int budget = EmcStorageBridgeConfig.DISPLAY_REFRESH_BUDGET_PER_TICK.get();
        for (Map.Entry<UUID, State> mapEntry : STATES.entrySet()) {
            State state = mapEntry.getValue();
            if (state.items.isEmpty()) continue;
            var account = ProjectEAccountService.getReadableAccount(mapEntry.getKey());
            if (account.isEmpty()) continue;
            BigInteger emc = account.get().getEmc();
            int work = Math.min(budget, state.items.size());
            boolean changed = false;
            for (int i = 0; i < work; i++) {
                ItemInfo info = state.items.get(state.cursor++ % state.items.size());
                changed |= update(state, info, emc);
            }
            if (changed) EmcEntryRegistry.refreshOwnerNetworks(mapEntry.getKey());
        }
    }

    public static synchronized void addAvailable(UUID owner, KeyCounter out, boolean allowNbt) {
        State state = state(owner);
        if (allowNbt) {
            out.addAll(state.snapshot);
            return;
        }
        for (var entry : state.snapshot) {
            if (entry.getKey() instanceof AEItemKey key && !key.hasTag()) {
                out.add(key, entry.getLongValue());
            }
        }
    }

    public static synchronized boolean isEmpty(UUID owner) {
        return state(owner).snapshot.isEmpty();
    }

    public static synchronized void refreshKey(UUID owner, ItemInfo info) {
        var account = ProjectEAccountService.getReadableAccount(owner);
        if (account.isEmpty()) return;
        State state = state(owner);
        boolean changed = update(state, info, account.get().getEmc());
        if (changed) EmcEntryRegistry.refreshOwnerNetworks(owner);
    }

    public static synchronized void knowledgeChanged(UUID owner) {
        STATES.remove(owner);
        EmcEntryRegistry.refreshOwnerNetworks(owner);
    }

    public static synchronized void knownItemAdded(UUID owner, ItemInfo info) {
        State state = state(owner);
        if (!state.items.contains(info)) state.items.add(info);
        var account = ProjectEAccountService.getReadableAccount(owner);
        if (account.isPresent() && update(state, info, account.get().getEmc())) {
            EmcEntryRegistry.refreshOwnerNetworks(owner);
        }
    }

    public static synchronized void clear() {
        for (UUID owner : STATES.keySet()) EmcEntryRegistry.refreshOwnerNetworks(owner);
        STATES.clear();
    }

    private static State state(UUID owner) {
        return STATES.computeIfAbsent(owner, id -> {
            State result = new State();
            var account = ProjectEAccountService.getReadableAccount(id);
            if (account.isPresent()) {
                IKnowledgeProvider provider = account.get();
                result.items = new ArrayList<>(provider.getKnowledge());
                result.items.sort((a, b) -> a.toString().compareTo(b.toString()));
                // Counts fill gradually by tick budget to avoid a full value lookup during network discovery.
            }
            return result;
        });
    }

    private static boolean update(State state, ItemInfo info, BigInteger balance) {
        long value = ProjectEValueCache.getValue(info);
        long amount = value <= 0 ? 0 : EmcMath.maxExtractable(balance, value,
                EmcStorageBridgeConfig.MAX_DISPLAY_AMOUNT.get());
        AEItemKey key = AEItemKey.of(info.createStack());
        long current = state.snapshot.get(key);
        if (amount == current) return false;
        if (amount <= 0) state.snapshot.remove(key);
        else state.snapshot.set(key, amount);
        return true;
    }

    private static final class State {
        private List<ItemInfo> items = List.of();
        private int cursor;
        private final KeyCounter snapshot = new KeyCounter();
    }
}
