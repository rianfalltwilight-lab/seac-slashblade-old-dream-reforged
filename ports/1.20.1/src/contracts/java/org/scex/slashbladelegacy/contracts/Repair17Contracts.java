package org.scex.slashbladelegacy.contracts;

import java.util.*;
import java.util.function.Consumer;
import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.event.*;
import mods.flammpfeil.slashblade.registry.*;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraftforge.common.MinecraftForge;

final class Repair17Contracts {
    static void run(ServerPlayer player,Map<String,Object> report) {
        var original=player.getMainHandItem();var inventory=new ArrayList<>(player.getInventory().items);
        int xp=player.experienceLevel,total=player.totalExperience;float progress=player.experienceProgress;
        var rows=new ArrayList<Map<String,Object>>();report.put("legacy17_repair",rows);
        try {
            player.stopUsingItem();player.getInventory().clearContent();player.experienceLevel=1000;player.totalExperience=100000;
            var materials=List.of(SlashBladeItems.PROUDSOUL.get(),SlashBladeItems.PROUDSOUL_INGOT.get(),SlashBladeItems.PROUDSOUL_SPHERE.get(),SlashBladeItems.PROUDSOUL_TINY.get(),SlashBladeItems.PROUDSOUL_CRYSTAL.get(),SlashBladeItems.PROUDSOUL_TRAPEZOHEDRON.get());
            int[] damage={35,15,5,55,0,0},soul={200,400,400,100,500,500},cost={2,3,4,1,5,5};
            for(int i=0;i<materials.size();i++) {
                var blade=fresh(original);var state=BladeStateAccess.of(blade).orElseThrow();state.setMaxDamage(100);blade.setDamageValue(75);state.setRefine(300);state.setProudSoulCount(0);
                blade.enchant(Enchantments.SHARPNESS,1);
                var menu=new AnvilMenu(20+i,player.getInventory());menu.getSlot(0).set(blade);menu.getSlot(1).set(new ItemStack(materials.get(i)));menu.setItemName("r87 repair");menu.createResult();
                var output=menu.getSlot(2).getItem();var out=BladeStateAccess.of(output).orElseThrow();
                check(output.getDamageValue()==damage[i] && out.getMaxDamage()==100 && out.getRefine()==301 && out.getProudSoulCount()==soul[i],"material factor, single refine, no max-durability growth "+i);
                check(menu.getCost()==cost[i] && menu.getSlot(2).mayPickup(player),"actual anvil level cost "+i);
                int before=player.experienceLevel;menu.getSlot(2).onTake(player,output);
                check(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty() && player.experienceLevel==before-cost[i],"actual output pickup pays one material "+i);
                check(state.getRefine()==300 && blade.getDamageValue()==75,"input blade unchanged");
                rows.add(Map.of("material",materials.get(i).toString(),"damage",output.getDamageValue(),"souls",out.getProudSoulCount(),"cost",cost[i],"refine",out.getRefine()));
            }
            // Every actual addon definition retains model, SA, SE, enchants and item identity after anvil copy.
            int addonCount=0;
            for(var entry:player.level().registryAccess().registryOrThrow(SlashBladeDefinition.REGISTRY_KEY).holders().toList()) {
                if(!Set.of("prinegorerouse","si_slashblade").contains(entry.key().location().getNamespace()))continue;
                var blade=entry.value().getBlade();var state=BladeStateAccess.of(blade).orElseThrow();
                var menu=new AnvilMenu(50,player.getInventory());menu.getSlot(0).set(blade);menu.getSlot(1).set(new ItemStack(SlashBladeItems.PROUDSOUL.get()));menu.createResult();
                var output=menu.getSlot(2).getItem();var out=BladeStateAccess.of(output).orElseThrow();
                check(output.getItem()==blade.getItem() && out.getModel().equals(state.getModel()) && out.getTexture().equals(state.getTexture()) && out.getSlashArtsKey().equals(state.getSlashArtsKey()) && out.getSpecialEffects().equals(state.getSpecialEffects()) && EnchantmentHelper.getEnchantments(output).equals(EnchantmentHelper.getEnchantments(blade)),"addon anvil identity "+entry.key());addonCount++;
            }
            rows.add(Map.of("addon_anvil_definitions",addonCount));
            var blade=fresh(original);var state=BladeStateAccess.of(blade).orElseThrow();state.setMaxDamage(100);blade.setDamageValue(75);state.setProudSoulCount(0);
            blade.enchant(Enchantments.UNBREAKING,1);blade.setHoverName(Component.literal("passive repair"));
            player.getInventory().setItem(1,blade);player.experienceLevel=10;player.totalExperience=100;player.experienceProgress=.5f;
            while(player.level().getGameTime()%20!=0)InputClock.next(player);
            blade.inventoryTick(player.level(),player,1,false);check(blade.getDamageValue()==74 && state.getProudSoulCount()==10 && player.totalExperience==90,"one healthy hotbar repair per second costs 10 raw XP");
            InputClock.next(player);blade.inventoryTick(player.level(),player,1,false);check(blade.getDamageValue()==74,"no repair on intervening tick");
            while(player.level().getGameTime()%20!=0)InputClock.next(player);
            state.setBroken(true);int beforeXP=player.experienceLevel;blade.inventoryTick(player.level(),player,1,false);check(blade.getDamageValue()==64 && player.experienceLevel==beforeXP-1 && state.getProudSoulCount()==30,"broken hotbar repairs ten percent for one level");
            player.experienceLevel=0;var tiny=new ItemStack(SlashBladeItems.PROUDSOUL_TINY.get(),2);player.getInventory().setItem(2,tiny);blade.inventoryTick(player.level(),player,1,false);
            check(blade.getDamageValue()==54 && tiny.getCount()==1 && state.getProudSoulCount()==30,"plain tiny soul replaces XP without new souls");
            tiny.enchant(Enchantments.POWER_ARROWS,1);blade.inventoryTick(player.level(),player,1,false);check(blade.getDamageValue()==54 && tiny.getCount()==1,"enchanted tiny soul protected");
            player.experienceLevel=10;player.getInventory().setItem(1,ItemStack.EMPTY);player.getInventory().setItem(10,blade);blade.inventoryTick(player.level(),player,10,false);check(blade.getDamageValue()==54,"non-hotbar inventory does not self repair");
            player.getInventory().setItem(10,ItemStack.EMPTY);player.setItemInHand(InteractionHand.MAIN_HAND,blade);state.setComboSeq(ComboStateRegistry.NONE.getId());blade.inventoryTick(player.level(),player,player.getInventory().selected,true);check(blade.getDamageValue()==54,"selected blade does not self repair");
            state.setProudSoulCount(0);state.setRefine(0);blade.setRepairCost(7);blade.inventoryTick(player.level(),player,player.getInventory().selected,true);
            check(state.getProudSoulCount()==200 && state.getRefine()==1 && blade.getBaseRepairCost()==0,"prior anvil work converted once");
            blade.inventoryTick(player.level(),player,player.getInventory().selected,true);check(state.getProudSoulCount()==200 && state.getRefine()==1,"no repeated prior work credit");
            int[] cancelled={0};Consumer<RefineProgressEvent> cancel=e->{cancelled[0]++;e.setCanceled(true);};MinecraftForge.EVENT_BUS.addListener(cancel);
            try{
                var menu=renamePreview(player,blade,70);
                menu.getSlot(1).set(new ItemStack(SlashBladeItems.PROUDSOUL.get()));menu.createResult();
                checkCancellation(player,menu,cancelled[0],"progress",rows);
            }finally{MinecraftForge.EVENT_BUS.unregister(cancel);}
            int[] settled={0};Consumer<RefineSettlementEvent> cancelSettlement=e->{settled[0]++;e.setCanceled(true);};MinecraftForge.EVENT_BUS.addListener(cancelSettlement);
            try{
                var menu=renamePreview(player,blade,71);
                menu.getSlot(1).set(new ItemStack(SlashBladeItems.PROUDSOUL.get()));menu.createResult();
                checkCancellation(player,menu,settled[0],"settlement",rows);
            }finally{MinecraftForge.EVENT_BUS.unregister(cancelSettlement);}
            rows.add(Map.of("inventory","healthy/broken/material, cadence, slots, selected exclusion, prior work idempotence","cancellation",true));
        } finally {player.getInventory().clearContent();for(int i=0;i<inventory.size();i++)player.getInventory().setItem(i,inventory.get(i));player.setItemInHand(InteractionHand.MAIN_HAND,original);player.experienceLevel=xp;player.totalExperience=total;player.experienceProgress=progress;}
    }
    private static void checkCancellation(ServerPlayer player,AnvilMenu menu,int callbacks,String phase,List<Map<String,Object>> rows){
        var output=menu.getSlot(2).getItem();boolean empty=output.isEmpty(),pickup=menu.getSlot(2).mayPickup(player);
        var row=new LinkedHashMap<String,Object>();row.put("cancel_phase",phase);row.put("callbacks",callbacks);
        row.put("output_empty",empty);row.put("may_pickup",pickup);row.put("cost",menu.getCost());
        row.put("legacy_mode",org.scex.slashbladelegacy.LegacyMode.legacy(player));
        row.put("output_nbt",output.save(new net.minecraft.nbt.CompoundTag()).toString());rows.add(row);
        check(callbacks>0,phase+" cancellation listener runs: "+row);
        check(empty,phase+" cancellation clears the previous output: "+row);
        check(!pickup && menu.getCost()==0,phase+" cancellation disables pickup and clears displayed cost: "+row);
    }
    private static AnvilMenu renamePreview(ServerPlayer player,ItemStack blade,int id){
        var menu=new AnvilMenu(id,player.getInventory());menu.getSlot(0).set(blade);menu.setItemName("cancelled refine");
        check(!menu.getSlot(2).getItem().isEmpty(),"rename creates a real preview before the material changes");return menu;
    }
    private static ItemStack fresh(ItemStack original){var blade=original.copy();blade.setRepairCost(0);blade.removeTagKey("Enchantments");var state=BladeStateAccess.of(blade).orElseThrow();state.setBroken(false);state.setSealed(false);state.setSpecialEffects(new net.minecraft.nbt.ListTag());return blade;}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError("r87 repair: "+message);}
}
