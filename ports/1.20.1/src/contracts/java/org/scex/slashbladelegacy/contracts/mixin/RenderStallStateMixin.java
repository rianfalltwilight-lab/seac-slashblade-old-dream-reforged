package org.scex.slashbladelegacy.contracts.mixin;

import mods.flammpfeil.slashblade.capability.slashblade.SlashBladeState;
import org.scex.slashbladelegacy.contracts.RenderStallMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Count lazy capability construction. This version has no data components. */
@Mixin(value=SlashBladeState.class,remap=false)
public abstract class RenderStallStateMixin {
    @Inject(method="<init>(Lnet/minecraft/world/item/ItemStack;)V",at=@At("RETURN"))
    private void probe$initialized(CallbackInfo ci) {
        if(RenderStallMetrics.active())RenderStallMetrics.add(RenderStallMetrics.STATE_INIT,1);
    }
}
