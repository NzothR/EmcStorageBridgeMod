package com.nzothr.emcstoragebridge.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import com.mojang.logging.LogUtils;
import com.nzothr.emcstoragebridge.config.EmcStorageBridgeConfig;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.emc.EMCMappingHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/** Shared, integration-independent view of the items available from a ProjectE account. */
public final class EmcDisplayCache {
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Set<UUID> PENDING_OWNERS = new HashSet<>();
    private static final Set<UUID> PENDING_KNOWLEDGE_CHANGES = new HashSet<>();
    private static final Set<UUID> BRIDGE_LEARNS_IN_PROGRESS = new HashSet<>();
    private static final Set<UUID> FULL_KNOWLEDGE_NORMALIZATION_IN_PROGRESS = new HashSet<>();
    private static final Set<UUID> BULK_REFRESH_OWNERS = new HashSet<>();
    private static final Map<UUID, Long> REVISIONS = new HashMap<>();
    private static final List<Consumer<UUID>> NETWORK_REFRESHERS = new CopyOnWriteArrayList<>();
    private static final AtomicLong NEXT_REVISION = new AtomicLong();
    private static final Logger LOGGER = LogUtils.getLogger();

    private EmcDisplayCache() {}

    public static void addNetworkRefresher(Consumer<UUID> refresher) {
        NETWORK_REFRESHERS.add(refresher);
    }

