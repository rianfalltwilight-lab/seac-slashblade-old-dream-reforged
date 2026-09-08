package org.scex.slashbladelegacy.mixin;

import java.util.function.Consumer;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import org.scex.slashbladelegacy.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public abstract class LegacyDurabilityMixin {
    @Inject(method="hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$durability(int amount,ServerLevel level,LivingEntity user,Consumer<Item> onBroken,CallbackInfo ci) {
        var blade=(ItemStack)(Object)this;
        if(blade.getItem() instanceof ItemSlashBlade && LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT)) {
            LegacyDurability.hurt(blade,amount,level,user,onBroken);ci.cancel();
        }
    }
}
