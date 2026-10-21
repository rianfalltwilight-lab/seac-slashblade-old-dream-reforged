package org.scex.slashbladelegacy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** 读取时不向无标签物品写入空 NBT。 */
public final class LegacyTags {
    private LegacyTags() {}
    public static CompoundTag read(ItemStack stack) {
        var tag=stack.getTag();
        return tag==null?new CompoundTag():tag;
    }
    public static CompoundTag copy(ItemStack stack) {return read(stack).copy();}
}
