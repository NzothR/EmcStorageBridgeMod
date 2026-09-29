package com.nzothr.emcstoragebridge.item;

import com.nzothr.emcstoragebridge.EmcStorageBridgeMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.fml.ModList;

public final class EmcStorageBridgeItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, EmcStorageBridgeMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EmcStorageBridgeMod.MOD_ID);

    public static RegistryObject<Item> EMC_STORAGE_CELL;

    public static void registerAe2Item() {
        EMC_STORAGE_CELL = ITEMS.register("emc_storage_cell",
                () -> new EmcStorageCellItem(new Item.Properties().stacksTo(1)));
    }

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.emcstoragebridge"))
                    .icon(() -> {
                        Item item = optionalItem("emc_storage_cell", "ae2");
                        if (item == null) item = optionalItem("emc_interface", "refinedstorage");
                        return (item == null ? net.minecraft.world.item.Items.EMERALD : item).getDefaultInstance();
                    })
                    .displayItems((parameters, output) -> {
                        Item cell = optionalItem("emc_storage_cell", "ae2");
                        Item rsInterface = optionalItem("emc_interface", "refinedstorage");
                        if (cell != null) output.accept(cell);
                        if (rsInterface != null) output.accept(rsInterface);
                    })
                    .build());

    private static Item optionalItem(String path, String modId) {
        if (!ModList.get().isLoaded(modId)) return null;
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(EmcStorageBridgeMod.MOD_ID, path));
        return item == net.minecraft.world.item.Items.AIR ? null : item;
    }

    private EmcStorageBridgeItems() {
    }
}
