package org.scex.slashbladelegacy.mixin;

import net.minecraft.world.item.*;
import net.minecraft.world.entity.LivingEntity;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import org.scex.slashbladelegacy.LegacyCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ItemSlashBlade.hurtEnemy already consumes one durability; 1.21 SwordItem adds another after it. */
@Mixin(SwordItem.class)
public abstract class LegacyPostHurtMixin {
    @Inject(method="postHurtEnemy",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$oneWear(ItemStack blade,LivingEntity target,LivingEntity attacker,CallbackInfo ci) {
        if(blade.getItem() instanceof ItemSlashBlade && LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT)
                && BladeStateAccess.of(blade).map(state->state.onClick() && state.getComboRoot().equals(ComboStateRegistry.STANDBY.getId())).orElse(false))ci.cancel();
    }
}
