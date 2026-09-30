package com.nzothr.emcstoragebridge.rs;

import com.mojang.logging.LogUtils;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.storage.AccessType;
import com.refinedmods.refinedstorage.api.storage.cache.IStorageCache;
import com.refinedmods.refinedstorage.api.storage.externalstorage.IExternalStorage;
import com.refinedmods.refinedstorage.api.storage.externalstorage.IExternalStorageContext;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.api.util.IComparer;
import com.refinedmods.refinedstorage.apiimpl.network.node.ExternalStorageNetworkNode;
import com.nzothr.emcstoragebridge.config.EmcStorageBridgeConfig;
import com.nzothr.emcstoragebridge.core.EmcDisplayCache;
import com.nzothr.emcstoragebridge.core.EmcFluidStorageCore;
import com.nzothr.emcstoragebridge.core.FluidKey;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Refined Storage fluid view of one owner's ProjectE account. */
public final class RsEmcFluidExternalStorage implements IExternalStorage<FluidStack> {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final IExternalStorageContext context;
    private final EmcInterfaceBlockEntity blockEntity;
    private Map<FluidKey, FluidStack> reportedFluids;
    private INetwork reportedNetwork;
    private long reportedRevision = Long.MIN_VALUE;
    private boolean reportedActive;
    private RsEmcFluidEntryRegistry.Entry entry;

    RsEmcFluidExternalStorage(IExternalStorageContext context, EmcInterfaceBlockEntity blockEntity) {
        this.context = context;
        this.blockEntity = blockEntity;
    }

