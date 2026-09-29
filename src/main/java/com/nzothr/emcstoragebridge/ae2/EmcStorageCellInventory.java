package com.nzothr.emcstoragebridge.ae2;

import java.util.UUID;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import com.nzothr.emcstoragebridge.core.EmcTransactionCore;
import com.nzothr.emcstoragebridge.core.NbtPolicy;
import com.nzothr.emcstoragebridge.core.ProjectEValueCache;
import com.nzothr.emcstoragebridge.item.EmcStorageCellItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import moze_intel.projecte.api.ItemInfo;

public final class EmcStorageCellInventory implements StorageCell {
    private final ItemStack stack;
    private final ISaveProvider saveProvider;
    private final UUID owner;
    private final EmcEntryRegistry.Entry entry;

    EmcStorageCellInventory(ItemStack stack, ISaveProvider saveProvider) {
        this.stack = stack;
        this.saveProvider = saveProvider;
        this.owner = EmcStorageCellItem.getOwner(stack);
        this.entry = EmcEntryRegistry.register(stack, saveProvider, owner);
    }

    @Override
    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (!isActive() || owner == null || !(key instanceof AEItemKey itemKey)) return 0;
        ItemStack input = itemKey.toStack();
        long accepted = EmcTransactionCore.insert(owner, input, amount, policy(), mode == Actionable.MODULATE);
        if (mode == Actionable.MODULATE && accepted > 0) {
            EmcDisplayCache.knownItemAdded(owner, ItemInfo.fromStack(input));
        }
        return accepted;
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (!isActive() || owner == null || !(key instanceof AEItemKey itemKey)) return 0;
        ItemStack output = itemKey.toStack();
        long extracted = EmcTransactionCore.extract(owner, output, amount, policy(), mode == Actionable.MODULATE);
        if (mode == Actionable.MODULATE && extracted > 0) {
            EmcDisplayCache.refreshKey(owner, ItemInfo.fromStack(output));
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (!isActive() || owner == null) return;
        EmcDisplayCache.addAvailable(owner, out, policy() == NbtPolicy.ALLOW);
    }

    @Override
    public Component getDescription() {
        return Component.translatable("item.emcstoragebridge.emc_storage_cell");
    }

    @Override
    public CellState getStatus() {
        if (owner == null || !isActive()) return CellState.EMPTY;
        return EmcDisplayCache.isEmpty(owner) ? CellState.EMPTY : CellState.NOT_EMPTY;
    }

    @Override
    public double getIdleDrain() {
        return 0;
    }

    @Override
    public boolean canFitInsideCell() {
        return false;
    }

    @Override
    public void persist() {
        if (saveProvider != null) saveProvider.saveChanges();
    }

    private boolean isActive() {
        return owner != null && EmcStorageCellItem.getOwner(stack) != null && EmcEntryRegistry.isActive(entry);
    }

    private NbtPolicy policy() {
        return EmcStorageCellItem.getNbtPolicy(stack);
    }
}
