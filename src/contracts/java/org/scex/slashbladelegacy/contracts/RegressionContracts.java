package org.scex.slashbladelegacy.contracts;

import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.neoforged.neoforge.common.NeoForge;
import org.scex.slashbladelegacy.*;

final class RegressionContracts {
    static void run(ServerPlayer player,Map<String,Object> report) {
        var level=player.serverLevel();var saved=player.getMainHandItem();int savedLevel=player.experienceLevel;
        var blade=saved.copy();var state=BladeStateAccess.of(blade).orElseThrow();
        state.setSpecialEffects(new net.minecraft.nbt.ListTag());state.setComboRoot(ComboStateRegistry.STANDBY.getId());
        state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(level.getGameTime());
        player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.setPos(24,160,0);player.setYRot(0);player.setXRot(0);player.setOnGround(true);
        player.getData(mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState.INPUT_STATE).getCommands().clear();
        var target=EntityType.ZOMBIE.create(level);target.setPos(24,160,2);level.addFreshEntity(target);
        var bounds=player.getBoundingBox().inflate(16);var arcs=new ArrayList<EntitySlashEffect>();
        for(var arc:level.getEntitiesOfClass(EntitySlashEffect.class,bounds))arc.discard();
        int[] events={0};Consumer<SlashBladeEvent.DoSlashEvent> listener=e->{if(e.getUser()==player)events[0]++;};
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            InputClock.use(player,blade);
            arcs.addAll(level.getEntitiesOfClass(EntitySlashEffect.class,bounds));
            require(events[0]==1 && arcs.size()==1,"Right click must emit one public slash event and arc");
            float health=target.getHealth();require(health<20,"Immediate melee missing");
            for(int i=0;i<8;i++)arcs.getFirst().tick();
            require(target.getHealth()==health,"Visual arc duplicated melee damage");
            Consumer<SlashBladeEvent.DoSlashEvent> cancel=e->{if(e.getUser()==player)e.setCanceled(true);};
            NeoForge.EVENT_BUS.addListener(cancel);
            try {InputClock.use(player,blade);require(target.getHealth()==health,"Canceled slash still dealt melee damage");}
            finally {NeoForge.EVENT_BUS.unregister(cancel);}
            // Public SE callbacks may consume or replace the held blade synchronously.
            var replacement=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
            Consumer<SlashBladeEvent.DoSlashEvent> swap=e->{if(e.getUser()==player)player.setItemInHand(InteractionHand.MAIN_HAND,replacement);};
            NeoForge.EVENT_BUS.addListener(swap);
            float beforeSwap=target.getHealth();
            try {
                state.updateComboSeq(player,LegacyCombat.id(LegacyMove.BATTOU));
                require(player.getMainHandItem()==replacement,"SE hand replacement was overwritten");
                require(target.getHealth()==beforeSwap,"Old attack continued with replacement item");
            } finally {NeoForge.EVENT_BUS.unregister(swap);player.setItemInHand(InteractionHand.MAIN_HAND,blade);}
            report.put("se_hand_replacement",Map.of("no_crash",true,"replacement_preserved",true,"no_followup_melee",true));
            int[] hitSwaps={0};
            Consumer<SlashBladeEvent.HitEvent> hitSwap=e->{if(e.getUser()==player){hitSwaps[0]++;player.setItemInHand(InteractionHand.MAIN_HAND,replacement);}};
            NeoForge.EVENT_BUS.addListener(hitSwap);
            try {
                target.setHealth(20);target.invulnerableTime=0;
                state.updateComboSeq(player,LegacyCombat.id(LegacyMove.BATTOU));
                require(hitSwaps[0]>0 && player.getMainHandItem()==replacement,"Hit callback replacement not exercised/preserved");
            } finally {NeoForge.EVENT_BUS.unregister(hitSwap);player.setItemInHand(InteractionHand.MAIN_HAND,blade);}
            report.put("hit_hand_replacement",Map.of("callback_observed",true,"no_marker_crash",true));
            var definition=level.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements()
                    .filter(h->h.key().location().getNamespace().equals("slashblade_addon") && h.key().location().getPath().endsWith("kirisaya")).findFirst();
            if(definition.isPresent()) {
                var kirisaya=definition.get().value().getBlade(level.registryAccess());player.setItemInHand(InteractionHand.MAIN_HAND,kirisaya);
                var ks=BladeStateAccess.of(kirisaya).orElseThrow();ks.setProudSoulCount(10000);ks.setBroken(false);
                player.experienceLevel=29;InputClock.use(player,kirisaya);
                require(level.getEntitiesOfClass(EntityDrive.class,bounds).stream().noneMatch(e->!(e instanceof LegacyDrive)),"BurstDrive activated below native SE level");
                player.experienceLevel=30;ks.setComboSeq(ComboStateRegistry.NONE.getId());
                InputClock.use(player,kirisaya);
                var drives=level.getEntitiesOfClass(EntityDrive.class,bounds).stream().filter(e->!(e instanceof LegacyDrive)).toList();
                require(drives.size()==1,"Actual kirisaya BurstDrive failed to spawn exactly one native drive");
                require(!drives.getFirst().getPersistentData().getBoolean("slashblade_legacy_compat.visual_arc"),"SE projectile incorrectly made visual-only");
                for(var drive:drives)drive.discard();
                report.put("kirisaya_burst_drive",Map.of("actual_named_blade",true,"native_level_gate",30,"right_click_drive",true));
            }else report.put("kirisaya_burst_drive","SKIP: SJAP absent in minimal environment");
            // Simulate the same displacement the client sends, against actual solid blocks.
            player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.setPos(24,164,0);player.setOnGround(false);
            var floor=new net.minecraft.core.BlockPos(24,159,0);var old=level.getBlockState(floor);
            try {
                level.setBlockAndUpdate(floor,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                state.updateComboSeq(player,LegacyCombat.id(LegacyMove.HELM_BRAKER));
                for(int i=2;i<9 && !player.onGround();i++) {
                    LegacyCombat.tickMotion(player,level.getGameTime()+i);
                    require(player.getDeltaMovement().y<0,"Helm sent non-descending velocity");
                    player.move(MoverType.SELF,player.getDeltaMovement());
                }
                require(player.onGround() && player.getY()==160,"Helm failed solid-floor landing");
                LegacyCombat.tickMotion(player,level.getGameTime()+9);
                require(player.getY()==160,"Helm moved after landing");
            }finally {level.setBlockAndUpdate(floor,old);}
            report.put("dev8_regressions",Map.of("public_slash_event",true,"visible_arc_without_duplicate_damage",true,
                    "cancel_respected",true,"helm_solid_floor_landing",true));
            namedChains(player,report);
            rankAwards(player,report);
        }finally {
            NeoForge.EVENT_BUS.unregister(listener);target.discard();
            for(var arc:level.getEntitiesOfClass(EntitySlashEffect.class,bounds))arc.discard();
            player.setItemInHand(InteractionHand.MAIN_HAND,saved);player.experienceLevel=savedLevel;
        }
    }
    private static void namedChains(ServerPlayer player,Map<String,Object> report) {
        var level=player.serverLevel();var main=player.getMainHandItem();var off=player.getOffhandItem();
        var results=new LinkedHashMap<String,Object>();
        player.setItemInHand(InteractionHand.OFF_HAND,net.minecraft.world.item.ItemStack.EMPTY);
        player.setOnGround(true);player.experienceLevel=0;
        try {
            for(var holder:level.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements().toList()) {
                var blade=holder.value().getBlade(level.registryAccess());var state=BladeStateAccess.of(blade).orElse(null);
                if(state==null){results.put(holder.key().location().toString(),"SKIP: definition returns no blade state");continue;}
                if(!state.getComboRoot().equals(ComboStateRegistry.STANDBY.getId())) {
                    results.put(holder.key().location().toString(),"CUSTOM_ROOT: "+state.getComboRoot());continue;
                }
                if(mods.flammpfeil.slashblade.item.SwordType.from(blade).contains(mods.flammpfeil.slashblade.item.SwordType.NOSCABBARD))continue;
                player.setItemInHand(InteractionHand.MAIN_HAND,blade);
                state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(level.getGameTime());
                player.getData(mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState.INPUT_STATE).getCommands().clear();
                var chain=new ArrayList<String>();
                for(int click=0;click<3;click++) {
                    InputClock.use(player,blade);
                    chain.add(state.getComboSeq().toString());
                    state.setLastActionTime(level.getGameTime()-4);
                    player.stopUsingItem();
                }
                results.put(holder.key().location().toString(),chain);
                require(new HashSet<>(chain).size()==3,"Repeated combo on named blade "+holder.key().location()+": "+chain);
                for(var arc:level.getEntitiesOfClass(EntitySlashEffect.class,player.getBoundingBox().inflate(16)))arc.discard();
                for(var drive:level.getEntitiesOfClass(EntityDrive.class,player.getBoundingBox().inflate(16)))drive.discard();
            }
            require(results.get("slashblade:sange") instanceof List,"Sange chain was not exercised");
            if(net.neoforged.fml.ModList.get().isLoaded("slashblade_addon"))
                require(results.get("slashblade_addon:kamuy_lightning") instanceof List,"Kamuy lightning chain was not exercised");
        }finally {player.setItemInHand(InteractionHand.MAIN_HAND,main);player.setItemInHand(InteractionHand.OFF_HAND,off);report.put("named_blade_right_chains",results);}
    }
    private static void rankAwards(ServerPlayer player,Map<String,Object> report) {
        var level=player.serverLevel();var saved=player.getMainHandItem();
        var holder=level.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements()
                .filter(h->h.key().location().getPath().endsWith("sange")).findFirst().orElseThrow();
        var blade=holder.value().getBlade(level.registryAccess());var state=BladeStateAccess.of(blade).orElseThrow();
        var rank=player.getData(mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank.RANK_POINT);
        var target=EntityType.ZOMBIE.create(level);target.setPos(24,160,2);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);
        level.addFreshEntity(target);player.setPos(24,160,0);player.setOnGround(true);player.setItemInHand(InteractionHand.MAIN_HAND,blade);
        var points=new ArrayList<Long>();
        try {
            require(state.getCarryType().name().equals("PSO2"),"Sange carry fixture changed");
            require(LegacyBladePose.handlesCarry("PSO2",LegacyMove.SAYA1) && !LegacyBladePose.handlesCarry("PSO2",LegacyMove.NONE),"PSO2 swing/standby routing");
            for(boolean enabled:new boolean[]{false,true}) {
                LegacyCompat.LEGACY_RANK.set(enabled);rank.setRawRankPoint(0);rank.setLastUpdte(level.getGameTime());
                for(var move:LegacyMove.values())player.getPersistentData().remove("slashblade_legacy_compat.rank_cd."+move.name());
                state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(level.getGameTime());
                for(int click=0;click<3;click++) {
                    InputClock.use(player,blade);player.stopUsingItem();
                    state.setLastActionTime(level.getGameTime()-4);
                }
                points.add(rank.getRawRankPoint());
            }
            require(points.get(0)<rank.getUnitCapacity() && points.get(1)>=rank.getUnitCapacity(),"Legacy chain should reach visible D rank: "+points);
            long before=rank.getRawRankPoint();
            state.setComboSeq(ComboStateRegistry.NONE.getId());InputClock.use(player,blade);
            require(rank.getRawRankPoint()-before<rank.getUnitCapacity()*.3,"Repeated same move lacks diminishing return");
            target.setPos(24,160,100);before=rank.getRawRankPoint();
            InputClock.use(player,blade);
            require(rank.getRawRankPoint()==before,"Empty swing awarded rank");
            report.put("legacy_rank_sange",Map.of("native_three_hit_points",points.get(0),"legacy_three_hit_points",points.get(1),
                    "visible_rank_threshold",rank.getUnitCapacity(),"repeat_diminished",true,"no_whiff_points",true,"native_sync_path",true));
        } finally {LegacyCompat.LEGACY_RANK.set(true);target.discard();player.setItemInHand(InteractionHand.MAIN_HAND,saved);}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}

