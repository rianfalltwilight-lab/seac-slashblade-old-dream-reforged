package org.scex.slashbladelegacy.contracts;

import mods.flammpfeil.slashblade.RegistryEvents;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.scex.slashbladelegacy.LegacySummonedBlade;
import org.scex.slashbladelegacy.SummonedBladeMode;
import java.util.EnumSet;
import java.util.Map;

final class SbContracts {
    static void run(ServerPlayer player,ItemStack blade,Map<String,Object> report) {
        var level=player.serverLevel();
        level.addNewPlayer(player);
        blade.remove(DataComponents.ATTRIBUTE_MODIFIERS);
        blade.setDamageValue(0);
        blade.set(DataComponents.CUSTOM_NAME,Component.literal("SB contract"));
        blade.enchant(level.registryAccess().holderOrThrow(Enchantments.POWER),3);
        var state=BladeStateAccess.of(blade).orElseThrow();
        state.setProudSoulCount(100);
        var stand=RegistryEvents.BladeStand.create(level);
        stand.setPos(0,160,-1);stand.setItem(blade);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SlashBladeItems.PROUDSOUL_SPHERE.get(),2));
        // Exercise the actual BladeStandEntity damage path used by left-clicking with the orb.
        player.attack(stand);
        require(!stand.isRemoved() && player.getMainHandItem().getCount()==1,"Left-click SB mode switch consumption/stand protection");
        blade=stand.getItem().copy();
        require(SummonedBladeMode.enabled(blade),"SB mode switch persisted");
        player.setItemSlot(EquipmentSlot.MAINHAND,blade);
        state=BladeStateAccess.of(blade).orElseThrow();
        var bounds=new AABB(-64,100,-64,64,220,64);
        for(var old:level.getEntitiesOfClass(LegacySummonedBlade.class,bounds)) old.discard();
        report.put("sb_before_press",snapshot(player,blade,bounds));
        input(player,false,true);
        require(state.getProudSoulCount()==100,"Original single shot was not suppressed");
        require(level.getEntitiesOfClass(LegacySummonedBlade.class,bounds).isEmpty(),"SB fired before release");
        var joinEvents=new java.util.ArrayList<Map<String,Object>>();
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> observer=e->{
            if(e.getEntity() instanceof LegacySummonedBlade projectile)joinEvents.add(java.util.Map.of(
                    "position",projectile.position().toString(),"canceled",e.isCanceled(),"removed",projectile.isRemoved(),
                    "loaded_from_disk",e.loadedFromDisk(),"in_query_bounds",projectile.getBoundingBox().intersects(bounds)));
        };
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,true,observer);
        try{input(player,true,false);}finally{NeoForge.EVENT_BUS.unregister(observer);}
        report.put("sb_release_join_events",joinEvents);
        report.put("sb_after_release",snapshot(player,player.getMainHandItem(),bounds));
        var shots=level.getEntitiesOfClass(LegacySummonedBlade.class,bounds);
        require(shots.size()==1 && state.getProudSoulCount()==99,"Single SB spawn/cost");
        input(player,true,false);
        require(level.getEntitiesOfClass(LegacySummonedBlade.class,bounds).size()==1 && state.getProudSoulCount()==99,"Repeated release duplicated shot");
        var shot=shots.getFirst();
        require(shot.getDamage()==1,"Low Rank POWER clamp");
        var initial=shot.position();
        for(int i=0;i<10;i++) shot.tick();
        require(shot.position().equals(initial),"Launch delay");
        shot.tick();require(shot.position().distanceToSqr(initial)>1,"SB never launched");
        var save=new net.minecraft.nbt.CompoundTag();shot.save(save);
        var restored=(LegacySummonedBlade)EntityType.loadEntityRecursive(save,level,e->e);
        require(restored!=null && restored.getOwner()==player && restored.getDamage()==1,"SB owner/damage reload");
        shot.discard();
        var target=EntityType.ZOMBIE.create(level);
        target.setPos(restored.getX(),restored.getY(),restored.getZ()+0.4);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
        require(level.addFreshEntity(target),"Target entity registration failed");
        report.put("sb_target_in_world",level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,bounds).contains(target));
        float health=target.getHealth();
        var diagnosticRay=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level,restored,
                restored.position(),restored.position().add(restored.getDeltaMovement()),
                restored.getBoundingBox().expandTowards(restored.getDeltaMovement()).inflate(1),e->e==target);
        report.put("sb_ray_before",java.util.Map.of("ray",String.valueOf(diagnosticRay),"pickable",target.canBeHitByProjectile(),
                "targetFilter",mods.flammpfeil.slashblade.util.TargetSelector.test.test(player,target),"owner",String.valueOf(restored.getOwner()),
                "position",restored.position().toString(),"box",target.getBoundingBox().toString()));
        restored.tick();
        report.put("sb_collision_observation",java.util.Map.of("removed",restored.isRemoved(),"position",restored.position().toString(),
                "motion",restored.getDeltaMovement().toString(),"targetPosition",target.position().toString(),"health",target.getHealth(),
                "attached",String.valueOf(restored.getHitEntity())));
        require(target.getHealth()<health && restored.getHitEntity()==target,"SB impact attachment");
        float afterHit=target.getHealth();
        restored.doForceHitEntity(target);
        require(target.getHealth()==afterHit,"SB duplicate direct hit");
        var attachedSave=new net.minecraft.nbt.CompoundTag();restored.save(attachedSave);
        var attached=(LegacySummonedBlade)EntityType.loadEntityRecursive(attachedSave,level,e->e);
        restored.discard();
        for(int i=0;i<200;i++) attached.tick();
        require(attached.isRemoved(),"SB attached lifetime");
        float finalHealth=target.getHealth();
        attached.tick();attached.burst();
        require(target.getHealth()==finalHealth,"SB repeated expiry damage");
        require(finalHealth<afterHit,"SB attached end damage missing");
        target.discard();
        require(SummonedBladeMode.enabled(ItemStack.parse(level.registryAccess(),blade.save(level.registryAccess())).orElseThrow()),"SB item save/load");
        report.put("sb_single_shot_cost_and_repeated_input",true);
        report.put("sb_delay_flight_save_impact_attachment_expiry",true);
        report.put("sb_test_scope","Actual server event and projectile lifecycle with FakePlayer; no client or multiplayer acceptance");
        testCharge(player,blade,report);
        testSourceIdentity(player,blade,report);
    }
    private static void testSourceIdentity(ServerPlayer player,ItemStack blade,Map<String,Object> report) {
        var source=blade.get(DataComponents.CUSTOM_DATA).copyTag().getUUID(SummonedBladeMode.SOURCE);
        var level=player.serverLevel();
        // Moving the original blade to another inventory slot must remain valid.
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        player.getInventory().setItem(9,blade);
        var moved=new LegacySummonedBlade(SummonedBladeMode.BLADE.get(),level);
        moved.initialize(player,1,0xffffff,source,null);moved.tick();
        require(!moved.isRemoved(),"Moving source blade invalidated SB");
        // A copied UUID is ambiguous; no arbitrary blade may receive damage callbacks.
        player.getInventory().setItem(10,blade.copy());
        moved.tick();require(moved.isRemoved(),"Duplicate source identity accepted");
        player.getInventory().setItem(10,ItemStack.EMPTY);
        var missing=new LegacySummonedBlade(SummonedBladeMode.BLADE.get(),level);
        missing.initialize(player,1,0xffffff,source,null);
        player.getInventory().setItem(9,ItemStack.EMPTY);
        missing.tick();require(missing.isRemoved(),"Missing source blade accepted");
        player.setItemInHand(InteractionHand.MAIN_HAND,blade);
        report.put("sb_source_moved_duplicate_missing",true);
    }
    private static void input(ServerPlayer player,boolean old,boolean now) {
        var state=player.getData(CapabilityInputState.INPUT_STATE);
        var before=old?EnumSet.of(InputCommand.M_DOWN):EnumSet.noneOf(InputCommand.class);
        var after=now?EnumSet.of(InputCommand.M_DOWN):EnumSet.noneOf(InputCommand.class);
        state.getCommands().clear();state.getCommands().addAll(after);
        if(!old && now) state.getLastPressTimes().put(InputCommand.M_DOWN,player.level().getGameTime());
        NeoForge.EVENT_BUS.post(new InputCommandEvent(player,state,before,after));
    }
    private static void require(boolean result,String message) { if(!result) throw new AssertionError(message); }
    private static Map<String,Object> snapshot(ServerPlayer player,ItemStack blade,AABB bounds){
        var state=BladeStateAccess.of(blade).orElseThrow();
        var data=new java.util.LinkedHashMap<String,Object>();
        data.put("blade_item",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(blade.getItem()).toString());
        data.put("blade_name",blade.getHoverName().getString());data.put("player_position",player.position().toString());
        data.put("player_alive",player.isAlive());data.put("player_removed",player.isRemoved());data.put("game_time",player.level().getGameTime());
        data.put("dimension",player.level().dimension().location().toString());data.put("broken",state.isBroken());data.put("sealed",state.isSealed());
        data.put("enabled",SummonedBladeMode.enabled(blade));data.put("sword_types",mods.flammpfeil.slashblade.item.SwordType.from(blade).toString());
        data.put("power",blade.getEnchantmentLevel(player.registryAccess().holderOrThrow(Enchantments.POWER)));
        data.put("souls",state.getProudSoulCount());data.put("shots_in_bounds",player.level().getEntitiesOfClass(LegacySummonedBlade.class,bounds).size());
        data.put("runtime_state",state.serializeNBT().toString());
        return data;
    }
    private static void testCharge(ServerPlayer player,ItemStack blade,Map<String,Object> report) {
        var state=BladeStateAccess.of(blade).orElseThrow();
        state.setBroken(false);state.setSealed(false);
        state.setSlashArtsKey(mods.flammpfeil.slashblade.registry.SlashArtsRegistry.JUDGEMENT_CUT.getId());
        require(state.getFullChargeTicks(player)==15,"Legacy charge cue tick");
        org.scex.slashbladelegacy.LegacyCompat.LEGACY_CHARGE.set(false);
        require(state.getFullChargeTicks(player)==9,"Charge toggle rollback");
        org.scex.slashbladelegacy.LegacyCompat.LEGACY_CHARGE.set(true);
        var seen=new java.util.LinkedHashMap<Integer,String>();
        java.util.function.Consumer<mods.flammpfeil.slashblade.event.SlashBladeEvent.PerformSlashArtEvent> observer=e->{
            if(e.getEntityLiving()==player) {seen.put(e.getElapsed(),String.valueOf(e.getType()));e.setCanceled(true);}
        };
        NeoForge.EVENT_BUS.addListener(observer);
        try {
            for(int elapsed=14;elapsed<=20;elapsed++) {
                state.setComboSeq(mods.flammpfeil.slashblade.registry.ComboStateRegistry.NONE.getId());
                state.setLastActionTime(player.level().getGameTime());
                state.doChargeAction(player,elapsed);
            }
        } finally {NeoForge.EVENT_BUS.unregister(observer);}
        require(!seen.containsKey(14) && !seen.containsKey(15),"Early SA release");
        for(int i=16;i<=18;i++) require("Jackpot".equals(seen.get(i)),"Legacy just tick "+i);
        require("Success".equals(seen.get(19)) && "Success".equals(seen.get(20)),"Legacy normal release");
        report.put("charge_release_ticks",seen);
    }
}
