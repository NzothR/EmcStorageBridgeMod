package com.nzothr.emcstoragebridge.ae2;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import com.mojang.logging.LogUtils;
import com.nzothr.emcstoragebridge.config.EmcStorageBridgeConfig;
import com.nzothr.emcstoragebridge.core.EmcMath;
import com.nzothr.emcstoragebridge.core.ProjectEAccountService;
import com.nzothr.emcstoragebridge.core.ProjectEValueCache;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/** Eventually consistent item counts; mutations always go through EmcTransactionCore. */
public final class EmcDisplayCache {
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Set<UUID> PENDING_OWNERS = new HashSet<>();
    private static final Set<UUID> PENDING_KNOWLEDGE_CHANGES = new HashSet<>();
    private static final Map<UUID, Long> REVISIONS = new HashMap<>();
    private static final AtomicLong NEXT_REVISION = new AtomicLong();
    private static final Logger LOGGER = LogUtils.getLogger();

    private EmcDisplayCache() {
    }

    public static synchronized void tick() {
        int budget = EmcStorageBridgeConfig.DISPLAY_REFRESH_BUDGET_PER_TICK.get();
        for (UUID owner : List.copyOf(PENDING_KNOWLEDGE_CHANGES)) {
            PENDING_KNOWLEDGE_CHANGES.remove(owner);
            State removed = STATES.remove(owner);
            PENDING_OWNERS.remove(owner);
            markChanged(owner);
            EmcEntryRegistry.refreshOwnerNetworks(owner);
            if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
                LOGGER.info("[EMCStorageBridge] Applied deferred Knowledge invalidation owner={} cachedItems={} pendingLookup={}",
                        owner, removed == null ? 0 : removed.items.size(), removed == null);
            }
        }
        for (UUID owner : List.copyOf(PENDING_OWNERS)) {
            if (state(owner) != null) {
                PENDING_OWNERS.remove(owner);
                EmcEntryRegistry.refreshOwnerNetworks(owner);
            }
        }
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
            if (!state.firstSweepLogged && state.cursor >= state.items.size()) {
                state.firstSweepLogged = true;
                if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
                    LOGGER.info("[EMCStorageBridge] Initial EMC display sweep complete owner={} knownItems={} visibleKeys={}",
                            mapEntry.getKey(), state.items.size(), state.snapshot.size());
                }
            }
            if (changed) {
                markChanged(mapEntry.getKey());
                EmcEntryRegistry.refreshOwnerNetworks(mapEntry.getKey());
            }
        }
    }

    public static synchronized void addAvailable(UUID owner, KeyCounter out, boolean allowNbt) {
        State state = state(owner);
        if (state == null) return;
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
        State state = state(owner);
        return state == null || state.snapshot.isEmpty();
    }

    public static synchronized List<net.minecraft.world.item.ItemStack> getAvailableStacks(UUID owner, boolean allowNbt) {
        State state = state(owner);
        if (state == null || state.snapshot.isEmpty()) return List.of();
        List<net.minecraft.world.item.ItemStack> result = new ArrayList<>(state.snapshot.size());
        for (var entry : state.snapshot) {
            if (!(entry.getKey() instanceof AEItemKey key) || (!allowNbt && key.hasTag())) continue;
            net.minecraft.world.item.ItemStack stack = key.toStack();
            stack.setCount((int) Math.min(Integer.MAX_VALUE, entry.getLongValue()));
            if (!stack.isEmpty()) result.add(stack);
        }
        return result;
    }

    public static synchronized long revision(UUID owner) {
        return owner == null ? 0 : REVISIONS.getOrDefault(owner, 0L);
    }

    public static synchronized void refreshKey(UUID owner, ItemInfo info) {
        var account = ProjectEAccountService.getReadableAccount(owner);
        if (account.isEmpty()) return;
        State state = state(owner);
        if (state == null) return;
        boolean changed = update(state, info, account.get().getEmc());
        if (changed) {
            markChanged(owner);
            EmcEntryRegistry.refreshOwnerNetworks(owner);
        }
    }

    public static synchronized void knowledgeChanged(UUID owner) {
        if (owner != null && PENDING_KNOWLEDGE_CHANGES.add(owner)
                && EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
            LOGGER.info("[EMCStorageBridge] Queued Knowledge cache invalidation owner={}", owner);
        }
    }

    public static synchronized void knownItemAdded(UUID owner, ItemInfo info) {
        State state = state(owner);
        if (state == null) return;
        if (!state.items.contains(info)) state.items.add(info);
        var account = ProjectEAccountService.getReadableAccount(owner);
        if (account.isPresent() && update(state, info, account.get().getEmc())) {
            markChanged(owner);
            EmcEntryRegistry.refreshOwnerNetworks(owner);
        }
    }

    public static synchronized void clear() {
        for (UUID owner : STATES.keySet()) {
            markChanged(owner);
            EmcEntryRegistry.refreshOwnerNetworks(owner);
        }
        STATES.clear();
        PENDING_OWNERS.clear();
        PENDING_KNOWLEDGE_CHANGES.clear();
    }

    private static State state(UUID owner) {
        State existing = STATES.get(owner);
        if (existing != null) return existing;
        var account = ProjectEAccountService.getReadableAccount(owner);
        if (account.isEmpty()) {
            if (PENDING_OWNERS.add(owner) && EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                LOGGER.warn("[EMCStorageBridge] Deferred ProjectE display lookup owner={} serverPresent={} serverThread={}",
                        owner, server != null, server != null && server.isSameThread());
            }
            return null;
        }

        IKnowledgeProvider provider = account.get();
        State created = new State();
        created.items = new ArrayList<>(provider.getKnowledge());
        created.items.sort((a, b) -> a.toString().compareTo(b.toString()));
        // Counts fill gradually by tick budget to avoid a full value lookup during network discovery.
        STATES.put(owner, created);
        if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
            LOGGER.info("[EMCStorageBridge] Initialized display snapshot owner={} knownItems={} fullKnowledge={} EMC={}",
                    owner, created.items.size(), provider.hasFullKnowledge(), provider.getEmc());
        }
        return created;
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

    private static void markChanged(UUID owner) {
        REVISIONS.put(owner, NEXT_REVISION.incrementAndGet());
    }

    private static final class State {
        private List<ItemInfo> items = List.of();
        private int cursor;
        private boolean firstSweepLogged;
        private final KeyCounter snapshot = new KeyCounter();
    }
}
