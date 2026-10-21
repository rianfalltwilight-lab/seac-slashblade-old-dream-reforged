package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraftforge.event.AnvilUpdateEvent;
import org.scex.slashbladelegacy.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets={"mods.flammpfeil.slashblade.event.handler.RefineHandler",
        "mods.flammpfeil.slashblade.event.RefineHandler"},remap=false)
public abstract class LegacyAnvilMixin {
    @Inject(method="onAnvilUpdateEvent",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$anvil(AnvilUpdateEvent event,CallbackInfo ci) {
        if(org.scex.slashbladelegacy.LegacyMode.legacy(event.getPlayer()) && event.getLeft().getItem() instanceof ItemSlashBlade) {
            LegacyAnvil.update(event);ci.cancel();
        }
    }
}
