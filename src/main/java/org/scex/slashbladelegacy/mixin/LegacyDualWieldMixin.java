package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.scex.slashbladelegacy.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=ItemSlashBlade.class,remap=false)
public abstract class LegacyDualWieldMixin {
    @Inject(method="use",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$mainInput(Level level,Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if(org.scex.slashbladelegacy.LegacyMode.legacy(player) && hand==InteractionHand.OFF_HAND)
            cir.setReturnValue(InteractionResultHolder.fail(player.getOffhandItem()));
    }
    @Inject(method="hurtEnemy",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$borrowedHand(ItemStack blade,LivingEntity target,LivingEntity user,CallbackInfoReturnable<Boolean> cir) {
        if(LegacyDualWield.hurtEnemy(blade,target,user))cir.setReturnValue(true);
    }
}
