package org.scex.slashbladelegacy.contracts;

import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.RegistryEvents;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.event.bladestand.ProudSoulEnchantmentEvent;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.neoforged.neoforge.common.NeoForge;
import org.scex.slashbladelegacy.*;

final class Enchant17Contracts {
    static void run(ServerPlayer player,Map<String,Object> report) {
        var level=player.serverLevel();var original=player.getMainHandItem();
        var blade=original.copy();blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);blade.setDamageValue(1);
        var state=BladeStateAccess.of(blade).orElseThrow();state.setBroken(false);state.setSealed(false);state.setSpecialEffects(new net.minecraft.nbt.ListTag());
        var stand=RegistryEvents.BladeStand.create(level);stand.setPos(player.getX(),player.getY(),player.getZ()+1);stand.setItem(blade);level.addFreshEntity(stand);
        blade=stand.getItem(); // ItemFrame.setItem stores a copy; modify the actually displayed blade.
        var spider=EntityType.SPIDER.create(level);spider.setPos(player.getX(),player.getY(),player.getZ()+2);
        spider.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);spider.setHealth(500);spider.setNoAi(true);level.addFreshEntity(spider);
        var drops=new ArrayList<ItemEntity>();Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> collect=e->{if(e.getEntity() instanceof ItemEntity item && item.distanceToSqr(stand)<4)drops.add(item);};
        NeoForge.EVENT_BUS.addListener(collect);var rows=new ArrayList<Map<String,Object>>();report.put("legacy17_enchantments",rows);
        try {
            for(var key:LegacyEnchantments.RARE) {
                var enchantment=player.registryAccess().holderOrThrow(key);
                check(blade.supportsEnchantment(enchantment),"rare support "+key);
                check(blade.isPrimaryItemFor(enchantment)==LegacyEnchantments.SWORD.contains(key),"rare excluded from table "+key);
            }
            for(var key:LegacyEnchantments.SWORD)check(blade.isPrimaryItemFor(player.registryAccess().holderOrThrow(key)),"old sword table "+key);
            for(var key:List.of(Enchantments.SOUL_SPEED,Enchantments.MENDING,Enchantments.SWEEPING_EDGE))check(!blade.supportsEnchantment(player.registryAccess().holderOrThrow(key)),"new acquisition absent "+key);
            var unbreaking=player.registryAccess().holderOrThrow(Enchantments.UNBREAKING);
            var power=player.registryAccess().holderOrThrow(Enchantments.POWER);
            var mending=player.registryAccess().holderOrThrow(Enchantments.MENDING);
            blade.enchant(mending,1);blade.enchant(unbreaking,3);
            var soul=new ItemStack(SlashBladeItems.PROUDSOUL_SPHERE.get(),2);soul.enchant(unbreaking,1);soul.enchant(power,1);
            player.setItemInHand(InteractionHand.MAIN_HAND,soul);player.attack(stand);
            blade=stand.getItem();
            rows.add(Map.of("first_sphere_count",soul.getCount(),"unbreaking",blade.getEnchantmentLevel(unbreaking),"power",blade.getEnchantmentLevel(power)));
            check(soul.getCount()==1 && blade.getEnchantmentLevel(unbreaking)==4 && blade.getEnchantmentLevel(power)==1,"one soul upgrades all enchants and allows Unbreaking IV");
            check(blade.getEnchantmentLevel(mending)==1,"existing modern enchant retained");
            player.attack(stand);blade=stand.getItem();check(blade.getEnchantmentLevel(unbreaking)==5 && blade.getEnchantmentLevel(power)==2,"Unbreaking V and second enchant");
            soul=new ItemStack(SlashBladeItems.PROUDSOUL_SPHERE.get(),1);soul.enchant(unbreaking,1);player.setItemInHand(InteractionHand.MAIN_HAND,soul);int before=drops.size();player.attack(stand);
            blade=stand.getItem();check(soul.isEmpty() && drops.size()==before+1,"capped enchant produces exactly one SA sphere");
            var orb=drops.getLast().getItem();check(orb.getOrDefault(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getString("SpecialAttackType").equals(BladeStateAccess.of(blade).orElseThrow().getSlashArtsKey().toString()),"copied SA retains addon key");
            rows.add(Map.of("stand","multi-enchant sphere, Unbreaking V, existing Mending preserved, capped SA copied","blade_sa",BladeStateAccess.of(blade).orElseThrow().getSlashArtsKey().toString()));
            // Seed whose first float is below .75 proves the ingot upgrades instead of modern unconditional SA-copy.
            long seed=0;while(net.minecraft.util.RandomSource.create(seed).nextFloat()>=.75)seed++;
            // Seed at the public stand event: Player.attack may consume random values before it reaches the stand.
            long standSeed=seed,worldSeed=0;while(net.minecraft.util.RandomSource.create(worldSeed).nextFloat()<=.75)worldSeed++;
            final long rejectedWorldSeed=worldSeed;
            int[] standCalls={0};
            var ingotDiagnostic=new LinkedHashMap<String,Object>();rows.add(ingotDiagnostic);
            Consumer<mods.flammpfeil.slashblade.event.SlashBladeEvent.BladeStandAttackEvent> seedStand=e->{if(e.getBladeStand()==stand){standCalls[0]++;player.getRandom().setSeed(standSeed);level.getRandom().setSeed(rejectedWorldSeed);ingotDiagnostic.put("player_roll",net.minecraft.util.RandomSource.create(standSeed).nextFloat());ingotDiagnostic.put("unused_world_roll",net.minecraft.util.RandomSource.create(rejectedWorldSeed).nextFloat());}};
            Consumer<ProudSoulEnchantmentEvent> observe=e->{if(e.getOriginalEvent().getBladeStand()==stand){ingotDiagnostic.put("extension_probability",e.getProbability());ingotDiagnostic.put("extension_cost",e.getTotalShrinkCount());ingotDiagnostic.put("extension_blade_same",e.getBlade()==stand.getItem());}};
            Consumer<mods.flammpfeil.slashblade.event.SlashBladeEvent.BladeStandAttackEvent> afterStand=e->{if(e.getBladeStand()==stand){ingotDiagnostic.put("after_canceled",e.isCanceled());ingotDiagnostic.put("after_fire",stand.getRemainingFireTicks());}};
            NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,seedStand);
            NeoForge.EVENT_BUS.addListener(observe);
            NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,true,afterStand);
            var punch=player.registryAccess().holderOrThrow(Enchantments.PUNCH);
            soul=new ItemStack(SlashBladeItems.PROUDSOUL_INGOT.get(),2);soul.enchant(power,1);soul.enchant(punch,1);player.setItemInHand(InteractionHand.MAIN_HAND,soul);
            try{player.attack(stand);}finally{NeoForge.EVENT_BUS.unregister(seedStand);NeoForge.EVENT_BUS.unregister(observe);NeoForge.EVENT_BUS.unregister(afterStand);}
            blade=stand.getItem();rows.add(Map.of("ingot_count",soul.getCount(),"ingot_power",blade.getEnchantmentLevel(power),"stand_seed",standSeed,"stand_calls",standCalls[0],"stand_fire",stand.isOnFire(),"stand_removed",stand.isRemoved(),"attack_damage",player.getAttributeValue(Attributes.ATTACK_DAMAGE),"held_same",player.getMainHandItem()==soul));
            check(soul.getCount()==1 && blade.getEnchantmentLevel(power)==3 && blade.getEnchantmentLevel(punch)==1,"ingot shares one player roll across enchants despite rejecting native world roll");
            // A separately posted foreign event must still receive the installed upstream probability check.
            var foreignOriginal=new mods.flammpfeil.slashblade.event.SlashBladeEvent.BladeStandAttackEvent(blade,BladeStateAccess.of(blade).orElseThrow(),stand,player.damageSources().playerAttack(player));
            var foreign=new ProudSoulEnchantmentEvent(blade,BladeStateAccess.of(blade).orElseThrow(),power,4,true,.75f,1,foreignOriginal);
            level.getRandom().setSeed(rejectedWorldSeed);NeoForge.EVENT_BUS.post(foreign);
            check(foreign.isCanceled() && !LegacyEnchantments.ownsSoulRoll(foreign),"native probability remains active outside exact legacy event scope");
            Consumer<ProudSoulEnchantmentEvent> cancel=e->{if(e.getOriginalEvent().getBladeStand()==stand)e.setCanceled(true);};
            var snapshot=blade.copy();NeoForge.EVENT_BUS.addListener(cancel);
            try{player.attack(stand);check(soul.getCount()==1 && ItemStack.isSameItemSameComponents(blade,snapshot),"protection cancellation leaves blade/material unchanged");}
            finally{NeoForge.EVENT_BUS.unregister(cancel);}
            stand.igniteForSeconds(20);player.attack(stand);check(soul.getCount()==1 && ItemStack.isSameItemSameComponents(blade,snapshot),"burning stand does not enchant");stand.clearFire();
            // Real old hit path: exact duration comes from target's arthropod branch and player's RNG.
            blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);blade.enchant(player.registryAccess().holderOrThrow(Enchantments.BANE_OF_ARTHROPODS),5);
            player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.getData(CapabilityConcentrationRank.RANK_POINT).setRawRankPoint(0);player.removeAllEffects();player.setOnGround(true);player.fallDistance=0;LegacyDamage.update(player);
            player.getRandom().setSeed(17);int expected=20+net.minecraft.util.RandomSource.create(17).nextInt(50);
            check(LegacyDamage.hit(player,spider,LegacyMove.BATTOU,spider.getBoundingBox(),true),"actual Bane hit");
            var slow=spider.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            check(slow!=null && slow.getAmplifier()==3 && slow.getDuration()==expected,"old discrete Bane duration "+(slow==null?null:slow.getDuration())+" expected "+expected);
            rows.add(Map.of("bane_duration",slow.getDuration(),"bane_amplifier",slow.getAmplifier()));
        } finally { NeoForge.EVENT_BUS.unregister(collect);for(var drop:drops)drop.discard();stand.discard();spider.discard();player.setItemInHand(InteractionHand.MAIN_HAND,original); }
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError("r87 enchant: "+message);}
}
