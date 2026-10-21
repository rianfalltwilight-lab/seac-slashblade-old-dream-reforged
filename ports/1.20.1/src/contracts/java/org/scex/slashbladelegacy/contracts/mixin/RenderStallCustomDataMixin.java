package org.scex.slashbladelegacy.contracts.mixin;

import net.minecraft.nbt.CompoundTag;
import org.scex.slashbladelegacy.contracts.RenderStallMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CompoundTag.class)
public abstract class RenderStallCustomDataMixin {
    @Inject(method="copy()Lnet/minecraft/nbt/CompoundTag;",at=@At("HEAD"))
    private void probe$copy(CallbackInfoReturnable<?> ci) {
        if(RenderStallMetrics.active())RenderStallMetrics.add(RenderStallMetrics.COPY_TAG,1);
    }
}
