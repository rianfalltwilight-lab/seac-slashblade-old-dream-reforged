package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import org.scex.slashbladelegacy.LegacyAddonArtTiming;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets="cn.mmf.slashblade_addon.entity.BlisteringSwordsEntity",remap=false)
public abstract class LegacyAddonSwordDelayMixin {
    // The first getDelay is the standby threshold; the second incorrectly adds the same delay again.
    @Redirect(method="rideTick",at=@At(value="INVOKE",target="Lcn/mmf/slashblade_addon/entity/BlisteringSwordsEntity;getDelay()I",ordinal=1),require=1)
    private int onlyOneDelay(@Coerce EntityAbstractSummonedSword sword) {
        return LegacyAddonArtTiming.repeatedDelay(sword);
    }
}
