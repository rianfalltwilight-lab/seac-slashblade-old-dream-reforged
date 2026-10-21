package org.scex.slashbladelegacy.contracts.mixin;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import org.scex.slashbladelegacy.contracts.RenderStallMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=BladeStateAccess.class,remap=false)
public abstract class RenderStallStateMixin {
    @Inject(method="ensureComponent",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;set(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;",remap=true))
    private static void probe$persistent(CallbackInfo ci) {
        if(RenderStallMetrics.active())RenderStallMetrics.add(RenderStallMetrics.STATE_INIT,1);
    }
    @Inject(method="ensureRuntimeComponent",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;set(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;",remap=true))
    private static void probe$runtime(CallbackInfo ci) {
        if(RenderStallMetrics.active())RenderStallMetrics.add(RenderStallMetrics.RUNTIME_INIT,1);
    }
}
