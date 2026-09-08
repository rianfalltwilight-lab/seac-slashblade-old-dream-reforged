package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.event.RefineProgressEvent;
import mods.flammpfeil.slashblade.event.handler.RefineHandler;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import org.scex.slashbladelegacy.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RefineHandler.class)
public abstract class LegacyAnvilMixin {
    @Inject(method="onAnvilUpdateEvent",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$anvil(AnvilUpdateEvent event,CallbackInfo ci) {
        if(LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) && event.getLeft().getItem() instanceof ItemSlashBlade) {
            LegacyAnvil.update(event);ci.cancel();
        }
    }
    @Inject(method="refineLimitCheck",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$singleRefine(RefineProgressEvent event,CallbackInfo ci) {
        if(LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) && event.getBlade().getItem() instanceof ItemSlashBlade)ci.cancel();
    }
}
