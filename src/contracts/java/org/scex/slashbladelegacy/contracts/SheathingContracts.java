package org.scex.slashbladelegacy.contracts;

import java.util.Map;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import org.scex.slashbladelegacy.*;

final class SheathingContracts {
    static void run(ServerPlayer player,Map<String,Object> report) {
        var saved=player.getMainHandItem();var blade=saved.copy();
        blade.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        var state=BladeStateAccess.of(blade).orElseThrow();
        state.setComboRoot(ComboStateRegistry.STANDBY.getId());state.setProudSoulCount(1000);
        player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.setOnGround(false);
        var target=EntityType.ZOMBIE.create(player.level());
        target.setPos(player.position());player.serverLevel().addFreshEntity(target);
        try {
            blade.setDamageValue(30);
            finish(player);require(blade.getDamageValue()==30,"Empty sheath repaired without a kill");
            int souls=state.getProudSoulCount();
            target.hurt(player.damageSources().playerAttack(player),1000);
            int earned=state.getProudSoulCount()-souls;
            require(earned>0 && blade.getDamageValue()==30,"XP must credit souls once and defer repair");
            state.updateComboSeq(player,LegacyCombat.id(LegacyMove.NOUTOU));
            state.setLastActionTime(player.level().getGameTime()-9);
            state.resolvCurrentComboState(player);LegacySheathingRepair.tick(player);
            require(blade.getDamageValue()==30,"Sheath repaired before timeout");
            state.setLastActionTime(player.level().getGameTime()-11);
            state.resolvCurrentComboState(player);LegacySheathingRepair.tick(player);
            require(blade.getDamageValue()==30-earned && state.getProudSoulCount()==souls+earned,"Full earned repair or duplicate souls");
            finish(player);require(blade.getDamageValue()==30-earned,"Repeated sheath reused credit");
            blade.setDamageValue(30);state.setProudSoulCount(0);
            NeoForge.EVENT_BUS.post(new LivingExperienceDropEvent(target,player,0));finish(player);
            require(blade.getDamageValue()==30,"Sub-1000 soul blade repaired");
            state.setProudSoulCount(1000);NeoForge.EVENT_BUS.post(new LivingExperienceDropEvent(target,player,0));finish(player);
            require(blade.getDamageValue()==29,"Zero XP kill minimum repair missing");
            blade.setDamageValue(30);NeoForge.EVENT_BUS.post(new LivingExperienceDropEvent(target,player,5));
            state.updateComboSeq(player,LegacyCombat.id(LegacyMove.NOUTOU));player.setPos(player.getX()+1,player.getY(),player.getZ());
            state.setLastActionTime(player.level().getGameTime()-11);state.resolvCurrentComboState(player);LegacySheathingRepair.tick(player);
            require(blade.getDamageValue()==30,"Moving sheath repaired");
            // Credit is tied to the blade's serialized data, not the next item in the player's hand.
            var copy=blade.copy();player.setItemInHand(InteractionHand.MAIN_HAND,copy);finish(player);
            require(copy.getDamageValue()<30 && blade.getDamageValue()==30,"Copied/saveable blade credit not settled on held stack");
            player.setItemInHand(InteractionHand.MAIN_HAND,blade);
            java.util.function.Consumer<mods.flammpfeil.slashblade.event.BladeMotionEvent> cancel=event->{
                if(event.getEntity()==player && event.getCombo().equals(ComboStateRegistry.NONE.getId()))event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(cancel);
            try {finish(player);require(blade.getDamageValue()==30,"Canceled sheath repaired");}
            finally {NeoForge.EVENT_BUS.unregister(cancel);}
            state.setProudSoulCount(10000); // Native Resharpened threshold differs from legacy's 1000.
            LegacyCompat.SHEATHING_REPAIR.set(false);
            int before=blade.getDamageValue();NeoForge.EVENT_BUS.post(new LivingExperienceDropEvent(target,player,5));
            require(blade.getDamageValue()<before,"Disabled config must preserve native instant repair");
            report.put("legacy_sheathing_repair",Map.of("no_empty_repair",true,"deferred_full_credit",earned,
                    "soul_award_once",true,"threshold",1000,"zero_xp_minimum",1,"moving_rejected",true,"serialized_credit",true,"native_fallback",true));
        } finally {target.discard();LegacyCompat.SHEATHING_REPAIR.set(true);player.setItemInHand(InteractionHand.MAIN_HAND,saved);LegacySheathingRepair.tick(player);}
    }
    private static void finish(ServerPlayer player) {
        var state=BladeStateAccess.of(player.getMainHandItem()).orElseThrow();
        state.updateComboSeq(player,LegacyCombat.id(LegacyMove.NOUTOU));
        state.setLastActionTime(player.level().getGameTime()-11);
        state.resolvCurrentComboState(player);LegacySheathingRepair.tick(player);
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
