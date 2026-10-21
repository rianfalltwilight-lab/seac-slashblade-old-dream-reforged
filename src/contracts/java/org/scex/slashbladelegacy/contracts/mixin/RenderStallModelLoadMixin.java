package org.scex.slashbladelegacy.contracts.mixin;

import org.scex.slashbladelegacy.contracts.RenderStallMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets="mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager$1",remap=false)
public abstract class RenderStallModelLoadMixin {
    @Inject(method="load(Lnet/minecraft/resources/ResourceLocation;)Lmods/flammpfeil/slashblade/client/renderer/model/obj/WavefrontObject;",at=@At("HEAD"))
    private void probe$load(CallbackInfoReturnable<?> ci) {
        if(RenderStallMetrics.active())RenderStallMetrics.add(RenderStallMetrics.MODEL_LOADS,1);
    }
}
