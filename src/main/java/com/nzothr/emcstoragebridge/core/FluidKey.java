package com.nzothr.emcstoragebridge.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.util.Objects;

/** Immutable registry id + fluid tag key shared by the AE2 and RS adapters. */
public final class FluidKey {
    private final ResourceLocation fluidId;
    private final CompoundTag tag;

    public FluidKey(ResourceLocation fluidId, CompoundTag tag) {
        this.fluidId = Objects.requireNonNull(fluidId);
        this.tag = tag == null ? null : tag.copy();
    }

    public static FluidKey of(FluidStack stack) {
        return new FluidKey(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(stack.getFluid()), stack.getTag());
    }

    public ResourceLocation fluidId() {
        return fluidId;
    }

    public CompoundTag tag() {
        return tag == null ? null : tag.copy();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FluidKey key && fluidId.equals(key.fluidId) && Objects.equals(tag, key.tag);
    }

    @Override
    public int hashCode() {
        return 31 * fluidId.hashCode() + Objects.hashCode(tag);
    }

    public Fluid resolveFluid() {
        return net.minecraft.core.registries.BuiltInRegistries.FLUID.getOptional(fluidId).orElse(null);
    }
}
