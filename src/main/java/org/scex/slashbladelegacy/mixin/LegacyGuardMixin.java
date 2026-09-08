package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.scex.slashbladelegacy.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="mods.flammpfeil.slashblade.ability.Guard", remap=false)
public abstract class LegacyGuardMixin {
    @Inject(method="onLivingAttack", at=@At("HEAD"), cancellable=true)
    private void legacyCompat$guard(LivingIncomingDamageEvent event, CallbackInfo ci) {
        if (!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) || !(event.getEntity() instanceof Player)
                || BladeStateAccess.of(event.getEntity().getMainHandItem()).isEmpty()) return;
        LegacyJustGuard.incoming(event);
        ci.cancel();
    }
}
