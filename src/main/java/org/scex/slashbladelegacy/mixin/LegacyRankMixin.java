package org.scex.slashbladelegacy.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import net.minecraft.world.damagesource.DamageSource;
import org.scex.slashbladelegacy.LegacyRank;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets="mods.flammpfeil.slashblade.event.handler.RankPointHandler",remap=false)
public abstract class LegacyRankMixin {
    @WrapOperation(method="onLivingHurtEvent",at=@At(value="INVOKE",target="Lmods/flammpfeil/slashblade/capability/concentrationrank/IConcentrationRank;addRankPoint(Lnet/minecraft/world/entity/LivingEntity;J)V"))
    private void legacyCompat$hurt(IConcentrationRank rank,net.minecraft.world.entity.LivingEntity victim,long amount,Operation<Void> original,net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre event) {
        if(org.scex.slashbladelegacy.LegacyMode.legacy(victim)
                && org.scex.slashbladelegacy.LegacyCompat.isEnabled(org.scex.slashbladelegacy.LegacyCompat.LEGACY_RANK))LegacyRank.hurt(victim,rank,event.getSource());
        else original.call(rank,victim,amount);
    }
    @WrapOperation(method="onLivingHurtEvent",at=@At(value="INVOKE",target="Lmods/flammpfeil/slashblade/capability/concentrationrank/IConcentrationRank;addRankPoint(Lnet/minecraft/world/damagesource/DamageSource;)V"))
    private void legacyCompat$award(IConcentrationRank rank,DamageSource source,Operation<Void> original) {
        if(!LegacyRank.award(rank,source))original.call(rank,source);
    }
}
