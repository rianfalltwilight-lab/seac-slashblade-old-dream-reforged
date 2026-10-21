package org.scex.slashbladelegacy.contracts;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import mods.flammpfeil.slashblade.SlashBlade.RegistryEvents;
import mods.flammpfeil.slashblade.SlashBladeConfig;
import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.MinecraftForge;
import org.scex.slashbladelegacy.*;

final class Bugfix17Contracts {
    private static final List<Map<String,Object>> checks=new ArrayList<>();
    static void check(String name,boolean pass){checks.add(Map.of("name",name,"passed",pass));if(!pass)throw new AssertionError(name);}
    static void run(MinecraftServer server,Map<String,Object> report) throws Exception {
        checks.clear();report.put("bugfix_20260917",checks);
        var level=server.overworld();
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"Bugfix17"));
        player.setPos(24,160,0);player.setOnGround(true);player.setYRot(0);player.experienceLevel=100;
        var original=new ItemStack(SlashBladeItems.SLASHBLADE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND,original);
        Guard17Contracts.run(player,report);
        BladeSoul17Contracts.run(player,report);
        var stand=RegistryEvents.BladeStand.create(level);stand.setPos(24,160,1);stand.setItem(original.copy());level.addFreshEntity(stand);
        var cow=EntityType.COW.create(level);cow.setPos(24,160,2);cow.setNoAi(true);level.addFreshEntity(cow);
        var zombie=EntityType.ZOMBIE.create(level);var wolf=EntityType.WOLF.create(level);wolf.setOwnerUUID(player.getUUID());
        boolean nativeFriendly=SlashBladeConfig.FRIENDLY_ENABLE.get();
        try {
            var materials=List.of(SlashBladeItems.PROUDSOUL_TINY.get(),SlashBladeItems.PROUDSOUL.get(),SlashBladeItems.PROUDSOUL_INGOT.get(),SlashBladeItems.PROUDSOUL_SPHERE.get(),SlashBladeItems.PROUDSOUL_CRYSTAL.get(),SlashBladeItems.PROUDSOUL_TRAPEZOHEDRON.get());
            int[] expected={100,200,400,400,500,500};
            for(int i=0;i<materials.size();i++) {
                var soul=new ItemStack(materials.get(i),3);player.setItemInHand(InteractionHand.MAIN_HAND,soul);player.setShiftKeyDown(true);
                BladeStateAccess.of(stand.getItem()).orElseThrow().setProudSoulCount(0);stand.hurt(player.damageSources().playerAttack(player),1);
                check("feed material "+i,BladeStateAccess.of(stand.getItem()).orElseThrow().getProudSoulCount()==expected[i] && soul.getCount()==2 && !stand.isOnFire());
            }
            var soul=new ItemStack(SlashBladeItems.PROUDSOUL.get(),3);player.setItemInHand(InteractionHand.MAIN_HAND,soul);player.setShiftKeyDown(false);
            var state=BladeStateAccess.of(stand.getItem()).orElseThrow();state.setProudSoulCount(0);stand.hurt(player.damageSources().playerAttack(player),1);
            check("plain left click feeds fragment",state.getProudSoulCount()==200 && soul.getCount()==2);
            state=BladeStateAccess.of(stand.getItem()).orElseThrow();
            Consumer<SlashBladeEvent.AddProudSoulEvent> cancel=e->{if(e.getBlade()==stand.getItem())e.setNewCount(0);};
            MinecraftForge.EVENT_BUS.addListener(cancel);try{stand.hurt(player.damageSources().playerAttack(player),1);}finally{MinecraftForge.EVENT_BUS.unregister(cancel);}
            check("cancel keeps material and souls",state.getProudSoulCount()==200 && soul.getCount()==2);
            state.setProudSoulCount(Integer.MAX_VALUE-1);stand.hurt(player.damageSources().playerAttack(player),1);
            check("soul total saturates",state.getProudSoulCount()==Integer.MAX_VALUE && soul.getCount()==1);
            state=BladeStateAccess.of(stand.getItem()).orElseThrow();
            stand.hurt(player.damageSources().playerAttack(player),1);check("full blade keeps material",soul.getCount()==1);
            state.setProudSoulCount(0);player.getAbilities().instabuild=true;stand.hurt(player.damageSources().playerAttack(player),1);
            check("creative still consumes one",state.getProudSoulCount()==200 && soul.isEmpty());player.getAbilities().instabuild=false;
            state=BladeStateAccess.of(stand.getItem()).orElseThrow();
            soul=new ItemStack(SlashBladeItems.PROUDSOUL.get(),3);player.setItemInHand(InteractionHand.MAIN_HAND,soul);
            LegacyCompat.SOUL_FEEDING.set(false);MinecraftForge.EVENT_BUS.post(new SlashBladeEvent.BladeStandAttackEvent(stand.getItem(),state,stand,player.damageSources().playerAttack(player)));
            check("feeding switch off",state.getProudSoulCount()==200 && soul.getCount()==3);LegacyCompat.SOUL_FEEDING.set(true);
            player.getPersistentData().putBoolean(LegacyMode.MODERN,true);MinecraftForge.EVENT_BUS.post(new SlashBladeEvent.BladeStandAttackEvent(stand.getItem(),state,stand,player.damageSources().playerAttack(player)));
            check("modern mode untouched",state.getProudSoulCount()==200 && soul.getCount()==3);player.getPersistentData().remove(LegacyMode.MODERN);
            var sphere=new ItemStack(SlashBladeItems.PROUDSOUL_SPHERE.get(),2);stand.getItem().enchant(Enchantments.POWER_ARROWS,1);
            player.setItemInHand(InteractionHand.MAIN_HAND,sphere);stand.hurt(player.damageSources().playerAttack(player),1);
            check("plain sphere keeps SB operation",SummonedBladeMode.enabled(stand.getItem()) && sphere.getCount()==1 && BladeStateAccess.of(stand.getItem()).orElseThrow().getProudSoulCount()==200);
            player.setShiftKeyDown(true);stand.hurt(player.damageSources().playerAttack(player),1);
            check("sneak sphere feeds without toggling SB",SummonedBladeMode.enabled(stand.getItem()) && sphere.isEmpty() && BladeStateAccess.of(stand.getItem()).orElseThrow().getProudSoulCount()==600);
            player.setShiftKeyDown(false);player.setItemInHand(InteractionHand.MAIN_HAND,original);
            SlashBladeConfig.FRIENDLY_ENABLE.set(true);LegacyCompat.FRIENDLY_TARGETS.set(false);
            check("friendly off independent of framework",!LegacyTargets.attackable(player,cow));
            float hp=cow.getHealth();InputClock.use(player,original);check("right click protects friendly",cow.getHealth()==hp);
            LegacyCompat.FRIENDLY_TARGETS.set(true);InputClock.use(player,original);
            check("right click enabled damages friendly",cow.getHealth()<hp);
            check("owned pet remains protected",!LegacyTargets.attackable(player,wolf));
            LegacyCompat.FRIENDLY_TARGETS.set(false);check("hostile always targetable",LegacyTargets.attackable(player,zombie));
            cow.setLastHurtByMob(player);check("provoked mob can be fought",LegacyTargets.attackable(player,cow));
        } finally {
            LegacyCompat.FRIENDLY_TARGETS.set(false);LegacyCompat.SOUL_FEEDING.set(true);SlashBladeConfig.FRIENDLY_ENABLE.set(nativeFriendly);
            player.getPersistentData().remove(LegacyMode.MODERN);player.setShiftKeyDown(false);player.stopUsingItem();stand.discard();cow.discard();wolf.discard();zombie.discard();
        }
    }
}
