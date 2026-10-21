package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.scex.slashbladelegacy.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets={"mods.flammpfeil.slashblade.event.handler.FallHandler",
        "mods.flammpfeil.slashblade.event.FallHandler"},remap=false)
public abstract class LegacyLandingMixin {
    @WrapOperation(method="*(Lnet/minecraft/world/entity/LivingEntity;Lmods/flammpfeil/slashblade/capability/slashblade/ISlashBladeState;)V",at=@At(value="INVOKE",target="Lmods/flammpfeil/slashblade/capability/slashblade/ISlashBladeState;setComboSeq(Lnet/minecraft/resources/ResourceLocation;)V"),require=1,expect=1,allow=1)
    private static void legacyCompat$landingClock(ISlashBladeState state,ResourceLocation loc,Operation<Void> original,LivingEntity user,ISlashBladeState capturedState) {
        if(!user.level().isClientSide && org.scex.slashbladelegacy.LegacyMode.legacy(user) && loc.equals(LegacyCombat.id(LegacyMove.HELM_LANDING))) {
            state.updateComboSeq(user,loc);
        } else original.call(state,loc);
    }
}
