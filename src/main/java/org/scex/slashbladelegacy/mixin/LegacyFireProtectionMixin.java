package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.event.handler.SlashBladeEventHandler;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.scex.slashbladelegacy.LegacyCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace passive Resharpened fire immunity only for blades managed by the old rules. */
@Mixin(value=SlashBladeEventHandler.class,remap=false)
public abstract class LegacyFireProtectionMixin {
    @Inject(method="onLivingOnFire",at=@At("HEAD"),cancellable=true)
    private static void legacyCompat$activeFireResistance(LivingIncomingDamageEvent event,CallbackInfo ci) {
        if(LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) && BladeStateAccess.of(event.getEntity().getMainHandItem()).isPresent())ci.cancel();
    }
}
