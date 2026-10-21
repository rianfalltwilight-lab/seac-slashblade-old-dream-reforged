package org.scex.slashbladelegacy.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.scex.slashbladelegacy.LegacyCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Gaia extends Mob but does not implement Enemy.
 * These hooks replace local revenge and FRIENDLY_ENABLE results.
 * Keep global configuration and revenge tags unchanged.
 * SlashBlade 2.0.5 uses BooleanValue.get ordinal 0 for PVP.
 * It uses ordinal 1 for FRIENDLY.
 */
@Mixin(targets="mods.flammpfeil.slashblade.util.TargetSelector$AttackablePredicate", remap=false)
public abstract class GaiaTargetingMixin {
    @ModifyExpressionValue(method="test(Lnet/minecraft/world/entity/LivingEntity;)Z",
            at=@At(value="INVOKE",target="Ljava/util/Set;contains(Ljava/lang/Object;)Z"),require=1)
    private boolean legacyCompat$scopedRevenge(boolean original,LivingEntity target) {
        return LegacyCompat.isEnabled(LegacyCompat.HOSTILE_TARGETING)?org.scex.slashbladelegacy.HostileTargeting.REVENGE_CONTEXT.get()==target:original;
    }
    @ModifyExpressionValue(method="test(Lnet/minecraft/world/entity/LivingEntity;)Z",
            at=@At(value="INVOKE", target="Lnet/neoforged/neoforge/common/ModConfigSpec$BooleanValue;get()Ljava/lang/Object;", ordinal=1),
            require=1, expect=1)
    private Object legacyCompat$gaiaIsHostile(Object original, LivingEntity target) {
        return org.scex.slashbladelegacy.HostileTargeting.isAdditionalHostile(target)
                || org.scex.slashbladelegacy.HostileTargeting.SOURCE_CONTEXT.get()!=null
                    && org.scex.slashbladelegacy.LegacyMode.legacy(org.scex.slashbladelegacy.HostileTargeting.SOURCE_CONTEXT.get())
                    && org.scex.slashbladelegacy.LegacyTargets.defaultAttackable(target)
                ? Boolean.TRUE : original;
    }
}
