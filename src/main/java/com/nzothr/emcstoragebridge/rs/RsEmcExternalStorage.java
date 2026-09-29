package com.nzothr.emcstoragebridge.rs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.logging.LogUtils;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.storage.AccessType;
import com.refinedmods.refinedstorage.api.storage.cache.IStorageCache;
import com.refinedmods.refinedstorage.api.storage.externalstorage.IExternalStorage;
import com.refinedmods.refinedstorage.api.storage.externalstorage.IExternalStorageContext;
import com.refinedmods.refinedstorage.apiimpl.network.node.ExternalStorageNetworkNode;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.api.util.IComparer;
import com.nzothr.emcstoragebridge.config.EmcStorageBridgeConfig;
import com.nzothr.emcstoragebridge.core.EmcDisplayCache;
import com.nzothr.emcstoragebridge.core.EmcTransactionCore;
import com.nzothr.emcstoragebridge.core.NbtPolicy;
import moze_intel.projecte.api.ItemInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/** Refined Storage view of one owner's ProjectE account. */
public final class RsEmcExternalStorage implements IExternalStorage<ItemStack> {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final IExternalStorageContext context;
    private final EmcInterfaceBlockEntity blockEntity;
    private Map<CompoundTag, ItemStack> reportedStacks;
    private INetwork reportedNetwork;
    private long reportedRevision = Long.MIN_VALUE;
    private long deferCacheSyncThroughTick = Long.MIN_VALUE;
    private boolean reportedActive;
    private RsEmcEntryRegistry.Entry entry;

    RsEmcExternalStorage(IExternalStorageContext context, EmcInterfaceBlockEntity blockEntity) {
        this.context = context;
        this.blockEntity = blockEntity;
    }

