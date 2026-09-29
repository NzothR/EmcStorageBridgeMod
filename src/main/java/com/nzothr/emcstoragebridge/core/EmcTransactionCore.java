package com.nzothr.emcstoragebridge.core;

import java.math.BigInteger;
import java.util.UUID;

import appeng.api.stacks.AEKey;
import com.mojang.logging.LogUtils;
import com.nzothr.emcstoragebridge.config.EmcStorageBridgeConfig;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.event.PlayerAttemptLearnEvent;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;

public final class EmcTransactionCore {
    private static final Logger LOGGER = LogUtils.getLogger();

    private EmcTransactionCore() {
    }

    public static long insert(UUID owner, ItemStack stack, long requested, NbtPolicy nbtPolicy, boolean execute) {
        if (stack.isEmpty() || requested <= 0) {
            log("insert", owner, stack, requested, execute, 0, "empty-stack-or-non-positive-request");
            return 0;
        }
        if (nbtPolicy == NbtPolicy.REJECT && stack.hasTag()) {
            log("insert", owner, stack, requested, execute, 0, "NBT-rejected-by-cell-policy");
            return 0;
        }

        var account = ProjectEAccountService.getWritableAccount(owner);
        if (account.isEmpty()) {
            log("insert", owner, stack, requested, execute, 0, "account-not-online-or-not-server-thread");
            return 0;
        }

        long sellValue = ProjectEValueCache.getSellValue(stack);
        if (sellValue <= 0) {
            log("insert", owner, stack, requested, execute, 0, "ProjectE-sell-value-is-zero");
            return 0;
        }

        var provider = account.get().knowledge();
        boolean alreadyKnown = provider.hasKnowledge(stack);
        if (!execute) {
            return alreadyKnown || sellValue > 0 ? requested : 0;
        }

        if (!alreadyKnown && !learn(account.get().player(), provider, stack)) {
            log("insert", owner, stack, requested, true, 0, "ProjectE-learning-was-rejected");
            return 0;
        }

        BigInteger gain = EmcMath.gain(requested, sellValue);
        provider.setEmc(provider.getEmc().add(gain));
        provider.syncEmc(account.get().player());
        log("insert", owner, stack, requested, true, requested, alreadyKnown ? "accepted-known-item" : "learned-and-accepted");
        return requested;
    }

    public static long extract(UUID owner, ItemStack stack, long requested, NbtPolicy nbtPolicy, boolean execute) {
        if (stack.isEmpty() || requested <= 0) {
            log("extract", owner, stack, requested, execute, 0, "empty-stack-or-non-positive-request");
            return 0;
        }
        if (nbtPolicy == NbtPolicy.REJECT && stack.hasTag()) {
            log("extract", owner, stack, requested, execute, 0, "NBT-rejected-by-cell-policy");
            return 0;
        }

        var account = ProjectEAccountService.getReadableAccount(owner);
        if (account.isEmpty()) {
            log("extract", owner, stack, requested, execute, 0, "ProjectE-account-unavailable-on-server-thread");
            return 0;
        }
        if (!account.get().hasKnowledge(stack)) {
            log("extract", owner, stack, requested, execute, 0, "item-not-in-ProjectE-Knowledge");
            return 0;
        }

        long emcPerItem = ProjectEValueCache.getValue(ItemInfo.fromStack(stack));
        if (emcPerItem <= 0) {
            log("extract", owner, stack, requested, execute, 0, "ProjectE-EMC-value-is-zero");
            return 0;
        }

        BigInteger balance = account.get().getEmc();
        long amount = EmcMath.maxExtractable(balance, emcPerItem, requested);
        if (amount <= 0) {
            log("extract", owner, stack, requested, execute, 0, "insufficient-EMC");
            return 0;
        }
        if (!execute) {
            return amount;
        }

        var writable = ProjectEAccountService.getWritableAccount(owner);
        if (writable.isEmpty()) {
            log("extract", owner, stack, requested, true, 0, "owner-offline-or-not-server-thread");
            return 0;
        }

        // Re-read live state at execute time; another network may have spent EMC since simulation.
        var provider = writable.get().knowledge();
        if (!provider.hasKnowledge(stack)) {
            log("extract", owner, stack, requested, true, 0, "Knowledge-changed-before-execution");
            return 0;
        }
        amount = EmcMath.maxExtractable(provider.getEmc(), emcPerItem, requested);
        if (amount <= 0) {
            log("extract", owner, stack, requested, true, 0, "EMC-spent-before-execution");
            return 0;
        }

        provider.setEmc(provider.getEmc().subtract(EmcMath.cost(amount, emcPerItem)));
        provider.syncEmc(writable.get().player());
        log("extract", owner, stack, requested, true, amount, "accepted");
        return amount;
    }

    public static void logRejected(String operation, UUID owner, AEKey key, long requested, boolean execute, String reason) {
        if (execute) log(operation, owner, key == null ? ItemStack.EMPTY : key.wrapForDisplayOrFilter(), requested, true, 0, reason);
    }

    private static void log(String operation, UUID owner, ItemStack stack, long requested, boolean execute,
            long accepted, String reason) {
        if (execute && EmcStorageBridgeConfig.LOG_TRANSACTIONS.get()) {
            LOGGER.info("[EMCStorageBridge] {} owner={} item={} requested={} accepted={} reason={}", operation,
                    owner, stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()),
                    requested, accepted, reason);
        }
    }

    private static boolean learn(net.minecraft.server.level.ServerPlayer player,
            moze_intel.projecte.api.capabilities.IKnowledgeProvider provider, ItemStack stack) {
        ItemInfo source = ItemInfo.fromStack(stack);
        ItemInfo persistent = IEMCProxy.INSTANCE.getPersistentInfo(source);
        PlayerAttemptLearnEvent event = new PlayerAttemptLearnEvent(player, source, persistent);
        if (MinecraftForge.EVENT_BUS.post(event)) {
            return false;
        }

        ItemInfo learnedInfo = event.getReducedInfo();
        boolean learned = provider.addKnowledge(learnedInfo);
        if (learned) {
            provider.syncKnowledgeChange(player, learnedInfo, true);
        }
        return learned || provider.hasKnowledge(stack);
    }
}
