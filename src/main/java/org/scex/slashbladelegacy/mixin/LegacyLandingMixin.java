package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.scex.slashbladelegacy.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ISlashBladeState.class,remap=false)
public interface LegacyLandingMixin {
    @Inject(method="synchronizeComboSeq",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$landingClock(LivingEntity user,ResourceLocation loc,CallbackInfo ci) {
        if(!user.level().isClientSide && LegacyCompat.LEGACY_COMBAT.get() && loc.equals(LegacyCombat.id(LegacyMove.HELM_LANDING))) {
            ((ISlashBladeState)(Object)this).updateComboSeq(user,loc);ci.cancel();
        }
    }
}
