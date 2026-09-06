package org.scex.slashbladelegacy.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.minecraft.world.item.ItemStack;
import org.scex.slashbladelegacy.LegacySheathingRepair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets="mods.flammpfeil.slashblade.event.handler.KillCounter",remap=false)
public abstract class LegacySheathingRepairMixin {
    @WrapOperation(method="lambda$onXPDropping$2",at=@At(value="INVOKE",target="Lmods/flammpfeil/slashblade/event/SlashBladeEvent$AddProudSoulEvent;getNewCount()I"))
    private static int legacyCompat$credit(SlashBladeEvent.AddProudSoulEvent event,Operation<Integer> original,
                                          @Local(argsOnly=true) ItemStack blade) {
        int amount=original.call(event);
        LegacySheathingRepair.credit(blade,amount);
        return amount;
    }
    @WrapOperation(method="lambda$onXPDropping$2",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;setDamageValue(I)V"))
    private static void legacyCompat$defer(ItemStack blade,int damage,Operation<Void> original) {
        if(!LegacySheathingRepair.handles(blade))original.call(blade,damage);
    }
}
