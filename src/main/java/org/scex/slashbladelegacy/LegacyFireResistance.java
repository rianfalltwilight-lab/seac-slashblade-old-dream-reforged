package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/** r87 FireResistance: active use, burning acceleration, then extinguish after charge. */
public final class LegacyFireResistance {
    private LegacyFireResistance() {}
    public static void tick(LivingEntityUseItemEvent.Tick event) {
        if(!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) || !(event.getEntity() instanceof Player player)
                || !player.isUsingItem() || event.getItem()!=player.getMainHandItem()
                || BladeStateAccess.of(event.getItem()).isEmpty())return;
        int level=event.getItem().getEnchantmentLevel(player.registryAccess().holderOrThrow(Enchantments.FIRE_PROTECTION));
        if(level<=0)return;
        if(player.isOnFire()) {
            player.moveRelative(.25f+.05f*level,new Vec3(player.xxa,0,player.zza));
            if(event.getItem().getUseDuration(player)-event.getDuration()>15)player.setRemainingFireTicks(0);
        }
        if(!player.level().isClientSide)player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,2,level-1,true,true));
    }
}
