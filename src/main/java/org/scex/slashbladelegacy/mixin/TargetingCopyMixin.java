package org.scex.slashbladelegacy.mixin;

import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import mods.flammpfeil.slashblade.util.TargetSelector;
import org.scex.slashbladelegacy.LegacyCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla copy() erases SlashBlade's attacker-aware subclass. Preserve all copied options. */
@Mixin(TargetingConditions.class)
public abstract class TargetingCopyMixin {
    @Shadow private double range;
    @Shadow private boolean checkLineOfSight;
    @Shadow private boolean testInvisible;
    @Shadow private Predicate<LivingEntity> selector;
    @Inject(method="copy",at=@At("RETURN"),cancellable=true)
    private void legacyCompat$copyAttackerRules(CallbackInfoReturnable<TargetingConditions> cir) {
        if(!((Object)this instanceof TargetSelector.SlashBladeTargetingConditions) || !LegacyCompat.isEnabled(LegacyCompat.HOSTILE_TARGETING))return;
        TargetingConditions result=new TargetSelector.SlashBladeTargetingConditions().range(range).selector(selector);
        if(!checkLineOfSight)result.ignoreLineOfSight();
        if(!testInvisible)result.ignoreInvisibilityTesting();
        cir.setReturnValue(result);
    }
}
