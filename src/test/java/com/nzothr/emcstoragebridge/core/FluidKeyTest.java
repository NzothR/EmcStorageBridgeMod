package com.nzothr.emcstoragebridge.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FluidKeyTest {
    @Test
    void absentAndEmptyTagsRemainDifferentKeys() {
        ResourceLocation water = ResourceLocation.parse("minecraft:water");
        FluidKey untagged = new FluidKey(water, null);
        FluidKey emptyTag = new FluidKey(water, new CompoundTag());

        assertNotEquals(untagged, emptyTag);
        assertNull(untagged.tag());
        assertEquals(new CompoundTag(), emptyTag.tag());
    }

    @Test
    void tagIsCopiedAtConstructionAndAccess() {
        ResourceLocation water = ResourceLocation.parse("minecraft:water");
        CompoundTag source = new CompoundTag();
        source.putInt("variant", 1);
        FluidKey key = new FluidKey(water, source);
        source.putInt("variant", 2);
        CompoundTag returned = key.tag();
        returned.putInt("variant", 3);

        assertEquals(1, key.tag().getInt("variant"));
    }
}
