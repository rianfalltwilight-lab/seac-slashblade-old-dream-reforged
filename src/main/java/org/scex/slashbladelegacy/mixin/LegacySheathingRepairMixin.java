package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import org.scex.slashbladelegacy.LegacySheathingRepair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stable public handler hook: r87 earned XP is collected at sheathing, not instantly repaired. */
@Mixin(targets="mods.flammpfeil.slashblade.event.handler.KillCounter",remap=false)
public abstract class LegacySheathingRepairMixin {
    @Inject(method="onXPDropping",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$defer(LivingExperienceDropEvent event,CallbackInfo ci) {
        var player=event.getAttackingPlayer();
        if(player==null || !LegacySheathingRepair.handles(player.getMainHandItem()))return;
        var blade=player.getMainHandItem();var state=BladeStateAccess.of(blade).orElseThrow();
        org.scex.slashbladelegacy.LegacyDualWield.recordExperience(player,event.getEntity(),event.getDroppedExperience());
        var souls=new SlashBladeEvent.AddProudSoulEvent(blade,state,Math.max(0,event.getDroppedExperience()));
        NeoForge.EVENT_BUS.post(souls);
        if(player.getMainHandItem()==blade)LegacySheathingRepair.credit(player,blade,souls.getNewCount());
        ci.cancel();
    }
}
