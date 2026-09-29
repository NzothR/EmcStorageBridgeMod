package com.nzothr.emcstoragebridge.rs;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.refinedmods.refinedstorage.api.network.INetwork;
import com.mojang.logging.LogUtils;
import com.nzothr.emcstoragebridge.config.EmcStorageBridgeConfig;
import org.slf4j.Logger;

/** Suppresses duplicate access to one owner's EMC account on the same RS network. */
final class RsEmcEntryRegistry {
    private static final Map<INetwork, List<Entry>> ENTRIES = new WeakHashMap<>();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static long nextOrder;

    private RsEmcEntryRegistry() {
    }

    static synchronized Entry register(RsEmcExternalStorage storage, INetwork network) {
        Entry current = storage.getEntry();
        if (current != null && current.network.get() == network) return current;
        if (current != null) remove(current);

        Entry entry = new Entry(network, storage, storage.owner(), nextOrder++);
        ENTRIES.computeIfAbsent(network, ignored -> new ArrayList<>()).add(entry);
        storage.setEntry(entry);
        if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
            LOGGER.info("[EMCStorageBridge] Registered RS EMC Interface owner={} network={} order={}", entry.owner,
                    Integer.toHexString(System.identityHashCode(network)), entry.order);
        }
        return entry;
    }

    static synchronized boolean isActive(Entry entry) {
        if (entry == null || entry.owner == null || !entry.isPresent()) return false;
        INetwork network = entry.network.get();
        if (network == null) return false;
        List<Entry> entries = ENTRIES.get(network);
        if (entries == null) return true;
        entries.removeIf(candidate -> !candidate.isPresent());
        for (Entry candidate : entries) {
            if (candidate.owner.equals(entry.owner) && candidate.order < entry.order) {
                if (!entry.duplicateLogged && EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
                    LOGGER.info("[EMCStorageBridge] Disabled duplicate RS EMC Interface owner={} network={}", entry.owner,
                            Integer.toHexString(System.identityHashCode(network)));
                    entry.duplicateLogged = true;
                }
                return false;
            }
        }
        return true;
    }

    private static void remove(Entry entry) {
        INetwork network = entry.network.get();
        List<Entry> entries = network == null ? null : ENTRIES.get(network);
        if (entries != null) entries.remove(entry);
    }

    static final class Entry {
        private final WeakReference<INetwork> network;
        private final WeakReference<RsEmcExternalStorage> storage;
        private final java.util.UUID owner;
        private final long order;
        private boolean duplicateLogged;

        private Entry(INetwork network, RsEmcExternalStorage storage, java.util.UUID owner, long order) {
            this.network = new WeakReference<>(network);
            this.storage = new WeakReference<>(storage);
            this.owner = owner;
            this.order = order;
        }

        private boolean isPresent() {
            RsEmcExternalStorage externalStorage = storage.get();
            return externalStorage != null && externalStorage.isPresent();
        }
    }
}
