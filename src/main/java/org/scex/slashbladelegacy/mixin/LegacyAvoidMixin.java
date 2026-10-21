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
        if(!org.scex.slashbladelegacy.LegacyMode.legacy(event.getEntity()))return;
        var current=event.getCurrent();
        if(!event.getOld().contains(InputCommand.SPRINT) && current.contains(InputCommand.SPRINT)) {
            // Route the key press before native TrickUp checks for a locked entity.
            if(current.contains(InputCommand.SNEAK) && current.contains(InputCommand.FORWARD)) {
                var player=event.getEntity();
                var target=mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess.of(player.getMainHandItem())
                        .map(state->state.getTargetEntity(player.level())).orElse(null);
                LegacyAirTrick.launch(player,target);
            } else LegacyAvoid.move(event.getEntity(),current);
        }
        ci.cancel();
    }

    @Inject(method="executeAirTrick",at=@At("HEAD"),cancellable=true,require=1)
    private void legacyAirTrick(net.minecraft.server.level.ServerPlayer player,net.minecraft.world.level.Level level,
            net.minecraft.world.entity.Entity target,mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState state,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if(LegacyMode.legacy(player)) {
            LegacyAirTrick.launch(player,target);
            // Missing requirements must not fall through to a free modern teleport or dodge.
            cir.setReturnValue(true);
        }
    }
}
