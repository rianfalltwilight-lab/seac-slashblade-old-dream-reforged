package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.event.RefineProgressEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import org.scex.slashbladelegacy.LegacyMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Apply the separate refine check only when upstream supplies it. */
@Pseudo
@Mixin(targets={"mods.flammpfeil.slashblade.event.handler.RefineHandler",
        "mods.flammpfeil.slashblade.event.RefineHandler"},remap=false)
public abstract class LegacyRefineLimitMixin {
    @Inject(method="refineLimitCheck(Lmods/flammpfeil/slashblade/event/RefineProgressEvent;)V",at=@At("HEAD"),cancellable=true,require=1)
    private void legacyRefineLimit(RefineProgressEvent event,CallbackInfo ci) {
        if(LegacyMode.legacy(event.getBlade()) && event.getBlade().getItem() instanceof ItemSlashBlade)ci.cancel();
    }
}
