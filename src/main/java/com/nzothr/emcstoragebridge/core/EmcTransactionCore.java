package com.nzothr.emcstoragebridge.core;

import java.math.BigInteger;
import java.util.UUID;

import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.event.PlayerAttemptLearnEvent;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

public final class EmcTransactionCore {
    private EmcTransactionCore() {
    }

    public static long insert(UUID owner, ItemStack stack, long requested, NbtPolicy nbtPolicy, boolean execute) {
        if (stack.isEmpty() || requested <= 0 || (nbtPolicy == NbtPolicy.REJECT && stack.hasTag())) {
            return 0;
        }

        var account = ProjectEAccountService.getWritableAccount(owner);
        if (account.isEmpty()) {
            return 0;
        }

        long sellValue = ProjectEValueCache.getSellValue(stack);
        if (sellValue <= 0) {
            return 0;
        }

        var provider = account.get().knowledge();
        boolean alreadyKnown = provider.hasKnowledge(stack);
        if (!execute) {
            return alreadyKnown || sellValue > 0 ? requested : 0;
        }

        if (!alreadyKnown && !learn(account.get().player(), provider, stack)) {
            return 0;
        }

        BigInteger gain = EmcMath.gain(requested, sellValue);
        provider.setEmc(provider.getEmc().add(gain));
        provider.syncEmc(account.get().player());
        return requested;
    }

    public static long extract(UUID owner, ItemStack stack, long requested, NbtPolicy nbtPolicy, boolean execute) {
        if (stack.isEmpty() || requested <= 0 || (nbtPolicy == NbtPolicy.REJECT && stack.hasTag())) {
            return 0;
        }

        var account = ProjectEAccountService.getReadableAccount(owner);
        if (account.isEmpty() || !account.get().hasKnowledge(stack)) {
            return 0;
        }

        long emcPerItem = ProjectEValueCache.getValue(ItemInfo.fromStack(stack));
        if (emcPerItem <= 0) {
            return 0;
        }

        BigInteger balance = account.get().getEmc();
        long amount = EmcMath.maxExtractable(balance, emcPerItem, requested);
        if (amount <= 0 || !execute) {
            return amount;
        }

        var writable = ProjectEAccountService.getWritableAccount(owner);
        if (writable.isEmpty()) {
            return 0;
        }

        // Re-read live state at execute time; another network may have spent EMC since simulation.
        var provider = writable.get().knowledge();
        if (!provider.hasKnowledge(stack)) {
            return 0;
        }
        amount = EmcMath.maxExtractable(provider.getEmc(), emcPerItem, requested);
        if (amount <= 0) {
            return 0;
        }

        provider.setEmc(provider.getEmc().subtract(EmcMath.cost(amount, emcPerItem)));
        provider.syncEmc(writable.get().player());
        return amount;
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
