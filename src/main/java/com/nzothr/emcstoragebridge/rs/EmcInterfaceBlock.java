package com.nzothr.emcstoragebridge.rs;

import moze_intel.projecte.gameObjs.registries.PEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class EmcInterfaceBlock extends Block implements EntityBlock {
    public EmcInterfaceBlock() {
        super(BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EmcInterfaceBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof EmcInterfaceBlockEntity blockEntity) {
            blockEntity.setOwner(player.getUUID());
            player.sendSystemMessage(Component.translatable("message.emcstoragebridge.interface_bound"));
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || held.getItem() != PEItems.PHILOSOPHERS_STONE.get()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (!(level.getBlockEntity(pos) instanceof EmcInterfaceBlockEntity blockEntity)) {
            return InteractionResult.FAIL;
        }
        if (!blockEntity.isBoundTo(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.emcstoragebridge.interface_not_owner"));
            return InteractionResult.CONSUME;
        }

        var policy = blockEntity.toggleNbtPolicy();
        player.sendSystemMessage(Component.translatable(
                policy == com.nzothr.emcstoragebridge.core.NbtPolicy.ALLOW
                        ? "message.emcstoragebridge.interface_nbt_allowed"
                        : "message.emcstoragebridge.interface_nbt_rejected"));
        return InteractionResult.CONSUME;
    }
}
