package org.scex.slashbladelegacy.mixin;

import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import org.scex.slashbladelegacy.LegacySheathingRepair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stable public handler hook: r87 earned XP is collected at sheathing, not instantly repaired. */
@Pseudo
@Mixin(targets={"mods.flammpfeil.slashblade.event.handler.KillCounter",
        "mods.flammpfeil.slashblade.event.KillCounter"},remap=false)
public abstract class LegacySheathingRepairMixin {
    @Inject(method="onXPDropping",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$defer(LivingExperienceDropEvent event,CallbackInfo ci) {
        var player=event.getAttackingPlayer();
        if(player==null || !LegacySheathingRepair.handles(player.getMainHandItem()))return;
        var blade=player.getMainHandItem();var state=BladeStateAccess.of(blade).orElseThrow();
        org.scex.slashbladelegacy.LegacyDualWield.recordExperience(player,event.getEntity(),event.getDroppedExperience());
        int souls=org.scex.slashbladelegacy.BladeEvents.souls(blade,state,Math.max(0,event.getDroppedExperience()));
        if(player.getMainHandItem()==blade)LegacySheathingRepair.credit(player,blade,souls);
        ci.cancel();
    }
}