    @Override
    public void update(INetwork network) {
        if (network == null || blockEntity.getLevel() == null || blockEntity.getLevel().isClientSide) return;
        boolean wasActive = reportedActive;
        entry = RsEmcEntryRegistry.register(this, network);
        boolean active = owner() != null && RsEmcEntryRegistry.isActive(entry);
        long revision = EmcDisplayCache.revision(owner());
        boolean networkChanged = reportedNetwork != network;
        if (networkChanged) {
            reportedNetwork = network;
            reportedStacks = null;
        }
        long gameTime = blockEntity.getLevel().getGameTime();
        if (!networkChanged && gameTime <= deferCacheSyncThroughTick) return;
        if (gameTime > deferCacheSyncThroughTick) deferCacheSyncThroughTick = Long.MIN_VALUE;
        if (reportedStacks != null && revision == reportedRevision && active == wasActive) return;

        int previousCount = reportedStacks == null ? -1 : reportedStacks.size();
        Map<CompoundTag, ItemStack> current = active ? snapshot() : Map.of();
        IStorageCache<ItemStack> cache = network.getItemStorageCache();
        if (reportedStacks == null) {
            // RS has already populated this cache from getStacks() during its invalidation.
            reportedStacks = current;
        } else {
            applyDifference(cache, reportedStacks, current);
            reportedStacks = current;
        }
        reportedRevision = revision;
        reportedActive = active;
        if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
            LOGGER.info("[EMCStorageBridge] RS external storage synchronized owner={} active={} items={} revision={} previousItems={} access={} network={}",
                    owner(), active, current.size(), revision, previousCount, context.getAccessType(),
                    Integer.toHexString(System.identityHashCode(network)));
        }
    }

    @Override
    public long getCapacity() {
        return Long.MAX_VALUE;
    }

    @Override
    public Collection<ItemStack> getStacks() {
        Map<CompoundTag, ItemStack> stacks = owner() != null && isActive()
                ? snapshot() : Map.of();
        reportedStacks = stacks;
        reportedRevision = EmcDisplayCache.revision(owner());
        reportedActive = !stacks.isEmpty() || (owner() != null && isActive());
        List<ItemStack> result = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks.values()) result.add(stack.copy());
        if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
            LOGGER.info("[EMCStorageBridge] RS requested EMC stacks owner={} active={} returned={} revision={} NBT={} access={} pos={}",
                    owner(), isActive(), result.size(), reportedRevision, blockEntity.getNbtPolicy(), context.getAccessType(),
                    blockEntity.getBlockPos());
        }
        return result;
    }

    @Override
    public ItemStack insert(ItemStack prototype, int size, Action action) {
        if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
            LOGGER.info("[EMCStorageBridge] RS insert request owner={} item={} count={} action={} access={} active={} acceptedByContext={}",
                    owner(), prototype, size, action, context.getAccessType(), isActive(), context.acceptsItem(prototype));
        }
        if (prototype.isEmpty() || size <= 0 || !canInsert(prototype)) return remainder(prototype, size);
        ItemStack item = prototype.copy();
        item.setCount(1);
        long accepted = EmcTransactionCore.insert(owner(), item, size, blockEntity.getNbtPolicy(), action == Action.PERFORM);
        int remaining = (int) Math.max(0, size - accepted);
        if (action == Action.PERFORM && accepted > 0) {
            EmcDisplayCache.knownItemAdded(owner(), ItemInfo.fromStack(EmcTransactionCore.withoutNbt(item)));
        }
        return remaining == 0 ? ItemStack.EMPTY : remainder(prototype, remaining);
    }

    @Override
    public ItemStack extract(ItemStack prototype, int size, int flags, Action action) {
        boolean active = isActive();
        boolean acceptedByContext = context.acceptsItem(prototype);
        if (prototype.isEmpty() || size <= 0 || !canExtract(prototype)) {
            if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
                LOGGER.info("[EMCStorageBridge] RS extract rejected before EMC lookup owner={} item={} count={} action={} active={} ownerBound={} access={} acceptedByContext={} storagePresent={}",
                        owner(), prototype, size, action, active, owner() != null, context.getAccessType(),
                        acceptedByContext, isPresent());
            }
            return ItemStack.EMPTY;
        }
        ItemStack item = prototype.copy();
        item.setCount(1);
        long simulated = EmcTransactionCore.extract(owner(), item, size, false);
        boolean strictQuantity = (flags & IComparer.COMPARE_QUANTITY) == IComparer.COMPARE_QUANTITY;
        if (simulated <= 0 || (strictQuantity && simulated < size)) {
            if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
                LOGGER.info("[EMCStorageBridge] RS extract has insufficient EMC owner={} item={} requested={} simulated={} strictQuantity={} action={} NBT={}",
                        owner(), prototype, size, simulated, strictQuantity, action, blockEntity.getNbtPolicy());
            }
            return ItemStack.EMPTY;
        }
        if (action == Action.SIMULATE) {
            ItemStack result = withCount(prototype, (int) Math.min(simulated, size));
            if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
                LOGGER.info("[EMCStorageBridge] RS extract simulated owner={} item={} requested={} returned={}",
                        owner(), prototype, size, result.getCount());
            }
            return result;
        }

        long extracted = EmcTransactionCore.extract(owner(), item, Math.min(simulated, size), true);
        if (extracted > 0) {
            EmcDisplayCache.refreshKey(owner(), ItemInfo.fromStack(item));
            deferCacheSyncThroughTick = blockEntity.getLevel().getGameTime();
            if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
                LOGGER.info("[EMCStorageBridge] Deferred RS external storage cache diff until next tick after extraction owner={} item={} count={} tick={} network={}",
                        owner(), prototype, extracted, deferCacheSyncThroughTick,
                        Integer.toHexString(System.identityHashCode(reportedNetwork)));
            }
        }
        ItemStack result = extracted <= 0 ? ItemStack.EMPTY
                : withCount(prototype, (int) Math.min(extracted, Integer.MAX_VALUE));
        if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
            LOGGER.info("[EMCStorageBridge] RS extract performed owner={} item={} requested={} simulated={} extracted={} returned={}",
                    owner(), prototype, size, simulated, extracted, result.getCount());
        }
        return result;
    }

    @Override
    public int getStored() {
        long total = 0;
        if (reportedStacks != null) {
            for (ItemStack stack : reportedStacks.values()) total += stack.getCount();
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    @Override
    public int getPriority() {
        return context.getPriority();
    }

    @Override
    public AccessType getAccessType() {
        return context.getAccessType();
    }

    @Override
    public int getCacheDelta(int storedPreInsertion, int size, @Nullable ItemStack remainder) {
        return Math.max(0, size - (remainder == null ? 0 : remainder.getCount()));
    }

    UUID owner() {
        return blockEntity.getOwner();
    }

    RsEmcEntryRegistry.Entry getEntry() {
        return entry;
    }

    void setEntry(RsEmcEntryRegistry.Entry entry) {
        this.entry = entry;
    }

    boolean isPresent() {
        var level = blockEntity.getLevel();
        if (level == null || blockEntity.isRemoved() || !level.hasChunkAt(blockEntity.getBlockPos())
                || level.getBlockEntity(blockEntity.getBlockPos()) != blockEntity) {
            return false;
        }
        return context instanceof ExternalStorageNetworkNode node && node.getItemStorages().contains(this);
    }

    private boolean isActive() {
        return entry == null || RsEmcEntryRegistry.isActive(entry);
    }

    private boolean canInsert(ItemStack stack) {
        return owner() != null && isActive() && context.getAccessType() != AccessType.EXTRACT
                && context.acceptsItem(stack);
    }

    private boolean canExtract(ItemStack stack) {
        return owner() != null && isActive() && context.getAccessType() != AccessType.INSERT
                && context.acceptsItem(stack);
    }

    private Map<CompoundTag, ItemStack> snapshot() {
        Map<CompoundTag, ItemStack> stacks = new HashMap<>();
        for (ItemStack stack : EmcDisplayCache.getAvailableStacks(owner())) {
            if (stack.isEmpty() || !context.acceptsItem(stack)) continue;
            ItemStack copy = stack.copy();
            stacks.put(key(copy), copy);
        }
        return stacks;
    }

    private void applyDifference(IStorageCache<ItemStack> cache, Map<CompoundTag, ItemStack> before,
            Map<CompoundTag, ItemStack> after) {
        boolean changed = false;
        for (Map.Entry<CompoundTag, ItemStack> entry : before.entrySet()) {
            ItemStack oldStack = entry.getValue();
            ItemStack newStack = after.get(entry.getKey());
            int oldCount = oldStack.getCount();
            int newCount = newStack == null ? 0 : newStack.getCount();
            if (oldCount > newCount) {
                cache.remove(oldStack.copy(), oldCount - newCount, true);
                changed = true;
            }
        }
        for (Map.Entry<CompoundTag, ItemStack> entry : after.entrySet()) {
            ItemStack newStack = entry.getValue();
            ItemStack oldStack = before.get(entry.getKey());
            int oldCount = oldStack == null ? 0 : oldStack.getCount();
            if (newStack.getCount() > oldCount) {
                cache.add(newStack.copy(), newStack.getCount() - oldCount, false, true);
                changed = true;
            }
        }
        if (changed) cache.flush();
    }

    private static CompoundTag key(ItemStack stack) {
        CompoundTag tag = stack.save(new CompoundTag());
        tag.putByte("Count", (byte) 1);
        return tag;
    }

    private static ItemStack remainder(ItemStack prototype, int count) {
        if (prototype.isEmpty() || count <= 0) return ItemStack.EMPTY;
        return withCount(prototype, count);
    }

    private static ItemStack withCount(ItemStack prototype, int count) {
        ItemStack result = prototype.copy();
        result.setCount(count);
        return result;
    }
}
