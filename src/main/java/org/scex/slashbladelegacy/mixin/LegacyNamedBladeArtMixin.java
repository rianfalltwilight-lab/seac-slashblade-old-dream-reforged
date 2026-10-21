package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import org.scex.slashbladelegacy.LegacyNamedBlades;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=ISlashBladeState.class,remap=false)
public interface LegacyNamedBladeArtMixin {
    @Inject(method="getSlashArts",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$defaultArt(CallbackInfoReturnable<SlashArts> cir) {
        if(this instanceof LegacyNamedBladeStackAccessor access) {
            var corrected=LegacyNamedBlades.art((ISlashBladeState)(Object)this,access.legacyCompat$blade());
            if(corrected!=null)cir.setReturnValue(corrected);
        }
    }
}
