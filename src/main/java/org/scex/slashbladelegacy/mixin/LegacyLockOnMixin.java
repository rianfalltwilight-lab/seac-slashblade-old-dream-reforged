package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.ability.LockOnManager;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import org.scex.slashbladelegacy.LegacyLockOn;
import org.scex.slashbladelegacy.LegacyMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Legacy mode uses the r87 lock-on search. Modern mode keeps the upstream search. */
@Mixin(value=LockOnManager.class,remap=false)
public abstract class LegacyLockOnMixin {
    @Inject(method="onInputChange",at=@At("HEAD"),cancellable=true,require=1)
    private static void legacyLockOn(InputCommandEvent event,CallbackInfo ci) {
        if(!LegacyMode.legacy(event.getEntity()))return;
        LegacyLockOn.input(event.getEntity(),event.getOld(),event.getCurrent());
        ci.cancel();
    }
}