    @Override
    public void update(INetwork network) {
        if (network == null || blockEntity.getLevel() == null || blockEntity.getLevel().isClientSide) return;
        boolean wasActive = reportedActive;
        entry = RsEmcFluidEntryRegistry.register(this, network);
        boolean active = owner() != null && RsEmcFluidEntryRegistry.isActive(entry);
        long revision = EmcDisplayCache.revision(owner());
        boolean networkChanged = reportedNetwork != network;
        if (networkChanged) {
            reportedNetwork = network;
            reportedFluids = null;
        }
        if (!networkChanged && reportedFluids != null && revision == reportedRevision && active == wasActive) return;

        Map<FluidKey, FluidStack> current = active ? snapshot() : Map.of();
        IStorageCache<FluidStack> cache = network.getFluidStorageCache();
        if (reportedFluids == null) reportedFluids = current;
        else {
            applyDifference(cache, reportedFluids, current);
            reportedFluids = current;
        }
        reportedRevision = revision;
        reportedActive = active;
        if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
            LOGGER.info("[EMCStorageBridge] RS fluid storage synchronized owner={} active={} fluids={} revision={} network={}",
                    owner(), active, current.size(), revision,
                    Integer.toHexString(System.identityHashCode(network)));
        }
    }

    @Override
    public long getCapacity() {
        return Long.MAX_VALUE;
    }

    @Override
    public Collection<FluidStack> getStacks() {
        Map<FluidKey, FluidStack> stacks = owner() != null && isActive() ? snapshot() : Map.of();
        reportedFluids = stacks;
        reportedRevision = EmcDisplayCache.revision(owner());
        reportedActive = owner() != null && isActive();
        List<FluidStack> result = new ArrayList<>(stacks.size());
        for (FluidStack stack : stacks.values()) result.add(stack.copy());
        return result;
    }

    @Override
    public FluidStack insert(FluidStack prototype, int size, Action action) {
        if (prototype == null || prototype.isEmpty() || size <= 0) return FluidStack.EMPTY;
        if (action == Action.PERFORM && EmcStorageBridgeConfig.LOG_TRANSACTIONS.get()) {
            LOGGER.info("[EMCStorageBridge] RS fluid insert rejected owner={} fluid={} amount={} reason=fluid-input-unsupported",
                    owner(), FluidKey.of(prototype).fluidId(), size);
        }
        return withAmount(prototype, size);
    }

    @Override
    public FluidStack extract(FluidStack prototype, int size, int flags, Action action) {
        if (prototype == null || prototype.isEmpty() || size <= 0 || !canExtract(prototype)) return FluidStack.EMPTY;
        FluidStack key = new FluidStack(prototype.getFluid(), 1, prototype.getTag());
        long simulated = EmcFluidStorageCore.extract(owner(), key, size, false);
        boolean strictQuantity = (flags & IComparer.COMPARE_QUANTITY) == IComparer.COMPARE_QUANTITY;
        if (simulated <= 0 || (strictQuantity && simulated < size)) return FluidStack.EMPTY;
        if (action == Action.SIMULATE) return withAmount(prototype, (int) Math.min(simulated, size));

        long extracted = EmcFluidStorageCore.extract(owner(), key, Math.min(simulated, size), true);
        if (extracted > 0) {
            reportedRevision = Long.MIN_VALUE;
        }
        return extracted <= 0 ? FluidStack.EMPTY : withAmount(prototype, (int) Math.min(extracted, Integer.MAX_VALUE));
    }

    @Override
    public int getStored() {
        long total = 0;
        if (reportedFluids != null) {
            for (FluidStack stack : reportedFluids.values()) total += stack.getAmount();
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    @Override
    public int getPriority() { return context.getPriority(); }

    @Override
    public AccessType getAccessType() { return context.getAccessType(); }

    @Override
    public int getCacheDelta(int storedPreInsertion, int size, @Nullable FluidStack remainder) {
        return 0;
    }

    UUID owner() { return blockEntity.getOwner(); }

    RsEmcFluidEntryRegistry.Entry getEntry() { return entry; }

    void setEntry(RsEmcFluidEntryRegistry.Entry entry) { this.entry = entry; }

    boolean isPresent() {
        var level = blockEntity.getLevel();
        if (level == null || blockEntity.isRemoved() || !level.hasChunkAt(blockEntity.getBlockPos())
                || level.getBlockEntity(blockEntity.getBlockPos()) != blockEntity) return false;
        return context instanceof ExternalStorageNetworkNode node && node.getFluidStorages().contains(this);
    }

    private boolean isActive() { return entry == null || RsEmcFluidEntryRegistry.isActive(entry); }

    private boolean canExtract(FluidStack stack) {
        return owner() != null && isActive() && context.getAccessType() != AccessType.INSERT
                && context.acceptsFluid(stack);
    }

    private Map<FluidKey, FluidStack> snapshot() {
        Map<FluidKey, FluidStack> stacks = new HashMap<>();
        for (FluidStack stack : EmcFluidStorageCore.getAvailableFluids(owner())) {
            if (!stack.isEmpty() && context.acceptsFluid(stack)) stacks.put(FluidKey.of(stack), stack.copy());
        }
        return stacks;
    }

    private void applyDifference(IStorageCache<FluidStack> cache, Map<FluidKey, FluidStack> before,
            Map<FluidKey, FluidStack> after) {
        boolean changed = false;
        for (Map.Entry<FluidKey, FluidStack> old : before.entrySet()) {
            FluidStack next = after.get(old.getKey());
            int oldAmount = old.getValue().getAmount();
            int newAmount = next == null ? 0 : next.getAmount();
            if (oldAmount > newAmount) {
                cache.remove(old.getValue().copy(), oldAmount - newAmount, true);
                changed = true;
            }
        }
        for (Map.Entry<FluidKey, FluidStack> next : after.entrySet()) {
            FluidStack old = before.get(next.getKey());
            int oldAmount = old == null ? 0 : old.getAmount();
            if (next.getValue().getAmount() > oldAmount) {
                cache.add(next.getValue().copy(), next.getValue().getAmount() - oldAmount, false, true);
                changed = true;
            }
        }
        if (changed) cache.flush();
    }

    private static FluidStack withAmount(FluidStack prototype, int amount) {
        FluidStack result = prototype.copy();
        result.setAmount(amount);
        return result;
    }
}
