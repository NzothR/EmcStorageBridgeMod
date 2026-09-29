package com.nzothr.emcstoragebridge.rs;

import java.util.UUID;

import com.mojang.logging.LogUtils;
import com.nzothr.emcstoragebridge.core.NbtPolicy;
import com.nzothr.emcstoragebridge.config.EmcStorageBridgeConfig;
import net.minecraft.core.BlockPos;
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
        setChanged();
        if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
            LOGGER.info("[EMCStorageBridge] Bound RS EMC Interface owner={} pos={}", owner, getBlockPos());
        }
    }

    public NbtPolicy toggleNbtPolicy() {
        nbtPolicy = nbtPolicy == NbtPolicy.ALLOW ? NbtPolicy.REJECT : NbtPolicy.ALLOW;
        setChanged();
        if (EmcStorageBridgeConfig.ENABLE_DEBUG_LOG.get()) {
            LOGGER.info("[EMCStorageBridge] Changed RS EMC Interface NBT policy owner={} pos={} policy={}", owner,
                    getBlockPos(), nbtPolicy);
        }
        return nbtPolicy;
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
