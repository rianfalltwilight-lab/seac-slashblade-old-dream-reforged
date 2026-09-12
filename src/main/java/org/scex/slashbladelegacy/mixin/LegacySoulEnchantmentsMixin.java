package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.event.bladestand.BlandStandEventHandler;
import org.scex.slashbladelegacy.LegacyCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** r87 copies SA on a capped enchantment; a fresh enchanted ingot first upgrades the blade. */
@Mixin(BlandStandEventHandler.class)
public abstract class LegacySoulEnchantmentsMixin {
    @Inject(method={"eventProudSoulEnchantment","eventCopySA"},at=@At("HEAD"),cancellable=true)
    private static void legacyCompat$oldSoulOperation(SlashBladeEvent.BladeStandAttackEvent event,CallbackInfo ci) {
        if(org.scex.slashbladelegacy.LegacyMode.legacy(event.getDamageSource().getEntity()))ci.cancel();
    }
}
