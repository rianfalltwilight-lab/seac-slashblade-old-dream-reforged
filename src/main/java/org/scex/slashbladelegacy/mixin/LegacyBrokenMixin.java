package org.scex.slashbladelegacy.mixin;

import java.util.function.Consumer;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.scex.slashbladelegacy.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemSlashBlade.class)
public abstract class LegacyBrokenMixin {
    @Inject(method="getOnBroken",at=@At("HEAD"),cancellable=true)
    private static void legacyCompat$broken(ItemStack blade,CallbackInfoReturnable<Consumer<LivingEntity>> cir) {
        if(org.scex.slashbladelegacy.LegacyMode.legacy(blade))cir.setReturnValue(user->LegacyBroken.reward(blade,user));
    }
}
