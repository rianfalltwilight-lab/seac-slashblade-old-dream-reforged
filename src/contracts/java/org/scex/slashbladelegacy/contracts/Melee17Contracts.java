package org.scex.slashbladelegacy.contracts;

import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import org.scex.slashbladelegacy.*;

final class Melee17Contracts {
    static void run(ServerPlayer player,Map<String,Object> report) {
        var original=player.getMainHandItem();var blade=new ItemStack(SlashBladeItems.SLASHBLADE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.setOnGround(true);player.fallDistance=0;player.setSprinting(false);player.removeAllEffects();
        var state=BladeStateAccess.of(blade).orElseThrow();state.setComboSeq(net.minecraft.resources.ResourceLocation.parse("slashblade:judgement_cut"));
        var target=EntityType.ZOMBIE.create(player.level());target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.setPos(player.position().add(0,0,12));
        var nested=EntityType.ZOMBIE.create(player.level());nested.getAttribute(Attributes.ARMOR).setBaseValue(0);nested.setPos(target.position().add(1,0,0));
        var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);long unit=rank.getUnitCapacity();
        try {
            resetRank(player);blade.enchant(player.registryAccess().holderOrThrow(Enchantments.FIRE_ASPECT),1);
            int wear=blade.getDamageValue(),fire=target.getRemainingFireTicks();target.invulnerableTime=17;
            Consumer<AttackEntityEvent> attackCancel=e->{if(e.getEntity()==player && e.getTarget()==target)e.setCanceled(true);};
            NeoForge.EVENT_BUS.addListener(attackCancel);
            try {AttackManager.doMeleeAttack(player,target,true,true,2);} finally {NeoForge.EVENT_BUS.unregister(attackCancel);}
            check(target.getHealth()==20 && target.invulnerableTime==17 && blade.getDamageValue()==wear && rank.getRawRankPoint()==0,"SA AttackEntity cancellation preserves invulnerability, wear and rank");
            Consumer<LivingIncomingDamageEvent> damageCancel=e->{if(e.getEntity()==target)e.setCanceled(true);};
            NeoForge.EVENT_BUS.addListener(damageCancel);
            try {
                AttackManager.doMeleeAttack(player,target,true,true,2);
                check(target.getHealth()==20 && target.invulnerableTime==17 && target.getRemainingFireTicks()==fire && blade.getDamageValue()==wear && rank.getRawRankPoint()==0,"SA hurt cancellation rolls back preliminary changes");
                check(!LegacyDamage.hit(player,target,LegacyMove.SAYA1,target.getBoundingBox(),true) && target.invulnerableTime==17 && rank.getRawRankPoint()==0,"scabbard cancellation also retains target timer");
            } finally {NeoForge.EVENT_BUS.unregister(damageCancel);}
            Consumer<LivingIncomingDamageEvent> replacement=e->{if(e.getEntity()==target){e.getEntity().invulnerableTime=37;e.getEntity().setRemainingFireTicks(60);e.setCanceled(true);}};
            NeoForge.EVENT_BUS.addListener(replacement);
            try {
                AttackManager.doMeleeAttack(player,target,true,true,2);
                check(target.invulnerableTime==37 && target.getRemainingFireTicks()==60,"protection listener's new timers survive canceled melee");
                target.invulnerableTime=17;
                check(!LegacyProjectileDamage.strike(target,player,blade,target,3,false,0,false) && target.invulnerableTime==37,"protection listener's new timer survives canceled projectile");
            } finally {NeoForge.EVENT_BUS.unregister(replacement);}
            target.clearFire();resetRank(player);
            AttackManager.doMeleeAttack(player,target,true,true,2);
            check(target.getHealth()==14 && blade.getDamageValue()==wear+1 && target.invulnerableTime==0,"accepted SA retains explicit coefficient and requested reset");
            check(rank.getRawRankPoint()==60*unit/100,"foreign combo uses actual dimension hit award once");
            target.setHealth(20);target.clearFire();resetRank(player);
            Consumer<LivingIncomingDamageEvent> nestedHit=e->{if(e.getEntity()==target)LegacyDamage.hit(player,nested,LegacyMove.BATTOU,nested.getBoundingBox(),true);};
            NeoForge.EVENT_BUS.addListener(nestedHit);
            try {AttackManager.doMeleeAttack(player,target,true,true,1);} finally {NeoForge.EVENT_BUS.unregister(nestedHit);}
            check(nested.getHealth()==17 && target.getHealth()==17 && rank.getRawRankPoint()==110*unit/100,"nested callback restores each exact damage source and move");
            report.put("legacy17_melee_protection",Map.of("attack_cancel",true,"hurt_cancel",true,"scabbard_cancel",true,"listener_timer_preserved",true,"projectile_timer_preserved",true,"sa_award_points",60,"nested_award_points",110));
        } finally {target.discard();nested.discard();player.setItemInHand(InteractionHand.MAIN_HAND,original);}
    }
    private static void resetRank(ServerPlayer player) {
        var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);rank.setRawRankPoint(0);rank.setLastUpdte(player.level().getGameTime());
        for(var key:new ArrayList<>(player.getPersistentData().getAllKeys()))if(key.startsWith("slashblade_legacy_compat.rank_cd."))player.getPersistentData().remove(key);
        LegacyDamage.update(player);
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError("r87 melee: "+message);}
}