    public static synchronized void tick() {
        int budget = EmcStorageBridgeConfig.DISPLAY_REFRESH_BUDGET_PER_TICK.get();
        for (UUID owner : List.copyOf(PENDING_KNOWLEDGE_CHANGES)) {
            PENDING_KNOWLEDGE_CHANGES.remove(owner);
            boolean expandedFullKnowledge = expandFullKnowledge(owner);
            State removed = STATES.remove(owner);
            PENDING_OWNERS.remove(owner);
            if (expandedFullKnowledge) BULK_REFRESH_OWNERS.add(owner);
            markChanged(owner);
            if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
                LOGGER.info("[EMCStorageBridge] Applied deferred Knowledge invalidation owner={} cachedItems={} pendingLookup={}",
                        owner, removed == null ? 0 : removed.items.size(), removed == null);
            }
        }
        for (UUID owner : List.copyOf(PENDING_OWNERS)) {
            if (state(owner) != null) {
                PENDING_OWNERS.remove(owner);
                markChanged(owner);
                if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
                    LOGGER.info("[EMCStorageBridge] ProjectE display lookup recovered; refreshing networks owner={}", owner);
                }
            }
        }
        for (Map.Entry<UUID, State> mapEntry : STATES.entrySet()) {
            UUID owner = mapEntry.getKey();
            State state = mapEntry.getValue();
            if (state.items.isEmpty()) {
                BULK_REFRESH_OWNERS.remove(owner);
                continue;
            }
            var account = ProjectEAccountService.getReadableAccount(owner);
            if (account.isEmpty()) continue;
            BigInteger emc = account.get().getEmc();
            int refreshBudget = BULK_REFRESH_OWNERS.contains(owner) ? Math.max(budget, 512) : budget;
            int work = Math.min(refreshBudget, state.items.size());
            boolean changed = false;
            for (int i = 0; i < work; i++) {
                ItemInfo info = state.items.get(state.cursor++ % state.items.size());
                changed |= update(state, info, emc);
            }
            if (!state.firstSweepLogged && state.cursor >= state.items.size()) {
                state.firstSweepLogged = true;
                BULK_REFRESH_OWNERS.remove(owner);
                if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
                    LOGGER.info("[EMCStorageBridge] Initial EMC display sweep complete owner={} knownItems={} visibleKeys={}",
                            owner, state.items.size(), state.snapshot.size());
                }
            }
            if (changed) markChanged(owner);
        }
    }

    public static synchronized List<ItemStack> getAvailableStacks(UUID owner, boolean allowNbt) {
        State state = state(owner);
        if (state == null || state.snapshot.isEmpty()) return List.of();
        List<ItemStack> result = new ArrayList<>(state.snapshot.size());
        for (Map.Entry<ItemInfo, Long> entry : state.snapshot.entrySet()) {
            ItemStack stack = entry.getKey().createStack();
            if (stack.isEmpty() || (!allowNbt && stack.hasTag())) continue;
            stack.setCount((int) Math.min(Integer.MAX_VALUE, entry.getValue()));
            result.add(stack);
        }
        return result;
    }

    public static synchronized boolean isEmpty(UUID owner) {
        State state = state(owner);
        return state == null || state.snapshot.isEmpty();
    }

    public static synchronized long revision(UUID owner) {
        return owner == null ? 0 : REVISIONS.getOrDefault(owner, 0L);
    }

    public static synchronized void refreshKey(UUID owner, ItemInfo info) {
        var account = ProjectEAccountService.getReadableAccount(owner);
        if (account.isEmpty()) return;
        State state = state(owner);
        if (state != null && update(state, info, account.get().getEmc())) markChanged(owner);
    }

    public static synchronized void knowledgeChanged(UUID owner) {
        if (owner != null && FULL_KNOWLEDGE_NORMALIZATION_IN_PROGRESS.contains(owner)) {
            return;
        }
        if (owner != null && BRIDGE_LEARNS_IN_PROGRESS.contains(owner)) {
            if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
                LOGGER.info("[EMCStorageBridge] Ignoring intermediate Knowledge event from EMC insertion owner={}", owner);
            }
            return;
        }
        if (owner != null && PENDING_KNOWLEDGE_CHANGES.add(owner)
                && EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
            LOGGER.info("[EMCStorageBridge] Queued Knowledge cache invalidation owner={}", owner);
        }
    }

    public static synchronized void beginBridgeLearn(UUID owner) {
        if (owner != null) BRIDGE_LEARNS_IN_PROGRESS.add(owner);
    }

    public static synchronized void endBridgeLearn(UUID owner) {
        if (owner != null) BRIDGE_LEARNS_IN_PROGRESS.remove(owner);
    }

    public static synchronized void knownItemAdded(UUID owner, ItemInfo info) {
        State state = state(owner);
        if (state == null) return;
        if (!state.items.contains(info)) state.items.add(info);
        var account = ProjectEAccountService.getReadableAccount(owner);
        if (account.isPresent() && update(state, info, account.get().getEmc())) markChanged(owner);
    }

    public static synchronized void clear() {
        Set<UUID> owners = new HashSet<>(STATES.keySet());
        owners.addAll(PENDING_OWNERS);
        STATES.clear();
        PENDING_OWNERS.clear();
        PENDING_KNOWLEDGE_CHANGES.clear();
        BRIDGE_LEARNS_IN_PROGRESS.clear();
        FULL_KNOWLEDGE_NORMALIZATION_IN_PROGRESS.clear();
        BULK_REFRESH_OWNERS.clear();
        owners.forEach(EmcDisplayCache::markChanged);
    }

    private static boolean expandFullKnowledge(UUID owner) {
        var account = ProjectEAccountService.getWritableAccount(owner);
        if (account.isEmpty() || !account.get().knowledge().hasFullKnowledge()) return false;

        IKnowledgeProvider provider = account.get().knowledge();
        List<ItemInfo> mappedItems = new ArrayList<>(EMCMappingHandler.getMappedItems());
        FULL_KNOWLEDGE_NORMALIZATION_IN_PROGRESS.add(owner);
        int added = 0;
        try {
            // ProjectE's full-knowledge flag prevents ordinary EMC items from being forgotten individually.
            // Materialize the current EMC mapping as explicit knowledge so the table can still unlearn items.
            provider.setFullKnowledge(false);
            for (ItemInfo info : mappedItems) {
                if (provider.addKnowledge(info)) added++;
            }
            provider.sync(account.get().player());
        } finally {
            FULL_KNOWLEDGE_NORMALIZATION_IN_PROGRESS.remove(owner);
        }
        if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
            LOGGER.info("[EMCStorageBridge] Expanded ProjectE full Knowledge owner={} mappedItems={} newlyAdded={} fullKnowledge={}",
                    owner, mappedItems.size(), added, provider.hasFullKnowledge());
        }
        return true;
    }

    private static State state(UUID owner) {
        if (owner == null) return null;
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
        long current = state.snapshot.getOrDefault(info, 0L);
        if (amount == current) return false;
        if (amount <= 0) state.snapshot.remove(info);
        else state.snapshot.put(info, amount);
        return true;
    }

    private static void markChanged(UUID owner) {
        REVISIONS.put(owner, NEXT_REVISION.incrementAndGet());
        for (Consumer<UUID> refresher : NETWORK_REFRESHERS) {
            try {
                refresher.accept(owner);
            } catch (RuntimeException e) {
                LOGGER.error("[EMCStorageBridge] Failed to refresh integration network owner={}", owner, e);
            }
        }
    }

    private static final class State {
        private List<ItemInfo> items = List.of();
        private int cursor;
        private boolean firstSweepLogged;
        private final Map<ItemInfo, Long> snapshot = new HashMap<>();
    }
}
