package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.scex.slashbladelegacy.LegacyJustGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=ItemSlashBlade.class, remap=false)
public abstract class LegacyUseAbilitiesMixin {
    @Inject(method="use", at=@At("RETURN"))
    private void legacyCompat$beginGuard(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> result) {
        if (hand == InteractionHand.MAIN_HAND && player.isUsingItem() && player.getUsedItemHand() == hand)
            LegacyJustGuard.begin(player, player.getMainHandItem());
    }
}
