package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.scex.slashbladelegacy.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Addon SA callers already select their own target area; do not clip it to ordinary sword reach. */
@Mixin(value=AttackManager.class,remap=false)
public abstract class LegacyManagedMeleeMixin {
    @Inject(method="doMeleeAttack(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at=@At("HEAD"),cancellable=true)
    private static void legacyCompat$managedMelee(LivingEntity user,Entity target,boolean force,boolean reset,float ratio,CallbackInfo ci) {
        if(!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) || !(user instanceof Player player)
                || BladeStateAccess.of(player.getMainHandItem()).isEmpty())return;
        ci.cancel();
        if(player.level().isClientSide || !Float.isFinite(ratio) || ratio<=0 || !LegacyTargets.attackable(player,target))return;
        LegacyDamage.update(player);
        var state=BladeStateAccess.of(player.getMainHandItem()).orElseThrow();
        var move=LegacyCombat.move(state.getComboSeq());
        if(move==LegacyMove.NONE)move=LegacyMove.SLASH_DIM;
        // The exact target is authorized by the SA, not by an arbitrary player attack packet.
        boolean hit=LegacyDamage.hit(player,target,move,target.getBoundingBox(),true,ratio);
        if(hit && reset)target.invulnerableTime=0;
    }
}
