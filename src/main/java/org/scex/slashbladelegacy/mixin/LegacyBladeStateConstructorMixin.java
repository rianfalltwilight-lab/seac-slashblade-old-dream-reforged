package org.scex.slashbladelegacy.mixin;

import org.spongepowered.asm.mixin.Mixin;

/** The plugin adds the missing old constructor before this marker applies. */
@Mixin(targets="mods.flammpfeil.slashblade.capability.slashblade.BladeStateData",remap=false)
public abstract class LegacyBladeStateConstructorMixin {}
