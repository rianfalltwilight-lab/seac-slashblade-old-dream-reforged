package org.scex.slashbladelegacy.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The melee already hit immediately; retain the native arc and its SE event without a second hit. */
@Mixin(value=EntitySlashEffect.class,remap=false)
public abstract class LegacySlashVisualMixin {
    @ModifyExpressionValue(method="tick",at=@At(value="INVOKE",
            target="Lmods/flammpfeil/slashblade/entity/EntitySlashEffect;getShooter()Lnet/minecraft/world/entity/Entity;",ordinal=2))
    private Entity legacyCompat$visualOnly(Entity shooter) {
        return ((Entity)(Object)this).getPersistentData().getBoolean("slashblade_legacy_compat.visual_arc")?null:shooter;
    }
}
