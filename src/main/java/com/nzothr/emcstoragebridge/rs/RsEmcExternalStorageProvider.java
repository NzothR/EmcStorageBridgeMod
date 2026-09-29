package com.nzothr.emcstoragebridge.rs;

import com.refinedmods.refinedstorage.api.storage.externalstorage.IExternalStorage;
import com.refinedmods.refinedstorage.api.storage.externalstorage.IExternalStorageContext;
import com.refinedmods.refinedstorage.api.storage.externalstorage.IExternalStorageProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class RsEmcExternalStorageProvider implements IExternalStorageProvider<net.minecraft.world.item.ItemStack> {
    @Override
    public boolean canProvide(BlockEntity blockEntity, Direction direction) {
        return blockEntity instanceof EmcInterfaceBlockEntity;
    }

    @Override
    public IExternalStorage<net.minecraft.world.item.ItemStack> provide(IExternalStorageContext context,
            BlockEntity blockEntity, Direction direction) {
        return new RsEmcExternalStorage(context, (EmcInterfaceBlockEntity) blockEntity);
    }

    @Override
    public int getPriority() {
        return 0;
    }
}
