package org.scex.slashbladelegacy.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.scex.slashbladelegacy.LegacyProjectileGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="mods.flammpfeil.slashblade.ability.ArrowReflector",remap=false)
public abstract class LegacyReflectorMixin {
    @Inject(method="doTicks",at=@At("HEAD"),cancellable=true)
    private static void legacyCompat$guard(LivingEntity user,CallbackInfo ci) {
        if(LegacyProjectileGuard.handles(user)){LegacyProjectileGuard.tick((Player)user);ci.cancel();}
    }
}
