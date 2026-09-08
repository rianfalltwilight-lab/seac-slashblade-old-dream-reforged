package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.event.bladestand.BlandStandEventHandler;
import mods.flammpfeil.slashblade.event.bladestand.ProudSoulEnchantmentEvent;
import org.scex.slashbladelegacy.LegacyEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Present in the installed 2.0.5 JAR, absent from some upstream revisions; selected by capability. */
@Mixin(value=BlandStandEventHandler.class,remap=false)
public abstract class LegacySoulProbabilityMixin {
    @Inject(method="proudSoulEnchantmentProbabilityCheck(Lmods/flammpfeil/slashblade/event/bladestand/ProudSoulEnchantmentEvent;)V",at=@At("HEAD"),cancellable=true,require=1)
    private static void legacyCompat$oneMaterialRoll(ProudSoulEnchantmentEvent event,CallbackInfo ci) {
        if(LegacyEnchantments.ownsSoulRoll(event))ci.cancel();
    }
}
