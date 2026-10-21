package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.util.KnockBacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Some public DoSlash/SE callers pass null; preserve the native default and serializable state. */
@Mixin(value=EntityDrive.class,remap=false)
public abstract class LegacyDriveKnockbackMixin {
    @Inject(method="setKnockBack",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$defaultKnockback(KnockBacks action,CallbackInfo ci) {
        if(action==null){((EntityDrive)(Object)this).setKnockBack(KnockBacks.cancel);ci.cancel();}
    }
}
