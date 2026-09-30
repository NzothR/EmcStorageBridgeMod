package com.nzothr.emcstoragebridge.rs;

import com.mojang.logging.LogUtils;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.apiimpl.network.node.ExternalStorageNetworkNode;
import com.nzothr.emcstoragebridge.config.EmcStorageBridgeConfig;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Keeps one active EMC fluid view per owner and RS network. */
final class RsEmcFluidEntryRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<INetwork, List<Entry>> ENTRIES = new WeakHashMap<>();
    private static long nextOrder;

    private RsEmcFluidEntryRegistry() {}

    static synchronized Entry register(RsEmcFluidExternalStorage storage, INetwork network) {
        Entry current = storage.getEntry();
        if (current != null && current.network.get() == network
                && java.util.Objects.equals(current.owner, storage.owner())) return current;
        if (current != null) remove(current);
        Entry entry = new Entry(network, storage, storage.owner(), nextOrder++);
        ENTRIES.computeIfAbsent(network, ignored -> new ArrayList<>()).add(entry);
        storage.setEntry(entry);
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
            if (java.util.Objects.equals(candidate.owner, entry.owner) && candidate.order < entry.order) {
                if (!entry.duplicateLogged && EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
                    LOGGER.info("[EMCStorageBridge] Disabled duplicate RS EMC fluid interface owner={} network={}",
                            entry.owner, Integer.toHexString(System.identityHashCode(network)));
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
        private final WeakReference<RsEmcFluidExternalStorage> storage;
        private final java.util.UUID owner;
        private final long order;
        private boolean duplicateLogged;

        private Entry(INetwork network, RsEmcFluidExternalStorage storage, java.util.UUID owner, long order) {
            this.network = new WeakReference<>(network);
            this.storage = new WeakReference<>(storage);
            this.owner = owner;
            this.order = order;
        }

        private boolean isPresent() {
            RsEmcFluidExternalStorage current = storage.get();
            return current != null && current.isPresent();
        }
    }
}
