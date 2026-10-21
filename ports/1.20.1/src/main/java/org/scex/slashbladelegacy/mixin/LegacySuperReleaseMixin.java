package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.ability.SuperSlashArts;
import net.minecraft.server.level.ServerPlayer;
import org.scex.slashbladelegacy.LegacyMode;
import org.scex.slashbladelegacy.LegacySuperArts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Older releases perform this action inside the input handler. */
@Mixin(value=SuperSlashArts.class,remap=false)
public abstract class LegacySuperReleaseMixin {
    @Inject(method="releaseSSA(Lnet/minecraft/server/level/ServerPlayer;)V",at=@At("HEAD"),cancellable=true,require=1)
    private static void legacyRelease(ServerPlayer player,CallbackInfo ci) {
        if(LegacyMode.legacy(player)){LegacySuperArts.release(player);ci.cancel();}
    }
}
