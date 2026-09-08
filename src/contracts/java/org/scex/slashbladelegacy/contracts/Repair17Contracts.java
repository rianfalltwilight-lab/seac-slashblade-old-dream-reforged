package org.scex.slashbladelegacy.contracts;

import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.event.*;
import mods.flammpfeil.slashblade.registry.*;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.neoforged.neoforge.common.NeoForge;

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
                blade.enchant(player.registryAccess().holderOrThrow(Enchantments.SHARPNESS),1);
                var menu=new AnvilMenu(20+i,player.getInventory());menu.getSlot(0).set(blade);menu.getSlot(1).set(new ItemStack(materials.get(i),4));menu.setItemName("r87 repair");menu.createResult();
                var output=menu.getSlot(2).getItem();var out=BladeStateAccess.of(output).orElseThrow();
                check(output.getDamageValue()==damage[i] && out.getMaxDamage()==100 && out.getRefine()==301 && out.getProudSoulCount()==soul[i],"material factor, single refine, no max-durability growth "+i);
                check(menu.getCost()==cost[i] && menu.getSlot(2).mayPickup(player),"actual anvil level cost "+i);
                int before=player.experienceLevel;menu.getSlot(2).onTake(player,output);
                check(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().getCount()==3 && player.experienceLevel==before-cost[i],"actual output pickup pays one material "+i);
                check(state.getRefine()==300 && blade.getDamageValue()==75,"input blade unchanged");
                rows.add(Map.of("material",materials.get(i).toString(),"damage",output.getDamageValue(),"souls",out.getProudSoulCount(),"cost",cost[i],"refine",out.getRefine()));
            }
            // Every actual addon definition retains model, SA, SE, enchants and item identity after anvil copy.
            int addonCount=0;
            for(var entry:player.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements().toList()) {
                if(!Set.of("prinegorerouse","si_slashblade").contains(entry.key().location().getNamespace()))continue;
                var blade=entry.value().getBlade(player.registryAccess());var state=BladeStateAccess.of(blade).orElseThrow();
                var menu=new AnvilMenu(50,player.getInventory());menu.getSlot(0).set(blade);menu.getSlot(1).set(new ItemStack(SlashBladeItems.PROUDSOUL.get()));menu.createResult();
                var output=menu.getSlot(2).getItem();var out=BladeStateAccess.of(output).orElseThrow();
                check(output.getItem()==blade.getItem() && out.getModel().equals(state.getModel()) && out.getTexture().equals(state.getTexture()) && out.getSlashArtsKey().equals(state.getSlashArtsKey()) && out.getSpecialEffects().equals(state.getSpecialEffects()) && EnchantmentHelper.getEnchantmentsForCrafting(output).equals(EnchantmentHelper.getEnchantmentsForCrafting(blade)),"addon anvil identity "+entry.key());addonCount++;
            }
            rows.add(Map.of("addon_anvil_definitions",addonCount));
            var blade=fresh(original);var state=BladeStateAccess.of(blade).orElseThrow();state.setMaxDamage(100);blade.setDamageValue(75);state.setProudSoulCount(0);
            blade.enchant(player.registryAccess().holderOrThrow(Enchantments.UNBREAKING),1);blade.set(DataComponents.CUSTOM_NAME,Component.literal("passive repair"));
            player.getInventory().setItem(1,blade);player.experienceLevel=10;player.totalExperience=100;player.experienceProgress=.5f;
            while(player.level().getGameTime()%20!=0)InputClock.next(player);
            blade.inventoryTick(player.level(),player,1,false);check(blade.getDamageValue()==74 && state.getProudSoulCount()==10 && player.totalExperience==90,"one healthy hotbar repair per second costs 10 raw XP");
            InputClock.next(player);blade.inventoryTick(player.level(),player,1,false);check(blade.getDamageValue()==74,"no repair on intervening tick");
            while(player.level().getGameTime()%20!=0)InputClock.next(player);
            state.setBroken(true);int beforeXP=player.experienceLevel;blade.inventoryTick(player.level(),player,1,false);check(blade.getDamageValue()==64 && player.experienceLevel==beforeXP-1 && state.getProudSoulCount()==30,"broken hotbar repairs ten percent for one level");
            player.experienceLevel=0;var tiny=new ItemStack(SlashBladeItems.PROUDSOUL_TINY.get(),2);player.getInventory().setItem(2,tiny);blade.inventoryTick(player.level(),player,1,false);
            check(blade.getDamageValue()==54 && tiny.getCount()==1 && state.getProudSoulCount()==30,"plain tiny soul replaces XP without new souls");
            tiny.enchant(player.registryAccess().holderOrThrow(Enchantments.POWER),1);blade.inventoryTick(player.level(),player,1,false);check(blade.getDamageValue()==54 && tiny.getCount()==1,"enchanted tiny soul protected");
            player.experienceLevel=10;player.getInventory().setItem(1,ItemStack.EMPTY);player.getInventory().setItem(10,blade);blade.inventoryTick(player.level(),player,10,false);check(blade.getDamageValue()==54,"non-hotbar inventory does not self repair");
            player.getInventory().setItem(10,ItemStack.EMPTY);player.setItemInHand(InteractionHand.MAIN_HAND,blade);state.setComboSeq(ComboStateRegistry.NONE.getId());blade.inventoryTick(player.level(),player,player.getInventory().selected,true);check(blade.getDamageValue()==54,"selected blade does not self repair");
            state.setProudSoulCount(0);state.setRefine(0);blade.set(DataComponents.REPAIR_COST,7);blade.inventoryTick(player.level(),player,player.getInventory().selected,true);
            check(state.getProudSoulCount()==200 && state.getRefine()==1 && blade.getOrDefault(DataComponents.REPAIR_COST,0)==0,"prior anvil work converted once");
            blade.inventoryTick(player.level(),player,player.getInventory().selected,true);check(state.getProudSoulCount()==200 && state.getRefine()==1,"no repeated prior work credit");
            Consumer<RefineProgressEvent> cancel=e->e.setCanceled(true);NeoForge.EVENT_BUS.addListener(cancel);
            try{var menu=new AnvilMenu(70,player.getInventory());menu.getSlot(0).set(blade);menu.getSlot(1).set(new ItemStack(SlashBladeItems.PROUDSOUL.get()));menu.createResult();check(menu.getSlot(2).getItem().isEmpty(),"refine cancellation cannot fall through vanilla repair");}finally{NeoForge.EVENT_BUS.unregister(cancel);}
            rows.add(Map.of("inventory","healthy/broken/material, cadence, slots, selected exclusion, prior work idempotence","cancellation",true));
        } finally {player.getInventory().clearContent();for(int i=0;i<inventory.size();i++)player.getInventory().setItem(i,inventory.get(i));player.setItemInHand(InteractionHand.MAIN_HAND,original);player.experienceLevel=xp;player.totalExperience=total;player.experienceProgress=progress;}
    }
    private static ItemStack fresh(ItemStack original){var blade=original.copy();blade.remove(DataComponents.REPAIR_COST);blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);var state=BladeStateAccess.of(blade).orElseThrow();state.setBroken(false);state.setSealed(false);state.setSpecialEffects(new net.minecraft.nbt.ListTag());return blade;}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError("r87 repair: "+message);}
}
