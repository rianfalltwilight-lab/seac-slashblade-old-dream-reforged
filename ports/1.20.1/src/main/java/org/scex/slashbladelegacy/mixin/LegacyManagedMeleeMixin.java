package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.scex.slashbladelegacy.LegacyManagedMelee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Addon SA callers already select their own target area; do not clip it to ordinary sword reach. */
@Mixin(value=AttackManager.class,remap=false)
public abstract class LegacyManagedMeleeMixin {
    @Inject(method="doMeleeAttack(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at=@At("HEAD"),cancellable=true,require=1)
    private static void legacyCompat$managedMelee(LivingEntity user,Entity target,boolean force,boolean reset,float ratio,CallbackInfo ci) {
        if(LegacyManagedMelee.handle(user,target,reset,ratio))ci.cancel();
    }
}
