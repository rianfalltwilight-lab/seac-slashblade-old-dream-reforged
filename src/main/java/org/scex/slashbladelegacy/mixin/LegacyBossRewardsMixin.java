package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.scex.slashbladelegacy.LegacyBossRewards;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemSlashBlade.class)
public abstract class LegacyBossRewardsMixin {
    @Inject(method="hurtEnemy",at=@At("RETURN"))
    private void legacyCompat$bossReward(ItemStack blade,LivingEntity target,LivingEntity user,CallbackInfoReturnable<Boolean> cir) {
        if(cir.getReturnValue())LegacyBossRewards.hit(blade,target,user);
    }
}
