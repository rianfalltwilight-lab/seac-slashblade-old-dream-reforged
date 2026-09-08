package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import org.scex.slashbladelegacy.client.LegacyHeldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Disable this dependency's VMD consumer, preserving the state/network event and other animation mods. */
@Pseudo
@Mixin(targets="mods.flammpfeil.slashblade.compat.playerAnim.PlayerAnimationOverrider",remap=false)
public abstract class LegacyBodyMotionMixin {
    @Inject(method="onBladeAnimationStart",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$vanillaSwing(BladeMotionEvent event,CallbackInfo ci) {
        if(LegacyHeldRenderer.handles(event.getEntity()))ci.cancel();
    }
}
