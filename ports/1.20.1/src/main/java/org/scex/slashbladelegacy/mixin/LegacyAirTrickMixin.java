package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.ability.SlayerStyleArts;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.scex.slashbladelegacy.LegacyAirTrick;
import org.scex.slashbladelegacy.LegacyMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Newer releases expose a separate direct Air Trick entry. */
@Mixin(value=SlayerStyleArts.class,remap=false)
public abstract class LegacyAirTrickMixin {
    @Inject(method="executeAirTrick(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lmods/flammpfeil/slashblade/capability/slashblade/ISlashBladeState;)Z",at=@At("HEAD"),cancellable=true,require=1)
    private void legacyAirTrick(ServerPlayer player,Level level,Entity target,ISlashBladeState state,CallbackInfoReturnable<Boolean> cir) {
        if(LegacyMode.legacy(player)) {
            LegacyAirTrick.launch(player,target);
            // A failed legacy requirement must not permit a native teleport.
            cir.setReturnValue(true);
        }
    }
}
