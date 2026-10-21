package org.scex.slashbladelegacy.mixin;

import org.scex.slashbladelegacy.BladeStateAccess;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import org.scex.slashbladelegacy.LegacyCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace passive Resharpened fire immunity only for blades managed by the old rules. */
@Pseudo
@Mixin(targets={"mods.flammpfeil.slashblade.event.handler.SlashBladeEventHandler",
        "mods.flammpfeil.slashblade.event.SlashBladeEventHandler"},remap=false)
public abstract class LegacyFireProtectionMixin {
    @Inject(method="onLivingOnFire",at=@At("HEAD"),cancellable=true)
    private static void legacyCompat$activeFireResistance(LivingAttackEvent event,CallbackInfo ci) {
        if(org.scex.slashbladelegacy.LegacyMode.legacy(event.getEntity()) && BladeStateAccess.of(event.getEntity().getMainHandItem()).isPresent())ci.cancel();
    }
}
