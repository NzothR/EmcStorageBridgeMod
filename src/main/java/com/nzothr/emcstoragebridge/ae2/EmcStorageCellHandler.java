package com.nzothr.emcstoragebridge.ae2;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import com.nzothr.emcstoragebridge.item.EmcStorageBridgeItems;
import com.nzothr.emcstoragebridge.item.EmcStorageCellItem;
import net.minecraft.world.item.ItemStack;

public final class EmcStorageCellHandler implements ICellHandler {
    public static final EmcStorageCellHandler INSTANCE = new EmcStorageCellHandler();

    private EmcStorageCellHandler() {
    }

    @Override
    public boolean isCell(ItemStack stack) {
        return stack.is(EmcStorageBridgeItems.EMC_STORAGE_CELL.get());
    }

    @Override
    public StorageCell getCellInventory(ItemStack stack, ISaveProvider host) {
        return isCell(stack) ? new EmcStorageCellInventory(stack, host) : null;
    }
}
