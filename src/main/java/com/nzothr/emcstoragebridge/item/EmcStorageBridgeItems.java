package com.nzothr.emcstoragebridge.item;

import com.nzothr.emcstoragebridge.EmcStorageBridgeMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class EmcStorageBridgeItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, EmcStorageBridgeMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EmcStorageBridgeMod.MOD_ID);

    public static final RegistryObject<Item> EMC_STORAGE_CELL = ITEMS.register("emc_storage_cell",
            () -> new EmcStorageCellItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.emcstoragebridge"))
                    .icon(() -> EMC_STORAGE_CELL.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(EMC_STORAGE_CELL.get()))
                    .build());

    private EmcStorageBridgeItems() {
    }
}
