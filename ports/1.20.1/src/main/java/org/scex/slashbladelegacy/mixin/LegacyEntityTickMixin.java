package org.scex.slashbladelegacy.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.scex.slashbladelegacy.LegacyBladeSouls;
import org.scex.slashbladelegacy.LegacyManaBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Apply entity updates after the server ticks each active entity. */
@Mixin(ServerLevel.class)
public abstract class LegacyEntityTickMixin {
    @Inject(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V", shift = At.Shift.AFTER))
    private void legacy$entity(Entity entity, CallbackInfo callback) { legacy$update(entity); }
    @Inject(method = "tickPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;rideTick()V", shift = At.Shift.AFTER))
    private void legacy$passenger(Entity vehicle, Entity passenger, CallbackInfo callback) { legacy$update(passenger); }
    @Unique private static void legacy$update(Entity entity) {
        LegacyBladeSouls.tick(entity);
        LegacyManaBridge.entityTick(entity);
    }
}
