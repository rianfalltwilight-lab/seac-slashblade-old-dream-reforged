package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.ability.SummonedSwordArts;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import java.util.Optional;
import java.util.function.Consumer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.scex.slashbladelegacy.SummonedBladeMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Suppress only the immediate shot callback; public input scheduling remains intact.
 * This uses the stable Optional callback in onInputChange, without compiler-generated lambda numbers. */
@Mixin(value=SummonedSwordArts.class,remap=false)
public abstract class SummonedSwordArtsMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method="onInputChange",at=@At("HEAD"),cancellable=true)
    private void legacyRange(InputCommandEvent event,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if(org.scex.slashbladelegacy.LegacyMode.legacy(event.getEntity()))ci.cancel();
    }
    @WrapOperation(method="onInputChange",at=@At(value="INVOKE",target="Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V"),require=1,expect=1,allow=1)
    private void legacySingleShot(Optional<ISlashBladeState> state,Consumer<ISlashBladeState> action,Operation<Void> original,InputCommandEvent event) {
        if(org.scex.slashbladelegacy.LegacyMode.modern(event.getEntity()) || !SummonedBladeMode.enabled(event.getEntity().getMainHandItem()))original.call(state,action);
    }
}
