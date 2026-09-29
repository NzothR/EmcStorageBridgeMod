package com.nzothr.emcstoragebridge.ae2;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import appeng.api.networking.IGrid;
import appeng.blockentity.storage.DriveBlockEntity;
import com.mojang.logging.LogUtils;
import com.nzothr.emcstoragebridge.config.EmcStorageBridgeConfig;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/** Tracks live EMC cells by AE2 grid. Registration order selects the active cell. */
public final class EmcEntryRegistry {
    private static final Map<IGrid, List<Entry>> ENTRIES = new WeakHashMap<>();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static long nextOrder;

    private EmcEntryRegistry() {
    }

    public static synchronized Entry register(ItemStack stack, Object host, UUID owner) {
        DriveBlockEntity drive = resolveDrive(host);
        if (owner == null) {
            return new Entry(null, null, stack, owner, nextOrder++);
        }
        if (drive == null) {
            if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
                LOGGER.warn("[EMCStorageBridge] Could not resolve the AE2 Drive from save provider {}; duplicate suppression is unavailable for owner {}",
                        host == null ? "null" : host.getClass().getName(), owner);
            }
            return new Entry(null, null, stack, owner, nextOrder++);
        }
        int slot = findSlot(drive, stack, -1);
        IGrid grid = drive.getMainNode().getGrid();
        if (grid == null || slot < 0) {
            if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
                LOGGER.info("[EMCStorageBridge] Deferred grid registration owner={} drive={} slot={} gridPresent={}", owner,
                        drive.getBlockPos(), slot, grid != null);
            }
            return new Entry(null, drive, stack, owner, nextOrder++);
        }

        List<Entry> entries = ENTRIES.computeIfAbsent(grid, ignored -> new ArrayList<>());
        for (Entry entry : entries) {
            if (entry.owner.equals(owner) && entry.drive.get() == drive && entry.slot == slot && entry.isPresent()) {
                return entry;
            }
        }
        Entry entry = new Entry(grid, drive, stack, owner, nextOrder++);
        entry.slot = slot;
        entries.add(entry);
        if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
            LOGGER.info("[EMCStorageBridge] Registered EMC cell owner={} drive={} slot={} grid={} provider={}", owner,
                    drive.getBlockPos(), slot, Integer.toHexString(System.identityHashCode(grid)),
                    host == null ? "null" : host.getClass().getName());
        }
        return entry;
    }

    private static DriveBlockEntity resolveDrive(Object host) {
        if (host instanceof DriveBlockEntity drive) return drive;
        if (host == null) return null;
        // AE2 1.20.1 passes a method-reference save callback that captures its Drive.
        for (Field field : host.getClass().getDeclaredFields()) {
            if (!DriveBlockEntity.class.isAssignableFrom(field.getType())) continue;
            try {
                field.setAccessible(true);
                Object captured = field.get(host);
                if (captured instanceof DriveBlockEntity drive) return drive;
            } catch (ReflectiveOperationException | RuntimeException ex) {
                if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
                    LOGGER.warn("[EMCStorageBridge] Failed to read captured Drive from AE2 save callback {}",
                            host.getClass().getName(), ex);
                }
            }
        }
        return null;
    }

    public static synchronized boolean isActive(Entry entry) {
        if (entry == null) return false;
        DriveBlockEntity drive = entry.drive.get();
        IGrid actualGrid = drive == null ? null : drive.getMainNode().getGrid();
        IGrid grid = entry.grid.get();
        if (actualGrid != grid) {
            List<Entry> oldEntries = grid == null ? null : ENTRIES.get(grid);
            if (oldEntries != null) oldEntries.remove(entry);
            entry.grid = new WeakReference<>(actualGrid);
            if (drive != null && actualGrid != null) {
                ItemStack stack = entry.stack.get();
                entry.slot = stack == null ? -1 : findSlot(drive, stack, -1);
                List<Entry> newEntries = ENTRIES.computeIfAbsent(actualGrid, ignored -> new ArrayList<>());
                if (!newEntries.contains(entry)) newEntries.add(entry);
            }
            grid = actualGrid;
        }
        if (grid == null) return entry.isPresent();
        List<Entry> entries = ENTRIES.get(grid);
        if (entries == null) return entry.isPresent();
        entries.removeIf(candidate -> !candidate.isPresent());
        if (!entry.isPresent()) return false;
        for (Entry candidate : entries) {
            if (candidate.owner.equals(entry.owner) && candidate.order < entry.order && candidate.isPresent()) {
                if (!entry.duplicateLogged && EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
                    LOGGER.info("[EMCStorageBridge] Disabled duplicate EMC cell owner={} grid={}", entry.owner,
                            Integer.toHexString(System.identityHashCode(grid)));
                    entry.duplicateLogged = true;
                }
                return false;
            }
        }
        return true;
    }

    public static synchronized void refreshOwnerNetworks(UUID owner) {
        for (Map.Entry<IGrid, List<Entry>> gridEntries : ENTRIES.entrySet()) {
            if (gridEntries.getValue().stream().anyMatch(entry -> entry.owner.equals(owner) && entry.isPresent())) {
                gridEntries.getKey().getStorageService().invalidateCache();
            }
        }
    }

    private static int findSlot(DriveBlockEntity drive, ItemStack stack, int excludedSlot) {
        var inventory = drive.getInternalInventory();
        for (int i = 0; i < inventory.size(); i++) {
            if (i != excludedSlot && inventory.getStackInSlot(i) == stack) return i;
        }
        for (int i = 0; i < inventory.size(); i++) {
            if (i != excludedSlot && ItemStack.isSameItemSameTags(inventory.getStackInSlot(i), stack)) return i;
        }
        return -1;
    }

    public static final class Entry {
        private WeakReference<IGrid> grid;
        private final WeakReference<DriveBlockEntity> drive;
        private final WeakReference<ItemStack> stack;
        private final UUID owner;
        private final long order;
        private int slot = -1;
        private boolean duplicateLogged;

        private Entry(IGrid grid, DriveBlockEntity drive, ItemStack stack, UUID owner, long order) {
            this.grid = new WeakReference<>(grid);
            this.drive = new WeakReference<>(drive);
            this.stack = new WeakReference<>(stack);
            this.owner = owner;
            this.order = order;
        }

        private boolean isPresent() {
            ItemStack cell = stack.get();
            DriveBlockEntity host = drive.get();
            if (cell == null || cell.isEmpty()) return false;
            if (host == null || slot < 0) return true;
            return slot < host.getInternalInventory().size() && host.getInternalInventory().getStackInSlot(slot) == cell;
        }
    }
}
