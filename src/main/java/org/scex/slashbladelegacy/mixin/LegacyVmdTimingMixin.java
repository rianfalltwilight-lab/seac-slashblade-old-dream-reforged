package org.scex.slashbladelegacy.mixin;

import org.scex.slashbladelegacy.LegacyAnimationTiming;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Retimes existing complete motion assets; no new keyframes. Unmarked native animations are unchanged. */
@Pseudo
@Mixin(targets="mods.flammpfeil.slashblade.compat.playerAnim.VmdAnimation",remap=false)
public abstract class LegacyVmdTimingMixin implements LegacyAnimationTiming {
    @Shadow double start;
    @Shadow double end;
    @Shadow double span;
    @Unique private double legacyCompat$rate=1;
    @Unique private double legacyCompat$ticks;
    public void legacyCompat$duration(double ticks) {
        legacyCompat$ticks=ticks;
        legacyCompat$rate=Math.abs(end-start)/1.5/ticks;
        span=ticks;
    }
    @ModifyArg(method="setupAnim",at=@At(value="INVOKE",target="Lmods/flammpfeil/slashblade/util/TimeValueHelper;getMSecFromTicks(D)D"),index=0)
    private double legacyCompat$motionTime(double ticks) {
        return legacyCompat$ticks>0?Math.min(legacyCompat$ticks,ticks)*legacyCompat$rate:ticks;
    }
    @Inject(method="getClone",at=@At("RETURN"))
    private void legacyCompat$copyTiming(CallbackInfoReturnable<Object> result) {
        if(legacyCompat$ticks>0)((LegacyAnimationTiming)result.getReturnValue()).legacyCompat$duration(legacyCompat$ticks);
    }
}
