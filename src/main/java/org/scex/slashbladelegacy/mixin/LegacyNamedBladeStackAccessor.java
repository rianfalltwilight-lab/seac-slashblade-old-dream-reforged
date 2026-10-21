package org.scex.slashbladelegacy.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets="mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess$ComponentBackedState",remap=false)
public interface LegacyNamedBladeStackAccessor {
    @Accessor("stack") ItemStack legacyCompat$blade();
}
