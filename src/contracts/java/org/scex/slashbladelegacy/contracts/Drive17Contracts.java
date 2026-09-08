package org.scex.slashbladelegacy.contracts;

import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.scex.slashbladelegacy.*;

final class Drive17Contracts {
    static void run(ServerPlayer player,Map<String,Object> report) {
        var original=player.getMainHandItem();var originalPos=player.position();var blade=new ItemStack(SlashBladeItems.SLASHBLADE.get());
        blade.set(DataComponents.CUSTOM_NAME,Component.literal("r87 Drive contract"));blade.setDamageValue(1);
        player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.setPos(-8,160,-8);player.setYRot(0);player.setXRot(0);player.removeAllEffects();
        var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);rank.setRawRankPoint(0);rank.setLastUpdte(player.level().getGameTime());
        for(var key:new ArrayList<>(player.getPersistentData().getAllKeys()))if(key.startsWith("slashblade_legacy_compat.rank_cd."))player.getPersistentData().remove(key);
        var target=EntityType.ALLAY.create(player.level());target.setPos(-8,160,-8);player.serverLevel().addFreshEntity(target);
        var created=new ArrayList<LegacyDrive>();
        try {
            var moving=new LegacyDrive(SummonedBladeMode.DRIVE.get(),player.level());created.add(moving);moving.initialize(player,blade,3,.75f,-170,true);
            var origin=player.position().add(0,player.getEyeHeight()/2,0);check(moving.position().equals(origin),"setInitialSpeed starts at half-eye origin");
            moving.tick();check(moving.position().distanceTo(origin.add(0,0,.75*1.05))<.000001 && moving.age()==1,"first tick accelerates by 1.05");moving.discard();
            var drive=make(player,blade,false,created);drive.setLifetime(10);player.setPos(-8,160,80);
            int wear=blade.getDamageValue();drive.tick();check(target.getHealth()==17 && blade.getDamageValue()==wear+1 && !drive.isRemoved(),"first hit accepts non-Enemy beyond old adapter's 64-block cap");
            check(rank.getRawRankPoint()==rank.getUnitCapacity()/2,"single Drive awards old .5 once");
            for(int i=0;i<3;i++)drive.tick();check(target.getHealth()==17 && drive.age()==4,"single target deduplicated");
            var tag=new CompoundTag();drive.save(tag);var restored=(LegacyDrive)EntityType.loadEntityRecursive(tag,player.level(),e->e);created.add(restored);drive.discard();
            check(restored!=null && restored.getOwner()==player && restored.age()==4 && restored.getLifetime()==10 && !restored.multiHit(),"owner source age hit mode and lifetime survive entity save");
            for(int i=0;i<5;i++)restored.tick();check(!restored.isRemoved() && target.getHealth()==17,"restored single retains hit history through tick nine");
            restored.tick();check(restored.isRemoved(),"configured lifetime expires at ten");
            target.setHealth(20);var multi=make(player,blade,true,created);wear=blade.getDamageValue();multi.tick();check(target.getHealth()==20,"multi waits on odd tick");
            multi.tick();multi.tick();multi.tick();check(target.getHealth()==14 && blade.getDamageValue()==wear+2,"multi hits every second tick with one callback per hit");multi.discard();
            target.setHealth(20);var denied=make(player,blade,false,created);denied.setDimension(true);target.invulnerableTime=17;wear=blade.getDamageValue();long points=rank.getRawRankPoint();
            Consumer<LivingIncomingDamageEvent> cancel=e->{if(e.getEntity()==target)e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(cancel);
            try{denied.tick();}finally{NeoForge.EVENT_BUS.unregister(cancel);}
            check(target.getHealth()==20 && target.invulnerableTime==17 && blade.getDamageValue()==wear && rank.getRawRankPoint()==points,"canceled dimension Drive cannot cut HP or wear or rank");denied.discard();
            var cut=make(player,blade,false,created);cut.setDimension(true);cut.tick();check(target.getHealth()==14,"dimension cut plus magic preserves old arithmetic");cut.discard();
            target.setHealth(5);var lethal=make(player,blade,false,created);lethal.setDimension(true);int[] deaths={0};
            Consumer<net.neoforged.neoforge.event.entity.living.LivingDeathEvent> death=e->{if(e.getEntity()==target){check(e.getSource().getEntity()==player,"dimension kill credited to owner");deaths[0]++;}};
            NeoForge.EVENT_BUS.addListener(death);try{lethal.tick();}finally{NeoForge.EVENT_BUS.unregister(death);}
            check(target.getHealth()==0 && deaths[0]==1,"pre-hit health floor permits a kill and exactly one death callback");lethal.discard();
            var moved=make(player,blade,false,created);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.getInventory().setItem(9,blade);moved.tick();check(!moved.isRemoved(),"source moved to backpack remains valid");
            player.getInventory().setItem(10,blade.copy());moved.tick();check(moved.isRemoved(),"duplicated source fails closed");player.getInventory().setItem(9,ItemStack.EMPTY);player.getInventory().setItem(10,ItemStack.EMPTY);player.setItemInHand(InteractionHand.MAIN_HAND,blade);
            player.setPos(-8,160,-8);var state=BladeStateAccess.of(blade).orElseThrow();state.setProudSoulCount(100);state.setBroken(false);blade.setDamageValue(1);
            blade.enchant(player.registryAccess().holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.POWER),1);
            check(mods.flammpfeil.slashblade.item.SwordType.from(blade).contains(mods.flammpfeil.slashblade.item.SwordType.BEWITCHED),"bewitched charged followup fixture");
            rank.setRawRankPoint(4*rank.getUnitCapacity());rank.setLastUpdte(player.level().getGameTime());
            LegacyAdditionalAttack.markCharged(player,LegacyCombat.id(LegacyMove.BATTOU));LegacyAdditionalAttack.attack(player,LegacyMove.BATTOU);
            var followup=live(player);check(followup.size()==1 && followup.getFirst().multiHit() && followup.getFirst().getRotationRoll()==90 && state.getProudSoulCount()==90,"charged followup decodes inverted r87 setter as multi");followup.forEach(Entity::discard);
            state.setKillCount(1000);
            for(int band:new int[]{5,6}) {
                rank.setRawRankPoint((band==5?500:560)*rank.getUnitCapacity()/100);rank.setLastUpdte(player.level().getGameTime());
                LegacyAdditionalAttack.attack(player,LegacyMove.S_SLASH_BLADE);var finisher=live(player);
                check(finisher.size()==1 && finisher.getFirst().multiHit()==(band==6) && finisher.getFirst().dimension(),"fiercer finisher old single/multi rank "+band);finisher.forEach(Entity::discard);
            }
            report.put("legacy17_drive",Map.of("old_acceleration",1.05,"configured_lifetime",10,"allay_first_hit",true,"range_cap_removed",true,"owner_save",true,"single_dedupe_save",true,"multi_interval",2,"protection",true,"source_move_and_copy",true,"additional_flag_inversion",true));
        }finally {created.stream().filter(Objects::nonNull).forEach(Entity::discard);live(player).forEach(Entity::discard);target.discard();player.getInventory().setItem(9,ItemStack.EMPTY);player.getInventory().setItem(10,ItemStack.EMPTY);player.setItemInHand(InteractionHand.MAIN_HAND,original);player.setPos(originalPos);}
    }
    private static LegacyDrive make(ServerPlayer player,ItemStack blade,boolean multi,List<LegacyDrive> created){var drive=new LegacyDrive(SummonedBladeMode.DRIVE.get(),player.level());drive.initialize(player,blade,3,0,0,multi);drive.setPos(-8,160.5,-8);created.add(drive);return drive;}
    private static List<LegacyDrive> live(ServerPlayer player){return player.level().getEntitiesOfClass(LegacyDrive.class,new AABB(-20,150,-20,20,175,100),e->e.getOwner()==player && !e.isRemoved());}
    private static void check(boolean value,String message){if(!value)throw new AssertionError("r87 Drive: "+message);}
}
