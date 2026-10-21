package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import org.scex.slashbladelegacy.LegacyAddonArtTiming;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets="cn.mmf.slashblade_addon.specialattacks.LightingSwords",remap=false)
public abstract class LegacyLightningTimingMixin {
    @Redirect(method="lambda$doSlash$1",at=@At(value="INVOKE",target="Lcn/mmf/slashblade_addon/entity/BlisteringSwordsEntity;setDelay(I)V"),require=1)
    private static void oldCloudDelay(@Coerce EntityAbstractSummonedSword sword,int delay) {
        LegacyAddonArtTiming.lightning(sword,delay,false);
    }
    @Redirect(method="lambda$doSlash$1",at=@At(value="INVOKE",target="Lcn/mmf/slashblade_addon/entity/LightingSwordEntity;setDelay(I)V"),require=1)
    private static void oldThunderDelay(@Coerce EntityAbstractSummonedSword sword,int delay) {
        LegacyAddonArtTiming.lightning(sword,delay,true);
    }
}
