package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.scex.slashbladelegacy.LegacyArts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=SlashArts.class,remap=false)
public abstract class LegacyArtsMixin {
    @Inject(method="doArts",at=@At("HEAD"),cancellable=true,require=1)
    private void legacyArts(SlashArts.ArtsType type,LivingEntity user,CallbackInfoReturnable<ResourceLocation> result) {
        var combo=LegacyArts.select((SlashArts)(Object)this,type,user);if(combo!=null)result.setReturnValue(combo);
    }
}
