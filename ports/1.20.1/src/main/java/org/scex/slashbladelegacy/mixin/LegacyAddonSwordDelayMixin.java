package org.scex.slashbladelegacy.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import org.scex.slashbladelegacy.LegacyAddonArtTiming;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets="cn.mmf.slashblade_addon.entity.BlisteringSwordsEntity",remap=false)
public abstract class LegacyAddonSwordDelayMixin {
    // Keep the first delay. Remove the second delay only from marked legacy swords.
    @WrapOperation(method={"rideTick()V","m_6083_()V"},at=@At(value="INVOKE",target="Lcn/mmf/slashblade_addon/entity/BlisteringSwordsEntity;getDelay()I",ordinal=1),require=1,expect=1,allow=1)
    private int onlyOneDelay(@Coerce EntityAbstractSummonedSword sword,Operation<Integer> original) {
        return LegacyAddonArtTiming.repeatedDelay(sword,original.call(sword));
    }
}
