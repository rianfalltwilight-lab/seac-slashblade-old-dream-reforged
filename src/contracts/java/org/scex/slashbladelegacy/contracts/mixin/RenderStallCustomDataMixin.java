package org.scex.slashbladelegacy.contracts.mixin;

import net.minecraft.world.item.component.CustomData;
import org.scex.slashbladelegacy.contracts.RenderStallMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CustomData.class)
public abstract class RenderStallCustomDataMixin {
    @Inject(method="copyTag",at=@At("HEAD"))
    private void probe$copy(CallbackInfoReturnable<?> ci) {
        if(RenderStallMetrics.active())RenderStallMetrics.add(RenderStallMetrics.COPY_TAG,1);
    }
}
