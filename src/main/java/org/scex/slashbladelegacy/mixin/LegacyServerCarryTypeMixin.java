package org.scex.slashbladelegacy.mixin;

import org.spongepowered.asm.mixin.Mixin;

/** The plugin removes client lambda factories before this server-only marker applies. */
@Mixin(targets="mods.flammpfeil.slashblade.client.renderer.CarryType",remap=false)
public abstract class LegacyServerCarryTypeMixin {}
