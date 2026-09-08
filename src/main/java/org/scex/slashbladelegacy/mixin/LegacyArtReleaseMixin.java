package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.scex.slashbladelegacy.LegacyArts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ItemSlashBlade.class,remap=false)
public abstract class LegacyArtReleaseMixin {
    @Inject(method="releaseUsing",at=@At("HEAD"),cancellable=true,require=1)
    private void legacyRelease(ItemStack blade,Level level,LivingEntity user,int timeLeft,CallbackInfo callback) {
        if(LegacyArts.release(blade,level,user,timeLeft))callback.cancel();
    }
}
