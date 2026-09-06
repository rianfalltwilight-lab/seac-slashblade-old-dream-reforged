package org.scex.slashbladelegacy.contracts;

import java.util.Map;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import org.scex.slashbladelegacy.*;

final class TauntContracts {
    static void run(ServerPlayer player,Map<String,Object> report) {
        var saved=player.getMainHandItem();var pos=player.position();var blade=saved.copy();
        var state=BladeStateAccess.of(blade).orElseThrow();
        state.setComboRoot(ComboStateRegistry.STANDBY.getId());
        var level=player.serverLevel();var near=EntityType.ZOMBIE.create(level);var far=EntityType.ZOMBIE.create(level);
        player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.setPos(64,180,0);player.setOnGround(true);player.setShiftKeyDown(false);
        // Fixture entities must belong to loaded chunks before the synchronous server-start test.
        level.getChunk(4,0);level.getChunk(3,0);level.getChunk(4,-1);level.getChunk(3,-1);
        near.setPos(72,180,0);far.setPos(76,180,0);level.addFreshEntity(near);level.addFreshEntity(far);
        try {
            var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);rank.setRawRankPoint(0);
            LegacyCompat.SHEATHING_REPAIR.set(false);
            state.updateComboSeq(player,LegacyCombat.id(LegacyMove.NOUTOU));
            state.setLastActionTime(level.getGameTime()-9);state.resolvCurrentComboState(player);LegacySheathingRepair.tick(player);
            require(near.getTarget()==null,"Taunt fired before completed sheath");
            state.setLastActionTime(level.getGameTime()-11);state.resolvCurrentComboState(player);LegacySheathingRepair.tick(player);
            require(near.getTarget()==player,"Completed sheath failed to taunt beyond melee range: handles="+LegacyTaunt.handles(blade)
                    +", sword="+mods.flammpfeil.slashblade.item.SwordType.from(blade)+", combo="+state.getComboSeq()
                    +", targeting="+mods.flammpfeil.slashblade.util.TargetSelector.test.copy().range(0).test(player,near)
                    +", LOS="+player.hasLineOfSight(near)+"/"+near.hasLineOfSight(player)
                    +", present="+level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,player.getBoundingBox().inflate(10,5,10)).contains(near));
            require(far.getTarget()==null,"Taunt exceeded horizontal bounds");
            require(near.getEffect(MobEffects.DAMAGE_BOOST)!=null && near.getEffect(MobEffects.DAMAGE_BOOST).getAmplifier()==1
                    && near.getEffect(MobEffects.DAMAGE_BOOST).getDuration()==600,"Strength II duration mismatch");
            require(near.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier()==1 && near.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier()==0,"Taunt buff levels mismatch");
            require(near.getPersistentData().getInt("slashblade_legacy_compat.taunt_level")==1,"Taunt count mismatch");
            require(rank.getRawRankPoint()>0,"Successful taunt did not award rank");
            LegacySheathingRepair.tick(player);
            require(near.getPersistentData().getInt("slashblade_legacy_compat.taunt_level")==1,"Repeated tick duplicated taunt");
            near.setTarget(null);player.setOnGround(false);finish(player);
            require(near.getTarget()==null,"Airborne sheath taunted");
            player.setOnGround(true);player.setShiftKeyDown(true);finish(player);
            require(near.getTarget()==null,"Crouching sheath taunted");
            player.setShiftKeyDown(false);LegacyCompat.LEGACY_TAUNT.set(false);finish(player);
            require(near.getTarget()==null,"Disabled taunt fired");
            LegacyCompat.LEGACY_TAUNT.set(true);
            for(int i=0;i<6;i++)finish(player);
            require(near.getPersistentData().getInt("slashblade_legacy_compat.taunt_level")==5,"Taunt level failed to cap at five");
            var xp=new net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent(near,player,7);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(xp);
            require(xp.getDroppedExperience()==32,"Taunt XP bonus mismatch");
            report.put("legacy_taunt",Map.of("completed_sheath",true,"repair_disabled_independent",true,"range_8_in_12_out",true,"buff_ticks",600,"once_only",true,"air_crouch_config_rejected",true));
        } finally {
            near.discard();far.discard();LegacyCompat.SHEATHING_REPAIR.set(true);LegacyCompat.LEGACY_TAUNT.set(true);
            player.setShiftKeyDown(false);player.setPos(pos);player.setItemInHand(InteractionHand.MAIN_HAND,saved);
        }
    }
    private static void finish(ServerPlayer player) {
        var state=BladeStateAccess.of(player.getMainHandItem()).orElseThrow();state.updateComboSeq(player,LegacyCombat.id(LegacyMove.NOUTOU));
        state.setLastActionTime(player.level().getGameTime()-11);state.resolvCurrentComboState(player);LegacySheathingRepair.tick(player);
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
