package com.nzothr.emcstoragebridge.core;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;

/** Persisted prepaid fluid remainder. Values are shared by every network adapter for an owner. */
public final class EmcFluidCacheData extends SavedData {
    private static final String DATA_NAME = "emcstoragebridge_fluid_cache";
    private static final String OWNERS = "Owners";
    private static final int DATA_VERSION = 1;
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<UUID, Map<FluidKey, Long>> amounts = new HashMap<>();

    public static EmcFluidCacheData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                EmcFluidCacheData::load, EmcFluidCacheData::new, DATA_NAME);
    }

    public synchronized long getAmount(UUID owner, FluidKey key) {
        return amounts.getOrDefault(owner, Map.of()).getOrDefault(key, 0L);
    }

    public synchronized Set<FluidKey> getKeys(UUID owner) {
        return new HashSet<>(amounts.getOrDefault(owner, Map.of()).keySet());
    }

    public synchronized void setAmount(UUID owner, FluidKey key, long amount) {
        if (owner == null || amount < 0) throw new IllegalArgumentException("Invalid prepaid fluid amount");
        Map<FluidKey, Long> ownerAmounts = amounts.computeIfAbsent(owner, ignored -> new HashMap<>());
        if (amount == 0) ownerAmounts.remove(key);
        else ownerAmounts.put(key, amount);
        if (ownerAmounts.isEmpty()) amounts.remove(owner);
        setDirty();
    }

    public synchronized CompoundTag save(CompoundTag tag) {
        tag.putInt("Version", DATA_VERSION);
        ListTag ownerList = new ListTag();
        amounts.forEach((owner, fluids) -> {
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putUUID("Owner", owner);
            ListTag fluidList = new ListTag();
            fluids.forEach((key, amount) -> {
                if (amount <= 0 || key.resolveFluid() == null) return;
                CompoundTag fluidTag = new CompoundTag();
                fluidTag.putString("Fluid", key.fluidId().toString());
                fluidTag.put("Tag", key.tag());
                fluidTag.putLong("Amount", amount);
                fluidList.add(fluidTag);
            });
            ownerTag.put("Fluids", fluidList);
            ownerList.add(ownerTag);
        });
        tag.put(OWNERS, ownerList);
        return tag;
    }

    public static EmcFluidCacheData load(CompoundTag tag) {
        EmcFluidCacheData data = new EmcFluidCacheData();
        int version = tag.getInt("Version");
        if (version > DATA_VERSION) {
            LOGGER.error("[EMCStorageBridge] Fluid cache data version {} is newer than supported version {}; ignoring it",
                    version, DATA_VERSION);
            return data;
        }
        ListTag ownerList = tag.getList(OWNERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < ownerList.size(); i++) {
            CompoundTag ownerTag = ownerList.getCompound(i);
            if (!ownerTag.hasUUID("Owner")) continue;
            UUID owner = ownerTag.getUUID("Owner");
            ListTag fluidList = ownerTag.getList("Fluids", Tag.TAG_COMPOUND);
            for (int j = 0; j < fluidList.size(); j++) {
                CompoundTag fluidTag = fluidList.getCompound(j);
                ResourceLocation fluidId = ResourceLocation.tryParse(fluidTag.getString("Fluid"));
                long amount = fluidTag.getLong("Amount");
                if (fluidId == null || amount <= 0) continue;
                if (ForgeRegistries.FLUIDS.getValue(fluidId) == null) {
                    LOGGER.warn("[EMCStorageBridge] Ignoring cached fluid {} for owner {} because that fluid is not registered",
                            fluidId, owner);
                    continue;
                }
                FluidKey key = new FluidKey(fluidId, fluidTag.getCompound("Tag"));
                data.amounts.computeIfAbsent(owner, ignored -> new HashMap<>()).put(key, amount);
            }
        }
        return data;
    }
}
