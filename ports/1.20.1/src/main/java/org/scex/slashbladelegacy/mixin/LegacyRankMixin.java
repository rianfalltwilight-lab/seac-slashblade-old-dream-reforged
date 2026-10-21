package org.scex.slashbladelegacy.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullConsumer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.scex.slashbladelegacy.LegacyCompat;
import org.scex.slashbladelegacy.LegacyMode;
import org.scex.slashbladelegacy.LegacyRank;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets={"mods.flammpfeil.slashblade.event.handler.RankPointHandler",
        "mods.flammpfeil.slashblade.event.RankPointHandler"},remap=false)
public abstract class LegacyRankMixin {
    @WrapOperation(method="onLivingDeathEvent",at=@At(value="INVOKE",target="Lnet/minecraftforge/common/util/LazyOptional;ifPresent(Lnet/minecraftforge/common/util/NonNullConsumer;)V",ordinal=0))
    private void legacyCompat$hurt(LazyOptional<IConcentrationRank> rank,NonNullConsumer<IConcentrationRank> action,Operation<Void> original,LivingHurtEvent event) {
        var victim=event.getEntity();
        if(LegacyMode.legacy(victim) && LegacyCompat.isEnabled(LegacyCompat.LEGACY_RANK))
            rank.ifPresent(value->LegacyRank.hurt(victim,value,event.getSource()));
        else original.call(rank,action);
    }
    @WrapOperation(method="*(Lnet/minecraftforge/event/entity/living/LivingHurtEvent;Lmods/flammpfeil/slashblade/capability/concentrationrank/IConcentrationRank;)V",at=@At(value="INVOKE",target="Lmods/flammpfeil/slashblade/capability/concentrationrank/IConcentrationRank;addRankPoint(Lnet/minecraft/world/damagesource/DamageSource;)V"),require=1,expect=1,allow=1)
    private static void legacyCompat$award(IConcentrationRank rank,DamageSource source,Operation<Void> original) {
        if(!LegacyRank.award(rank,source))original.call(rank,source);
    }
}
