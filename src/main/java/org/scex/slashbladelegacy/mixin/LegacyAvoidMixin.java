package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.ability.SlayerStyleArts;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.util.InputCommand;
import org.scex.slashbladelegacy.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=SlayerStyleArts.class,remap=false)
public abstract class LegacyAvoidMixin {
    @Inject(method="onInputChange",at=@At("HEAD"),cancellable=true,require=1)
    private void legacyAvoid(InputCommandEvent event,CallbackInfo ci) {
        if(!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT))return;
        var current=event.getCurrent();
        // r87 routes Lock-on + forward + V to AirTrick before AvoidAction.
        // Keep the existing AirTrick path, including other addons' hooks.
        if(current.contains(InputCommand.SNEAK) && current.contains(InputCommand.FORWARD))return;
        if(!event.getOld().contains(InputCommand.SPRINT) && current.contains(InputCommand.SPRINT))
            LegacyAvoid.move(event.getEntity(),current);
        ci.cancel();
    }
}
