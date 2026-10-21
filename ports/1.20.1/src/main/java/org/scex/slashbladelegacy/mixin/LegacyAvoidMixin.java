package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.ability.SlayerStyleArts;
import mods.flammpfeil.slashblade.util.InputCommand;
import org.scex.slashbladelegacy.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=SlayerStyleArts.class,remap=false)
public abstract class LegacyAvoidMixin {
    @Inject(method="onInputChange",at=@At("HEAD"),cancellable=true,require=1)
    private void legacyAvoid(@Coerce Object source,CallbackInfo ci) {
        var event=(BladeInput)source;
        if(!org.scex.slashbladelegacy.LegacyMode.legacy(event.getEntity()))return;
        var current=event.getCurrent();
        if(!event.getOld().contains(InputCommand.SPRINT) && current.contains(InputCommand.SPRINT)) {
            // Route the key press before native TrickUp checks for a locked entity.
            if(current.contains(InputCommand.SNEAK) && current.contains(InputCommand.FORWARD)) {
                var player=event.getEntity();
                var target=org.scex.slashbladelegacy.BladeStateAccess.of(player.getMainHandItem())
                        .map(state->state.getTargetEntity(player.level())).orElse(null);
                LegacyAirTrick.launch(player,target);
            } else LegacyAvoid.move(event.getEntity(),current);
        }
        ci.cancel();
    }

}
