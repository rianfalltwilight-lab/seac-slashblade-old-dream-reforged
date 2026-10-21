package org.scex.slashbladelegacy.mixin;

import java.util.function.Consumer;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import org.scex.slashbladelegacy.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class LegacyDurabilityMixin {
    @Inject(method="hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$durability(int amount,LivingEntity user,Consumer<LivingEntity> onBroken,CallbackInfo ci) {
        var blade=(ItemStack)(Object)this;
        if(blade.getItem() instanceof ItemSlashBlade && org.scex.slashbladelegacy.LegacyMode.legacy(user)) {
            LegacyDurability.hurt(blade,amount,user,onBroken);ci.cancel();
        }
    }
    @Inject(method="hurt(ILnet/minecraft/util/RandomSource;Lnet/minecraft/server/level/ServerPlayer;)Z",
            at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;getDamageValue()I"),cancellable=true,require=2,expect=2,allow=2)
    private void legacyCompat$afterProtection(int amount,RandomSource random,ServerPlayer player,CallbackInfoReturnable<Boolean> cir) {
        if(LegacyDurability.applyPending((ItemStack)(Object)this,amount))cir.setReturnValue(false);
    }
}
