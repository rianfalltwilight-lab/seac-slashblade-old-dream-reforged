package org.scex.slashbladelegacy.contracts.mixin;

import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import org.scex.slashbladelegacy.contracts.RenderStallMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=BladeModelManager.class,remap=false)
public abstract class RenderStallModelMixin {
    @Inject(method="getModel",at=@At("HEAD"))
    private void probe$get(CallbackInfoReturnable<?> ci) {
        if(RenderStallMetrics.active())RenderStallMetrics.add(RenderStallMetrics.MODEL_GETS,1);
    }
}
