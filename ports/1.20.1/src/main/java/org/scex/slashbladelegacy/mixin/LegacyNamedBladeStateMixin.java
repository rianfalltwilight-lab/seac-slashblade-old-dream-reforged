package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.capability.slashblade.SlashBladeState;
import net.minecraft.world.item.ItemStack;
import org.scex.slashbladelegacy.LegacyNamedBladeStackAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the source stack available for mode-specific art selection. */
@Mixin(value = SlashBladeState.class, remap = false)
public abstract class LegacyNamedBladeStateMixin implements LegacyNamedBladeStackAccessor {
    @Unique private ItemStack legacy$blade;
    @Inject(method = "<init>(Lnet/minecraft/world/item/ItemStack;)V", at = @At("RETURN"))
    private void legacy$source(ItemStack blade, CallbackInfo callback) { legacy$blade = blade; }
    @Override public ItemStack legacyCompat$blade() { return legacy$blade; }
}
