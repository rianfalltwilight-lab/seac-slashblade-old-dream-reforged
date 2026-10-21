package org.scex.slashbladelegacy.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.scex.slashbladelegacy.LegacyArts;
import org.scex.slashbladelegacy.LegacyMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The SI spear already computes its dash; a server player also needs the velocity packet. */
@Pseudo
@Mixin(targets="org.scex.sislashblade.combat.FoxArts",remap=false)
public abstract class LegacyFoxSpearMotionMixin {
    @Inject(method="perform",at=@At("TAIL"),require=1)
    private static void syncDash(LivingEntity user,String art,CallbackInfo callback) {
        if(art.equals("spear") && user instanceof Player player && !user.level().isClientSide && LegacyMode.legacy(user))
            LegacyArts.syncMotion(player);
    }
}
