package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import org.scex.slashbladelegacy.LegacyAddonArtTiming;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets="cn.mmf.slashblade_addon.specialattacks.GaleSwords",remap=false)
public abstract class LegacyGaleTimingMixin {
    @Redirect(method="lambda$doSlash$0",at=@At(value="INVOKE",target="Lcn/mmf/slashblade_addon/entity/GaleSwordsEntity;setDelay(I)V"),require=1)
    private static void oldGaleDelay(@Coerce EntityAbstractSummonedSword sword,int delay) {
        LegacyAddonArtTiming.gale(sword,delay);
    }
}
