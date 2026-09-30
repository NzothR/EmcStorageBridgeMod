package com.nzothr.emcstoragebridge.core;

import com.mojang.logging.LogUtils;
import com.nzothr.emcstoragebridge.config.EmcStorageBridgeConfig;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.emc.EMCMappingHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

/** Platform-neutral fluid catalog, prepaid bucket cache, and EMC transaction boundary. */
public final class EmcFluidStorageCore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Map<FluidKey, Long>> PRICES = new HashMap<>();

    private EmcFluidStorageCore() {}

    public static synchronized List<FluidStack> getAvailableFluids(UUID owner) {
        if (!EmcStorageBridgeConfig.FLUID_ENABLED.get()) return List.of();
        var account = ProjectEAccountService.getReadableAccount(owner);
        if (account.isEmpty()) return List.of();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return List.of();

        BigInteger balance = account.get().getEmc();
        EmcFluidCacheData cache = EmcFluidCacheData.get(server);
        Map<FluidKey, Long> prices = prices(owner, account.get());
        Set<FluidKey> union = new java.util.HashSet<>(prices.keySet());
        union.addAll(cache.getKeys(owner));
        List<FluidKey> keys = new ArrayList<>(union);
        keys.sort(Comparator.comparing(key -> key.fluidId().toString() + key.tag()));
        List<FluidStack> result = new ArrayList<>(keys.size());
        long displayLimit = EmcStorageBridgeConfig.MAX_DISPLAY_AMOUNT.get();
        for (FluidKey key : keys) {
            Fluid fluid = key.resolveFluid();
            long price = prices.getOrDefault(key, 0L);
            if (fluid == null) continue;
            long available = FluidBucketMath.availableAmount(balance, cache.getAmount(owner, key), price, displayLimit);
            if (available > 0) result.add(new FluidStack(fluid, (int) available, key.tag()));
        }
        return result;
    }

    /** Drains up to requested mB. Simulation is read-only; execution prepays complete buckets. */
    public static synchronized long extract(UUID owner, FluidStack prototype, long requested, boolean execute) {
        if (!EmcStorageBridgeConfig.FLUID_ENABLED.get() || owner == null || prototype == null
                || prototype.isEmpty() || requested <= 0) {
            if (execute) log(owner, "unknown", requested, 0, "disabled-or-invalid-request");
            return 0;
        }
        var account = ProjectEAccountService.getReadableAccount(owner);
        if (account.isEmpty()) {
            if (execute) log(owner, prototype.getDisplayName().getString(), requested, 0, "account-unavailable");
            return 0;
        }
        FluidKey key = FluidKey.of(prototype);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            if (execute) log(owner, key.fluidId().toString(), requested, 0, "server-unavailable");
            return 0;
        }
        EmcFluidCacheData cache = EmcFluidCacheData.get(server);
        long cached = cache.getAmount(owner, key);
        long bucketPrice = prices(owner, account.get()).getOrDefault(key, 0L);
        if (bucketPrice <= 0 && cached <= 0) {
            if (execute) log(owner, key.fluidId().toString(), requested, 0, "fluid-not-learned-or-valued");
            return 0;
        }
        BigInteger balance = account.get().getEmc();
        long possible = FluidBucketMath.availableAmount(balance, cached, bucketPrice, Long.MAX_VALUE);
        long amount = Math.min(requested, possible);
        if (amount <= 0 || !execute) {
            if (execute) log(owner, key.fluidId().toString(), requested, 0, "insufficient-emc-and-empty-cache");
            return amount;
        }

        var writable = ProjectEAccountService.getWritableAccount(owner);
        if (writable.isEmpty()) {
            log(owner, key.fluidId().toString(), requested, 0, "owner-offline-or-account-not-writable");
            return 0;
        }
        IKnowledgeProvider knowledge = writable.get().knowledge();
        // Execute-time catalog reconstruction prevents stale display knowledge from authorizing fluid.
        long livePrice = findBucketPrice(knowledge, key);
        BigInteger liveBalance = knowledge.getEmc();
        cached = cache.getAmount(owner, key);
        if (livePrice <= 0 && cached <= 0) {
            log(owner, key.fluidId().toString(), requested, 0, "fluid-knowledge-changed-before-execution");
            return 0;
        }
        possible = FluidBucketMath.availableAmount(liveBalance, cached, livePrice, Long.MAX_VALUE);
        amount = Math.min(requested, possible);
        if (amount <= 0) {
            log(owner, key.fluidId().toString(), requested, 0, "emc-spent-before-execution");
            return 0;
        }

        long toBuy = Math.max(0, amount - cached);
        if (toBuy > 0 && livePrice <= 0) {
            log(owner, key.fluidId().toString(), requested, 0, "fluid-knowledge-forgotten-before-cache-refill");
            return 0;
        }
        long buckets = FluidBucketMath.bucketsToPurchase(toBuy);
        BigInteger cost = FluidBucketMath.cost(livePrice, buckets);
        if (liveBalance.compareTo(cost) < 0) {
            log(owner, key.fluidId().toString(), requested, 0, "emc-insufficient-for-whole-bucket");
            return 0;
        }

        // Main-thread transaction: debit first, then persist the prepaid fluid remainder.
        if (buckets > 0) {
            knowledge.setEmc(liveBalance.subtract(cost));
            knowledge.syncEmc(writable.get().player());
        }
        long newCache = FluidBucketMath.remainingCache(cached, amount, buckets);
        cache.setAmount(owner, key, newCache);
        com.nzothr.emcstoragebridge.core.EmcDisplayCache.stateChanged(owner);
        if (EmcStorageBridgeConfig.LOG_TRANSACTIONS.get()) {
            LOGGER.info("[EMCStorageBridge] fluid-extract owner={} fluid={} requested={} extracted={} bucketsPurchased={} emcCost={} remainingCached={} reason=accepted",
                    owner, key.fluidId(), requested, amount, buckets, cost, newCache);
        }
        return amount;
    }

    private static void log(UUID owner, String fluid, long requested, long accepted, String reason) {
        if (EmcStorageBridgeConfig.LOG_TRANSACTIONS.get()) {
            LOGGER.info("[EMCStorageBridge] fluid-extract owner={} fluid={} requested={} extracted={} reason={}",
                    owner, fluid, requested, accepted, reason);
        }
    }

    public static synchronized void knowledgeChanged(UUID owner) {
        if (owner == null) PRICES.clear();
        else PRICES.remove(owner);
    }

    public static synchronized void clear() {
        PRICES.clear();
    }

    public static synchronized boolean isEmpty(UUID owner) {
        return getAvailableFluids(owner).isEmpty();
    }

    private static Map<FluidKey, Long> prices(UUID owner, IKnowledgeProvider provider) {
        return PRICES.computeIfAbsent(owner, ignored -> buildPrices(provider.getKnowledge(), provider.hasFullKnowledge()));
    }

    private static Map<FluidKey, Long> buildPrices(Collection<ItemInfo> learned, boolean fullKnowledge) {
        Map<FluidKey, Long> result = new HashMap<>();
        for (ItemInfo info : learned) addBucketPrice(result, info);
        if (fullKnowledge) {
            for (ItemInfo info : EMCMappingHandler.getMappedItems()) addBucketPrice(result, info);
        }
        return result;
    }

    private static long findBucketPrice(IKnowledgeProvider provider, FluidKey wanted) {
        long best = Long.MAX_VALUE;
        for (ItemInfo info : provider.getKnowledge()) best = Math.min(best, bucketPrice(info, wanted));
        if (provider.hasFullKnowledge()) {
            for (ItemInfo info : EMCMappingHandler.getMappedItems()) best = Math.min(best, bucketPrice(info, wanted));
        }
        return best == Long.MAX_VALUE ? 0 : best;
    }

    private static void addBucketPrice(Map<FluidKey, Long> prices, ItemInfo info) {
        ItemStack stack = info.createStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof BucketItem bucket)) return;
        Fluid fluid = bucket.getFluid();
        if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) return;
        long value = ProjectEValueCache.getValue(info);
        if (value <= 0) return;
        FluidKey key = new FluidKey(BuiltInRegistries.FLUID.getKey(fluid), null);
        prices.merge(key, value, Math::min);
    }

    private static long bucketPrice(ItemInfo info, FluidKey wanted) {
        ItemStack stack = info.createStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof BucketItem bucket)
                || bucket.getFluid() == net.minecraft.world.level.material.Fluids.EMPTY) return Long.MAX_VALUE;
        FluidKey key = new FluidKey(BuiltInRegistries.FLUID.getKey(bucket.getFluid()), null);
        if (!key.equals(wanted)) return Long.MAX_VALUE;
        long value = ProjectEValueCache.getValue(info);
        return value > 0 ? value : Long.MAX_VALUE;
    }
}
