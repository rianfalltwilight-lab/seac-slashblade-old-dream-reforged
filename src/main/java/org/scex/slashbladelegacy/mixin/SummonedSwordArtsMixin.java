package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.ability.SummonedSwordArts;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import net.minecraft.server.level.ServerPlayer;
import org.scex.slashbladelegacy.SummonedBladeMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The public input event cannot cancel only the original single-shot branch.
 * Version-pinned synthetic method verified against the production JAR with javap.
 * Required injection failure stops startup rather than charging/spawning both projectiles.
 */
@Mixin(value=SummonedSwordArts.class,remap=false)
public abstract class SummonedSwordArtsMixin {
    @Inject(method="lambda$onInputChange$6(Lnet/minecraft/server/level/ServerPlayer;ILmods/flammpfeil/slashblade/capability/slashblade/ISlashBladeState;)V",
            at=@At("HEAD"),cancellable=true,require=1,expect=1)
    private void legacySingleShot(ServerPlayer player,int power,ISlashBladeState state,CallbackInfo callback) {
        if (SummonedBladeMode.enabled(player.getMainHandItem())) callback.cancel();
    }
}
