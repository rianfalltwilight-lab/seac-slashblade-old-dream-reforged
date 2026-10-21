package org.scex.slashbladelegacy.mixin;

import org.scex.slashbladelegacy.BladeInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Keep input handling independent of the upstream event package. */
@Pseudo
@Mixin(targets={"mods.flammpfeil.slashblade.event.handler.InputCommandEvent",
        "mods.flammpfeil.slashblade.event.InputCommandEvent"},remap=false)
public abstract class LegacyInputEventMixin implements BladeInput {}
