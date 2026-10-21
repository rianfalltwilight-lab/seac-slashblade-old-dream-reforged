package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.ability.SummonedSwordArts;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullConsumer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.scex.slashbladelegacy.SummonedBladeMode;
import org.scex.slashbladelegacy.BladeInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Legacy mode cancels native onInputChange scheduling.
 * Modern mode retains native input scheduling.
 * The immediate-shot hook uses LazyOptional.ifPresent in onInputChange. */
@Mixin(value=SummonedSwordArts.class,remap=false)
public abstract class SummonedSwordArtsMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method="onInputChange",at=@At("HEAD"),cancellable=true)
    private void legacyRange(@Coerce Object source,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        var event=(BladeInput)source;
        if(org.scex.slashbladelegacy.LegacyMode.legacy(event.getEntity()))ci.cancel();
    }
    @WrapOperation(method="onInputChange",at=@At(value="INVOKE",target="Lnet/minecraftforge/common/util/LazyOptional;ifPresent(Lnet/minecraftforge/common/util/NonNullConsumer;)V",ordinal=1),require=1,expect=1,allow=1)
    private void legacySingleShot(LazyOptional<ISlashBladeState> state,NonNullConsumer<ISlashBladeState> action,Operation<Void> original,@Coerce Object source) {
        var event=(BladeInput)source;
        if(org.scex.slashbladelegacy.LegacyMode.modern(event.getEntity()) || !SummonedBladeMode.enabled(event.getEntity().getMainHandItem()))original.call(state,action);
    }
}
