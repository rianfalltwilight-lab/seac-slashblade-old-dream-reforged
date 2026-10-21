package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.scex.slashbladelegacy.LegacyArts;
import org.scex.slashbladelegacy.LegacyMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="cn.mmf.slashblade_addon.specialattacks.WaterDrive",remap=false)
public abstract class LegacyWaterDriveMixin {
    @Inject(method="doSlash(Lnet/minecraft/world/entity/LivingEntity;FIILnet/minecraft/world/phys/Vec3;ZDLmods/flammpfeil/slashblade/util/KnockBacks;F)Lmods/flammpfeil/slashblade/entity/EntityDrive;",at=@At("HEAD"),cancellable=true,require=1)
    private static void oldWaterBlade(LivingEntity user,float roll,int lifetime,int color,Vec3 offset,boolean critical,double damage,KnockBacks knockback,float speed,CallbackInfoReturnable<EntityDrive> result) {
        if(user instanceof Player player && LegacyMode.legacy(user))
            result.setReturnValue(user.level().isClientSide?null:LegacyArts.water(player,player.getMainHandItem()));
    }
}
