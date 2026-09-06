package org.scex.slashbladelegacy.contracts;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import mods.flammpfeil.slashblade.SlashBladeConfig;
import mods.flammpfeil.slashblade.util.TargetSelector;
import org.scex.slashbladelegacy.LegacyCompat;

final class TargetAudit {
    static void run(ServerLevel level,Map<String,Object> report) {
        var candidates=new ArrayList<Object>();var failures=new ArrayList<Object>();var otherBossCandidates=new ArrayList<Object>();
        boolean friendly=SlashBladeConfig.FRIENDLY_ENABLE.get();
        SlashBladeConfig.FRIENDLY_ENABLE.set(false);
        try {
            for(var type:BuiltInRegistries.ENTITY_TYPE) {
                Entity entity=null;
                try {
                    entity=type.create(level);
                    if(!(entity instanceof LivingEntity living) || living instanceof Enemy)continue;
                    if(type.getCategory()!=MobCategory.MONSTER) {
                        boolean bossField=false;
                        for(Class<?> cls=living.getClass();cls!=null && cls!=LivingEntity.class;cls=cls.getSuperclass())
                            for(var field:cls.getDeclaredFields())if(net.minecraft.world.BossEvent.class.isAssignableFrom(field.getType()))bossField=true;
                        if(bossField || living.getMaxHealth()>=100)otherBossCandidates.add(Map.of("id",BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(),
                                "class",living.getClass().getName(),"category",type.getCategory().getName(),"health",living.getMaxHealth(),"bossField",bossField));
                        continue;
                    }
                    LegacyCompat.GAIA_TARGETING.set(false);LegacyCompat.HOSTILE_TARGETING.set(false);
                    boolean before=new TargetSelector.AttackablePredicate().test(living);
                    LegacyCompat.GAIA_TARGETING.set(true);LegacyCompat.HOSTILE_TARGETING.set(true);
                    boolean after=new TargetSelector.AttackablePredicate().test(living);
                    candidates.add(Map.of("id",BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(),
                            "class",living.getClass().getName(),"before",before,"after",after));
                } catch(Exception failure) {
                    failures.add(Map.of("id",BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(),"error",failure.toString()));
                }
                // These prototypes were never added to the world. Calling remove/discard can invoke
                // boss fight teardown (Gaia) on an uninitialized arena; allow ordinary GC instead.
            }
        } finally {
            SlashBladeConfig.FRIENDLY_ENABLE.set(friendly);
            LegacyCompat.GAIA_TARGETING.set(true);LegacyCompat.HOSTILE_TARGETING.set(true);
        }
        report.put("monster_without_enemy_interface",candidates);report.put("target_audit_creation_failures",failures);
        report.put("nonmonster_boss_candidates",otherBossCandidates);
    }
    static void copyRules(net.minecraft.server.level.ServerPlayer player,Map<String,Object> report) {
        var cow=EntityType.COW.create(player.level());cow.setPos(player.getX(),player.getY(),player.getZ()+2);
        boolean friendly=SlashBladeConfig.FRIENDLY_ENABLE.get();SlashBladeConfig.FRIENDLY_ENABLE.set(false);
        try {
            cow.setTarget(player);
            LegacyCompat.HOSTILE_TARGETING.set(false);
            if(TargetSelector.getAreaAttackPredicate(-1).test(player,cow))throw new AssertionError("Baseline copied hostility unexpectedly preserved");
            LegacyCompat.HOSTILE_TARGETING.set(true);
            if(!TargetSelector.getAreaAttackPredicate(-1).test(player,cow))throw new AssertionError("Attacking neutral mob excluded after copy");
            cow.setPos(player.getX(),player.getY(),player.getZ()+3); // Vanilla has a minimum targeting radius of two.
            if(TargetSelector.getAreaAttackPredicate(1).test(player,cow))throw new AssertionError("Copied range ignored");
            cow.setTarget(null);
            if(TargetSelector.getAreaAttackPredicate(-1).test(player,cow))throw new AssertionError("Peaceful neutral mob included");
            if(net.minecraft.world.entity.ai.targeting.TargetingConditions.forCombat().copy().getClass()!=net.minecraft.world.entity.ai.targeting.TargetingConditions.class)
                throw new AssertionError("Vanilla targeting copy changed type");
            cow.setTarget(player);cow.setHealth(0);
            if(TargetSelector.test.test(player,cow))throw new AssertionError("Dead target accepted");
            if(cow.getTags().contains("RevengeAttacker") || org.scex.slashbladelegacy.HostileTargeting.REVENGE_CONTEXT.get()!=null)
                throw new AssertionError("Early return leaked revenge state");
            cow.setHealth(10);cow.setTarget(null);
            if(new TargetSelector.AttackablePredicate().test(cow))throw new AssertionError("Revenge leaked across target checks");
            report.put("copied_hostility_range_neutral_rules",true);
            report.put("revenge_early_return_isolation",true);
        }finally{SlashBladeConfig.FRIENDLY_ENABLE.set(friendly);LegacyCompat.HOSTILE_TARGETING.set(true);cow.discard();}
    }
}
