package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import org.scex.slashbladelegacy.LegacyCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=ISlashBladeState.class,remap=false)
public interface LegacyChargeMixin {
    @Inject(method="updateComboSeq(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/resources/ResourceLocation;)V",
            at=@At("RETURN"),require=1)
    private void legacyClearAfterRest(LivingEntity user,ResourceLocation combo,org.spongepowered.asm.mixin.injection.callback.CallbackInfo result) {
        // ItemSlashBlade.setComboSequence(None) clears IsCharged in r87. Without
        // this, an expired SA can leave an extra Drive armed through later combos.
        if(LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT)
                && ((ISlashBladeState)(Object)this).getComboSeq().equals(ComboStateRegistry.NONE.getId())
                && mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess.of(user.getMainHandItem())
                    .map(state->state.getComboSeq().equals(ComboStateRegistry.NONE.getId())).orElse(false))
            org.scex.slashbladelegacy.LegacyAdditionalAttack.clearCharged(user.getMainHandItem());
    }
    @Inject(method="doChargeAction(Lnet/minecraft/world/entity/LivingEntity;I)Lnet/minecraft/resources/ResourceLocation;",
            at=@At("RETURN"),require=1)
    private void legacyAfterCharge(LivingEntity user,int elapsed,CallbackInfoReturnable<ResourceLocation> result) {
        org.scex.slashbladelegacy.LegacyAdditionalAttack.markCharged(user,result.getReturnValue());
    }
    @Inject(method="getFullChargeTicks(Lnet/minecraft/world/entity/LivingEntity;)I",at=@At("RETURN"),cancellable=true,require=1)
    private void legacyCue(LivingEntity user,CallbackInfoReturnable<Integer> result) {
        if(LegacyCompat.isEnabled(LegacyCompat.LEGACY_CHARGE) && result.getReturnValueI()==9) result.setReturnValue(15);
    }
    @Inject(method="doChargeAction(Lnet/minecraft/world/entity/LivingEntity;I)Lnet/minecraft/resources/ResourceLocation;",
            at=@At("HEAD"),cancellable=true,require=1)
    private void legacyMinimum(LivingEntity user,int elapsed,CallbackInfoReturnable<ResourceLocation> result) {
        if(LegacyCompat.isEnabled(LegacyCompat.LEGACY_CHARGE) && elapsed<=15) result.setReturnValue(ComboStateRegistry.NONE.getId());
    }
}
