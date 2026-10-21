package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.scex.slashbladelegacy.LegacyManagedMelee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The old overload has no damage multiplier. */
@Mixin(value=AttackManager.class,remap=false)
public abstract class LegacyManagedMeleeOldMixin {
    @Inject(method="doMeleeAttack(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;ZZ)V",
            at=@At("HEAD"),cancellable=true,require=1)
    private static void legacyManagedMelee(LivingEntity user,Entity target,boolean force,boolean reset,CallbackInfo ci) {
        if(LegacyManagedMelee.handle(user,target,reset,1))ci.cancel();
    }
}
