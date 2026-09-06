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
    @Inject(method="doChargeAction(Lnet/minecraft/world/entity/LivingEntity;I)Lnet/minecraft/resources/ResourceLocation;",
            at=@At("RETURN"),require=1)
    private void legacyAfterCharge(LivingEntity user,int elapsed,CallbackInfoReturnable<ResourceLocation> result) {
        org.scex.slashbladelegacy.LegacyAdditionalAttack.markCharged(user,result.getReturnValue());
    }
    @Inject(method="getFullChargeTicks(Lnet/minecraft/world/entity/LivingEntity;)I",at=@At("RETURN"),cancellable=true,require=1)
    private void legacyCue(LivingEntity user,CallbackInfoReturnable<Integer> result) {
        if(LegacyCompat.LEGACY_CHARGE.get() && result.getReturnValueI()==9) result.setReturnValue(15);
    }
    @Inject(method="doChargeAction(Lnet/minecraft/world/entity/LivingEntity;I)Lnet/minecraft/resources/ResourceLocation;",
            at=@At("HEAD"),cancellable=true,require=1)
    private void legacyMinimum(LivingEntity user,int elapsed,CallbackInfoReturnable<ResourceLocation> result) {
        if(LegacyCompat.LEGACY_CHARGE.get() && elapsed<=15) result.setReturnValue(ComboStateRegistry.NONE.getId());
    }
}
