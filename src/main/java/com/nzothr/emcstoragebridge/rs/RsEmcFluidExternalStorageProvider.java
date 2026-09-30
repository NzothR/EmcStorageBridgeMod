package com.nzothr.emcstoragebridge.rs;

import com.refinedmods.refinedstorage.api.storage.externalstorage.IExternalStorage;
import com.refinedmods.refinedstorage.api.storage.externalstorage.IExternalStorageContext;
import com.refinedmods.refinedstorage.api.storage.externalstorage.IExternalStorageProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;

public final class RsEmcFluidExternalStorageProvider implements IExternalStorageProvider<FluidStack> {
    @Override
    public boolean canProvide(BlockEntity blockEntity, Direction direction) {
        return blockEntity instanceof EmcInterfaceBlockEntity;
    }

    @Override
    public IExternalStorage<FluidStack> provide(IExternalStorageContext context, BlockEntity blockEntity,
            Direction direction) {
        return new RsEmcFluidExternalStorage(context, (EmcInterfaceBlockEntity) blockEntity);
    }

    @Override
    public int getPriority() {
        return 100;
    }
}
