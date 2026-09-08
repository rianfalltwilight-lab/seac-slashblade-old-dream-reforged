package org.scex.slashbladelegacy.contracts;

import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.neoforged.neoforge.common.NeoForge;
import org.scex.slashbladelegacy.*;

final class Broken17Contracts {
    static void run(ServerPlayer player,Map<String,Object> report) {
        var original=player.getMainHandItem();boolean creative=player.getAbilities().instabuild;
        var drops=new ArrayList<ItemEntity>();Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> collect=e->{if(e.getEntity() instanceof ItemEntity item && item.distanceToSqr(player)<4)drops.add(item);};
        NeoForge.EVENT_BUS.addListener(collect);var rows=new ArrayList<Map<String,Object>>();report.put("legacy17_broken",rows);
        try {
            player.getAbilities().instabuild=false;
            var boundary=fresh(original);boundary.setDamageValue(99);player.setItemInHand(InteractionHand.MAIN_HAND,boundary);
            LegacyAdditionalAttack.wear(boundary,1,player);check(boundary.getDamageValue()==100 && !BladeStateAccess.of(boundary).orElseThrow().isBroken() && drops.isEmpty(),"zero durability survives exact maximum");
            LegacyAdditionalAttack.wear(boundary,1,player);check(boundary.getDamageValue()==100 && BladeStateAccess.of(boundary).orElseThrow().isBroken() && drops.size()==1,"next wear breaks and retains maximum damage");clear(drops);
            int[] points={0,500,1000,1100,2000},counts={1,6,11,4,9},remaining={0,0,0,800,1200};
            for(int i=0;i<points.length;i++) {
                clear(drops);var blade=fresh(original);var state=BladeStateAccess.of(blade).orElseThrow();state.setProudSoulCount(points[i]);player.setItemInHand(InteractionHand.MAIN_HAND,blade);
                LegacyAdditionalAttack.wear(blade,1,player);
                check(state.isBroken() && !blade.isEmpty() && state.getProudSoulCount()==remaining[i],"real blade proud souls "+points[i]);
                check(drops.size()==1 && drops.getFirst().getItem().is(SlashBladeItems.PROUDSOUL.get()) && drops.getFirst().getItem().getCount()==counts[i],"normal soul count "+points[i]);
                LegacyAdditionalAttack.wear(blade,1,player);check(drops.size()==1 && state.getProudSoulCount()==remaining[i],"already broken cannot duplicate loot");
                rows.add(Map.of("before_souls",points[i],"normal_souls_dropped",counts[i],"remaining_souls",remaining[i]));
            }
            clear(drops);var blade=fresh(original);var state=BladeStateAccess.of(blade).orElseThrow();player.setItemInHand(InteractionHand.MAIN_HAND,blade);
            var unbreaking=player.registryAccess().holderOrThrow(Enchantments.UNBREAKING);blade.enchant(unbreaking,1);
            long prevent=0;while(net.minecraft.util.RandomSource.create(prevent).nextInt(2)==0)prevent++;
            player.getRandom().setSeed(prevent);LegacyAdditionalAttack.wear(blade,1,player);
            check(!state.isBroken() && blade.getDamageValue()==100 && drops.isEmpty(),"Unbreaking prevents break before callback");
            blade.enchant(player.registryAccess().holderOrThrow(Enchantments.LOOTING),3);
            for(var key:List.of(Enchantments.POWER,Enchantments.PUNCH,Enchantments.FORTUNE,Enchantments.RESPIRATION))blade.enchant(player.registryAccess().holderOrThrow(key),1);
            long damage=0;while(net.minecraft.util.RandomSource.create(damage).nextInt(2)!=0)damage++;
            player.getRandom().setSeed(damage);LegacyAdditionalAttack.wear(blade,1,player);
            check(state.isBroken() && blade.getEnchantmentLevel(unbreaking)==0 && EnchantmentHelper.getEnchantmentsForCrafting(blade).size()==5,"six enchants remove actual Unbreaking I");
            check(drops.size()==3 && drops.stream().filter(e->e.getItem().is(SlashBladeItems.PROUDSOUL_TINY.get())).count()==2,"two rare tiny souls plus normal");
            for(var drop:drops)if(drop.getItem().is(SlashBladeItems.PROUDSOUL_TINY.get()))check(EnchantmentHelper.getEnchantmentsForCrafting(drop.getItem()).entrySet().stream().allMatch(e->LegacyEnchantments.rare(e.getKey()) && e.getIntValue()==1),"old eight-enchantment rare pool");
            clear(drops);blade=fresh(original);state=BladeStateAccess.of(blade).orElseThrow();player.setItemInHand(InteractionHand.MAIN_HAND,blade);
            int lvl=1;for(var key:List.of(Enchantments.SHARPNESS,Enchantments.SMITE,Enchantments.BANE_OF_ARTHROPODS,Enchantments.FIRE_ASPECT,Enchantments.KNOCKBACK,Enchantments.LOOTING))blade.enchant(player.registryAccess().holderOrThrow(key),lvl++);
            var before=EnchantmentHelper.getEnchantmentsForCrafting(blade);LegacyAdditionalAttack.wear(blade,1,player);
            var extracted=drops.stream().map(ItemEntity::getItem).filter(s->s.is(SlashBladeItems.PROUDSOUL.get()) && s.isEnchanted()).toList();
            check(extracted.size()==1 && EnchantmentHelper.getEnchantmentsForCrafting(blade).size()==5,"six enchants without Unbreaking transfers one actual enchant");
            var entry=EnchantmentHelper.getEnchantmentsForCrafting(extracted.getFirst()).entrySet().iterator().next();
            check(before.getLevel(entry.getKey())==entry.getIntValue() && blade.getEnchantmentLevel(entry.getKey())==0,"transferred level conserved on real blade");
            clear(drops);blade=fresh(original);state=BladeStateAccess.of(blade).orElseThrow();player.setItemInHand(InteractionHand.MAIN_HAND,blade);
            Consumer<SlashBladeEvent.BreakEvent> cancel=e->e.setCanceled(true);NeoForge.EVENT_BUS.addListener(cancel);
            try{LegacyAdditionalAttack.wear(blade,1,player);check(!state.isBroken() && drops.isEmpty(),"BreakEvent protection prevents reward");}finally{NeoForge.EVENT_BUS.unregister(cancel);}
            player.getAbilities().instabuild=true;LegacyAdditionalAttack.wear(blade,1,player);check(!state.isBroken() && drops.isEmpty() && blade.getDamageValue()==100,"creative preserves durability before break check");
            player.getAbilities().instabuild=false;blade.hurtAndBreak(1,player.serverLevel(),player,item->{});check(state.isBroken() && drops.size()==1,"framework callback without loot helper still awards once");
            rows.add(Map.of("unbreaking_order",true,"rare_pool",8,"six_enchant_transfer",true,"protection",true,"creative",true,"generic_callback",true));
        } finally{clear(drops);NeoForge.EVENT_BUS.unregister(collect);player.getAbilities().instabuild=creative;player.setItemInHand(InteractionHand.MAIN_HAND,original);}
    }
    private static void clear(List<ItemEntity> drops){drops.forEach(ItemEntity::discard);drops.clear();}
    private static ItemStack fresh(ItemStack original){var blade=original.copy();blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);blade.remove(DataComponents.UNBREAKABLE);var state=BladeStateAccess.of(blade).orElseThrow();state.setBroken(false);state.setSealed(false);state.setMaxDamage(100);state.setProudSoulCount(0);state.setSpecialEffects(new net.minecraft.nbt.ListTag());blade.setDamageValue(100);return blade;}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError("r87 broken: "+message);}
}
