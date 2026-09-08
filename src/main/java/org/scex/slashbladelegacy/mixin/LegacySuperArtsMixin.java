package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.ability.SuperSlashArts;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import net.minecraft.server.level.ServerPlayer;
import org.scex.slashbladelegacy.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=SuperSlashArts.class,remap=false)
public abstract class LegacySuperArtsMixin {
    @Inject(method="onInputChange",at=@At("HEAD"),cancellable=true,require=1)
    private void legacyInput(InputCommandEvent event,CallbackInfo ci) {
        if(LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT)){LegacySuperArts.input(event);ci.cancel();}
    }
    @Inject(method="releaseSSA",at=@At("HEAD"),cancellable=true,require=1)
    private static void legacyRelease(ServerPlayer player,CallbackInfo ci) {
        if(LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT)){LegacySuperArts.release(player);ci.cancel();}
    }
}
