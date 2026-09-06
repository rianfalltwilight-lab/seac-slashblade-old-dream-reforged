package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.scex.slashbladelegacy.LegacyCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=ItemSlashBlade.class,remap=false)
public abstract class LegacyLeftClickMixin {
    @Inject(method="onLeftClickEntity",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$leftHitWindow(ItemStack stack,Player player,Entity entity,CallbackInfoReturnable<Boolean> cir) {
        if(!LegacyCompat.LEGACY_COMBAT.get() || !(entity instanceof LivingEntity target))return;
        var state=BladeStateAccess.of(stack).orElse(null);
        if(state==null || state.onClick() || !state.getComboRoot().equals(ComboStateRegistry.STANDBY.getId()))return;
        if(target.hurtDuration!=0 && target.hurtDuration-target.hurtTime<6)cir.setReturnValue(true);
    }
}
