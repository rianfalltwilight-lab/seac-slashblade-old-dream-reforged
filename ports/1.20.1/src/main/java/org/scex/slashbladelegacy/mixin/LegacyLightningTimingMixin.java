package org.scex.slashbladelegacy.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import org.scex.slashbladelegacy.LegacyAddonArtTiming;
import org.scex.slashbladelegacy.LegacyAddonCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets="cn.mmf.slashblade_addon.specialattacks.LightingSwords",remap=false)
public abstract class LegacyLightningTimingMixin {
    // Select the spawn callback by its parameters. Lambda numbers can change.
    @WrapOperation(method="*"+LegacyAddonCompatibility.LIGHTNING_DESCRIPTOR,at=@At(value="INVOKE",target="Lcn/mmf/slashblade_addon/entity/BlisteringSwordsEntity;setDelay(I)V"),require=1,expect=1,allow=1)
    private static void oldCloudDelay(@Coerce EntityAbstractSummonedSword sword,int delay,Operation<Void> original) {
        original.call(sword,LegacyAddonArtTiming.lightning(sword,delay,false));
    }
    @WrapOperation(method="*"+LegacyAddonCompatibility.LIGHTNING_DESCRIPTOR,at=@At(value="INVOKE",target="Lcn/mmf/slashblade_addon/entity/LightingSwordEntity;setDelay(I)V"),require=1,expect=1,allow=1)
    private static void oldThunderDelay(@Coerce EntityAbstractSummonedSword sword,int delay,Operation<Void> original) {
        original.call(sword,LegacyAddonArtTiming.lightning(sword,delay,true));
    }
}
