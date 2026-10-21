package org.scex.slashbladelegacy.mixin;

import org.spongepowered.asm.mixin.Mixin;

/** The plugin adds the missing typed getter before this client-only marker applies. */
@Mixin(targets="mods.flammpfeil.slashblade.client.renderer.model.BladeMotionManager",remap=false)
public abstract class LegacyBladeMotionApiMixin {}
