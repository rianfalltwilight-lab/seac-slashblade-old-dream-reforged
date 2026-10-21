package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.ability.SuperSlashArts;
import org.scex.slashbladelegacy.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=SuperSlashArts.class,remap=false)
public abstract class LegacySuperArtsMixin {
    @Inject(method="onInputChange",at=@At("HEAD"),cancellable=true,require=1)
    private void legacyInput(@Coerce Object source,CallbackInfo ci) {
        var event=(BladeInput)source;
        if(org.scex.slashbladelegacy.LegacyMode.legacy(event.getEntity())){LegacySuperArts.input(event);ci.cancel();}
    }
}
