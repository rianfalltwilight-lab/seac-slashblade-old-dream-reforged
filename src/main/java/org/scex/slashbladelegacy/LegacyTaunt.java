package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.ability.StunManager;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.item.SwordType;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.TargetSelector;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

public final class LegacyTaunt {
    private static final String LEVEL="slashblade_legacy_compat.taunt_level";
    private LegacyTaunt(){}
    public static boolean handles(ItemStack blade) {
        return LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) && LegacyCompat.isEnabled(LegacyCompat.LEGACY_TAUNT) && blade.getItem() instanceof ItemSlashBlade
                && BladeStateAccess.of(blade).isPresent()
                && !SwordType.from(blade).contains(SwordType.NOSCABBARD);
    }
    public static void fire(Player player,ItemStack blade) {
        if(!(player.level() instanceof ServerLevel level) || !handles(blade) || !player.onGround()
                || player.isCrouching() || player.isShiftKeyDown())return;
        // Keep hostile/owner/team/blacklist tests, but not the sword's much shorter melee reach.
        var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);
        int count=0;
        for(var mob:level.getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(10,5,10))) {
            if(!mob.isAlive() || !LegacyTargets.attackable(player,mob) || !player.hasLineOfSight(mob) || !mob.hasLineOfSight(player))continue;
            mob.setTarget(player);
            if(mob.getTarget()!=player)continue; // Respect target-change cancellation/AI ownership rules.
            mob.setLastHurtByMob(player);
            mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,600,1));
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,600,1));
            // User-specified Resistance I; do not port the old negative-amplifier bug or strip stronger buffs.
            mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,600,0));
            int taunt=Math.min(5,Math.max(0,mob.getPersistentData().getInt(LEVEL))+1);
            mob.getPersistentData().putInt(LEVEL,taunt);
            if(taunt<=2)StunManager.setStun(mob,10);
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER,mob.getX(),mob.getY(),mob.getZ(),5,mob.getBbWidth()*2,mob.getBbHeight(),mob.getBbWidth()*2,.02);
            if(count++<3)level.playSound(null,mob.blockPosition(),SoundEvents.FIRECHARGE_USE,SoundSource.PLAYERS,.5f,1);
            LegacyRank.awardAction(player,rank,"Taunt",.1f);
        }
        if(count>0) {
            LegacyRank.awardAction(player,rank,"Noutou",-1.5f);
            level.sendParticles(ParticleTypes.CRIT,player.getX(),player.getY()+1,player.getZ(),10,.4,.6,.4,.02);
        }
    }
    public static void experience(LivingExperienceDropEvent event) {
        if(!LegacyCompat.isEnabled(LegacyCompat.LEGACY_TAUNT))return;
        int taunt=Math.clamp(event.getEntity().getPersistentData().getInt(LEVEL),0,5);
        if(taunt>0)event.setDroppedExperience((int)Math.min(Integer.MAX_VALUE,(long)Math.max(0,event.getDroppedExperience())+taunt*5L));
    }
}
