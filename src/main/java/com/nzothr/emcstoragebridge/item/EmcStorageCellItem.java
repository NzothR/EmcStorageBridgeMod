package com.nzothr.emcstoragebridge.item;

import java.util.List;
import java.util.UUID;

import com.nzothr.emcstoragebridge.config.EmcStorageBridgeConfig;
import com.nzothr.emcstoragebridge.core.NbtPolicy;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class EmcStorageCellItem extends Item {
    public static final String OWNER_TAG = "OwnerUUID";
    public static final String NBT_POLICY_TAG = "NbtPolicy";

    public EmcStorageCellItem(Properties properties) {
        super(properties);
    }

    public static UUID getOwner(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().hasUUID(OWNER_TAG)) {
            return null;
        }
        return stack.getTag().getUUID(OWNER_TAG);
    }

    public static NbtPolicy getNbtPolicy(ItemStack stack) {
        return stack.hasTag() ? NbtPolicy.fromConfig(stack.getTag().getString(NBT_POLICY_TAG)) : NbtPolicy.REJECT;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        UUID owner = getOwner(stack);
        if (owner == null) {
            stack.getOrCreateTag().putUUID(OWNER_TAG, player.getUUID());
            stack.getTag().putString(NBT_POLICY_TAG, EmcStorageBridgeConfig.DEFAULT_NBT_POLICY.get());
            player.displayClientMessage(Component.translatable("message.emcstoragebridge.cell_bound"), true);
        } else if (player.isShiftKeyDown()) {
            NbtPolicy next = getNbtPolicy(stack) == NbtPolicy.REJECT ? NbtPolicy.ALLOW : NbtPolicy.REJECT;
            stack.getOrCreateTag().putString(NBT_POLICY_TAG, next.name());
            player.displayClientMessage(Component.translatable("message.emcstoragebridge.nbt_policy", policyText(next)), true);
        } else {
            player.displayClientMessage(Component.translatable("message.emcstoragebridge.cell_bound_to",
                    owner.toString(), policyText(getNbtPolicy(stack))), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        UUID owner = getOwner(stack);
        if (owner == null) {
            tooltip.add(Component.translatable("tooltip.emcstoragebridge.unbound").withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.translatable("tooltip.emcstoragebridge.bind_hint").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.emcstoragebridge.owner", owner.toString()));
        tooltip.add(Component.translatable("tooltip.emcstoragebridge.nbt_policy", policyText(getNbtPolicy(stack))));
        tooltip.add(Component.translatable("tooltip.emcstoragebridge.nbt_hint").withStyle(ChatFormatting.GRAY));
    }

    private static Component policyText(NbtPolicy policy) {
        return Component.translatable("tooltip.emcstoragebridge.policy." + policy.name().toLowerCase());
    }
}
