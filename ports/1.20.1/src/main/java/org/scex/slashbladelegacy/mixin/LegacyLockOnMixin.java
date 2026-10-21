package org.scex.slashbladelegacy.mixin;

import org.scex.slashbladelegacy.BladeInput;
import org.scex.slashbladelegacy.LegacyLockOn;
import org.scex.slashbladelegacy.LegacyMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Legacy mode uses the r87 lock-on search. Modern mode keeps the upstream search.
 * The event parameter is coerced because the input event package differs between SlashBlade APIs.
 */
@Mixin(targets="mods.flammpfeil.slashblade.ability.LockOnManager",remap=false)
public abstract class LegacyLockOnMixin {
    @Inject(method="onInputChange",at=@At("HEAD"),cancellable=true,require=1)
    private void legacyLockOn(@Coerce Object source,CallbackInfo ci) {
        var event=(BladeInput)source;
        if(!LegacyMode.legacy(event.getEntity()))return;
        LegacyLockOn.input(event.getEntity(),event.getOld(),event.getCurrent());
        ci.cancel();
    }
}
