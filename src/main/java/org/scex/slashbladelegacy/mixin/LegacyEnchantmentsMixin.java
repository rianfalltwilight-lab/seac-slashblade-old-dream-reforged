package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.scex.slashbladelegacy.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemSlashBlade.class)
public abstract class LegacyEnchantmentsMixin {
    @Inject(method="supportsEnchantment",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$support(ItemStack blade,Holder<Enchantment> enchantment,CallbackInfoReturnable<Boolean> cir) {
        if(LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) && LegacyEnchantments.vanilla(enchantment))
            cir.setReturnValue(LegacyEnchantments.sword(enchantment) || LegacyEnchantments.rare(enchantment));
    }
    @Inject(method="isPrimaryItemFor",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$table(ItemStack blade,Holder<Enchantment> enchantment,CallbackInfoReturnable<Boolean> cir) {
        if(LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) && LegacyEnchantments.vanilla(enchantment))
            cir.setReturnValue(LegacyEnchantments.sword(enchantment));
    }
}
