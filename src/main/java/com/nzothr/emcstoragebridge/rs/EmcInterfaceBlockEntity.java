package com.nzothr.emcstoragebridge.rs;

import java.util.UUID;

import com.mojang.logging.LogUtils;
import com.nzothr.emcstoragebridge.core.NbtPolicy;
import com.nzothr.emcstoragebridge.config.EmcStorageBridgeConfig;
import com.refinedmods.refinedstorage.blockentity.ExternalStorageBlockEntity;
import com.refinedmods.refinedstorage.apiimpl.network.node.ExternalStorageNetworkNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public final class EmcInterfaceBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String OWNER_TAG = "Owner";
    private static final String NBT_POLICY_TAG = "NbtPolicy";

    private UUID owner;
    private NbtPolicy nbtPolicy = NbtPolicy.fromConfig(EmcStorageBridgeConfig.DEFAULT_NBT_POLICY.get());
    private boolean refreshExternalStorage = true;
    private int refreshDelayTicks;
    private int refreshAttempts;

    public EmcInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(EmcInterfaceBlocks.EMC_INTERFACE_ENTITY.get(), pos, state);
    }

    public UUID getOwner() {
        return owner;
    }

    public NbtPolicy getNbtPolicy() {
        return nbtPolicy;
    }

    public boolean isBoundTo(UUID player) {
        return owner != null && owner.equals(player);
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        requestExternalStorageRefresh();
        setChanged();
        if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
            LOGGER.info("[EMCStorageBridge] Bound RS EMC Interface owner={} pos={}", owner, getBlockPos());
        }
    }

    public NbtPolicy toggleNbtPolicy() {
        nbtPolicy = nbtPolicy == NbtPolicy.ALLOW ? NbtPolicy.REJECT : NbtPolicy.ALLOW;
        requestExternalStorageRefresh();
        setChanged();
        if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
            LOGGER.info("[EMCStorageBridge] Changed RS EMC Interface NBT policy owner={} pos={} policy={}", owner,
                    getBlockPos(), nbtPolicy);
        }
        return nbtPolicy;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        requestExternalStorageRefresh();
    }

    public void serverTick() {
        if (!refreshExternalStorage || level == null || level.isClientSide) return;
        if (++refreshDelayTicks < 5) return;
        refreshDelayTicks = 0;
        refreshAttempts++;

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(direction);
            if (!level.hasChunkAt(neighborPos)) continue;
            if (!(level.getBlockEntity(neighborPos) instanceof ExternalStorageBlockEntity externalStorage)) continue;
            ExternalStorageNetworkNode node = externalStorage.getNode();
            if (node == null || node.getDirection() != direction.getOpposite()) continue;
            if (node.getNetwork() == null) continue;

            BlockState externalState = level.getBlockState(neighborPos);
            externalState.neighborChanged(level, neighborPos, externalState.getBlock(), worldPosition, false);
            refreshExternalStorage = false;
            if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
                LOGGER.info("[EMCStorageBridge] Refreshed adjacent RS External Storage after EMC interface load/configuration pos={} externalStorage={} network={}",
                        worldPosition, neighborPos, Integer.toHexString(System.identityHashCode(node.getNetwork())));
            }
            return;
        }

        if (refreshAttempts >= 200) {
            refreshExternalStorage = false;
            if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
                LOGGER.warn("[EMCStorageBridge] RS External Storage was not ready after {} retries near EMC interface pos={}",
                        refreshAttempts, worldPosition);
            }
        }
    }

    private void requestExternalStorageRefresh() {
        refreshExternalStorage = true;
        refreshDelayTicks = 0;
        refreshAttempts = 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) tag.putUUID(OWNER_TAG, owner);
        tag.putString(NBT_POLICY_TAG, nbtPolicy.name());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID(OWNER_TAG) ? tag.getUUID(OWNER_TAG) : null;
        nbtPolicy = NbtPolicy.fromConfig(tag.getString(NBT_POLICY_TAG));
    }
}
