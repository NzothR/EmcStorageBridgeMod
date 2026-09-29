package com.nzothr.emcstoragebridge.rs;

import com.nzothr.emcstoragebridge.EmcStorageBridgeMod;
import com.nzothr.emcstoragebridge.item.EmcStorageBridgeItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class EmcInterfaceBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, EmcStorageBridgeMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EmcStorageBridgeMod.MOD_ID);

    public static final RegistryObject<Block> EMC_INTERFACE = BLOCKS.register("emc_interface", EmcInterfaceBlock::new);
    public static final RegistryObject<Item> EMC_INTERFACE_ITEM = EmcStorageBridgeItems.ITEMS.register("emc_interface",
            () -> new BlockItem(EMC_INTERFACE.get(), new Item.Properties()) {
                @Override
                public void appendHoverText(net.minecraft.world.item.ItemStack stack,
                        net.minecraft.world.level.Level level, java.util.List<Component> tooltip,
                        net.minecraft.world.item.TooltipFlag flags) {
                    tooltip.add(Component.translatable("tooltip.emcstoragebridge.emc_interface"));
                }
            });
    public static final RegistryObject<BlockEntityType<EmcInterfaceBlockEntity>> EMC_INTERFACE_ENTITY =
            BLOCK_ENTITIES.register("emc_interface", () -> BlockEntityType.Builder
                    .of(EmcInterfaceBlockEntity::new, EMC_INTERFACE.get()).build(null));

    private EmcInterfaceBlocks() {
    }
}
