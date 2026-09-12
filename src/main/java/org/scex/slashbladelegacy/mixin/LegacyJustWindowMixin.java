package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.world.entity.LivingEntity;
import org.scex.slashbladelegacy.LegacyCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=SlashArts.class,remap=false)
public abstract class LegacyJustWindowMixin {
    @Inject(method="getJustReceptionSpan(Lnet/minecraft/world/entity/LivingEntity;)I",at=@At("RETURN"),cancellable=true,require=1)
    private static void legacyJustSpan(LivingEntity user,CallbackInfoReturnable<Integer> result) {
        if(org.scex.slashbladelegacy.LegacyMode.enabled(user,LegacyCompat.LEGACY_CHARGE)) result.setReturnValue(4);
    }
}
