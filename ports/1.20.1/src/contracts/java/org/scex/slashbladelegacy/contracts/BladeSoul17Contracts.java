package org.scex.slashbladelegacy.contracts;

import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.SlashBlade.RegistryEvents;
import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.MinecraftForge;
import org.scex.slashbladelegacy.*;

final class BladeSoul17Contracts {
    static void run(ServerPlayer player,Map<String,Object> report) {
        var level=player.serverLevel();var original=player.getMainHandItem();boolean creative=player.getAbilities().instabuild;
        var stand=RegistryEvents.BladeStand.create(level);stand.setPos(player.getX(),player.getY(),player.getZ()+1);stand.currentType=SlashBladeItems.BLADESTAND_2.get();stand.setItem(original.copy());level.addFreshEntity(stand);
        var drops=new ArrayList<ItemEntity>();Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> collect=e->{if(e.getEntity() instanceof ItemEntity item && item.distanceToSqr(stand)<9)drops.add(item);};MinecraftForge.EVENT_BUS.addListener(collect);
        var rows=new ArrayList<Map<String,Object>>();report.put("legacy17_blade_souls",rows);
        try {
            player.getAbilities().instabuild=false;var state=BladeStateAccess.of(stand.getItem()).orElseThrow();state.setProudSoulCount(0);
            var tiny=new ItemStack(SlashBladeItems.PROUDSOUL_TINY.get(),3);player.setItemInHand(InteractionHand.MAIN_HAND,tiny);
            hit(player,stand);check(stand.isOnFire() && tiny.getCount()==2 && BladeStateAccess.of(stand.getItem()).orElseThrow().getProudSoulCount()==50,"tiny ignites and awards fifty souls");
            hit(player,stand);check(!stand.isOnFire() && tiny.getCount()==2,"extinguish free");
            player.getAbilities().instabuild=true;hit(player,stand);check(tiny.getCount()==1,"old creative consumption");player.getAbilities().instabuild=false;
            var soul=new ItemStack(SlashBladeItems.PROUDSOUL.get(),3);player.setItemInHand(InteractionHand.MAIN_HAND,soul);hit(player,stand);
            check(drops.isEmpty() && soul.getCount()==3 && !stand.getItem().isEmpty(),"insufficient blade souls preserves blade and material");
            BladeStateAccess.of(stand.getItem()).orElseThrow().setProudSoulCount(2800);hit(player,stand);
            check(drops.size()==1 && soul.getCount()==2 && !LegacyBladeSouls.named(drops.get(0).getItem()) && BladeStateAccess.of(stand.getItem()).orElseThrow().getProudSoulCount()==2400,"plain extraction four hundred");
            var floating=drops.get(0);var position=floating.position();floating.setPos(position.add(5,2,3));LegacyBladeSouls.tick(floating);check(floating.position().equals(position),"floating crystal position");
            var saved=new CompoundTag();floating.saveWithoutId(saved);var restored=new ItemEntity(level,0,0,0,ItemStack.EMPTY);restored.load(saved);check(restored.getPersistentData().contains("Legacy17SoulFloatUntil"),"float saved");
            restored.getPersistentData().putLong("Legacy17SoulFloatUntil",level.getGameTime());LegacyBladeSouls.tick(restored);check(!restored.getPersistentData().contains("Legacy17SoulFloatUntil"),"float expires");
            soul=new ItemStack(SlashBladeItems.PROUDSOUL.get(),6);soul.enchant(Enchantments.POWER_ARROWS,1);player.setItemInHand(InteractionHand.MAIN_HAND,soul);
            var order=new ArrayList<String>();for(int i=0;i<6;i++){hit(player,stand);var drop=drops.get(i+1).getItem();check(LegacyBladeSouls.named(drop),"named extraction "+i);order.add(drop.getOrCreateTag().copy().getString(LegacyBladeSouls.DEFINITION));}
            check(new HashSet<>(order).size()==6 && order.get(0).equals(LegacyBladeSouls.POOL.get(1).toString()) && soul.isEmpty(),"dual stand visits six registered native souls");rows.add(Map.of("lottery_order",order));
            var standSave=new CompoundTag();stand.saveWithoutId(standSave);var copy=RegistryEvents.BladeStand.create(level);copy.load(standSave);check(copy.getPersistentData().getInt("Legacy17LastLotNumber")==stand.getPersistentData().getInt("Legacy17LastLotNumber"),"lottery position saved");
            copy.getPersistentData().putLong("Legacy17StandFireUntil",level.getGameTime());LegacyBladeSouls.tick(copy);check(!copy.isOnFire(),"stand fire expires");
            for(var key:LegacyBladeSouls.POOL) {
                var blank=new ItemStack(SlashBladeItems.SLASHBLADE.get());var before=BladeStateAccess.of(blank).orElseThrow();
                // A raw 1.20 stack keeps its capability empty until a definition initializes it.
                before.setNonEmpty();before.setProudSoulCount(1200);before.setKillCount(4321);before.setRefine(81);blank.enchant(Enchantments.MOB_LOOTING,3);blank.setDamageValue(20);
                check(blank.getDamageValue()==20,"initialized blank starts with damage twenty "+key);
                var inputSnapshot=blank.save(new CompoundTag()).copy();var stateSnapshot=before.serializeNBT().copy();
                var crystal=LegacyBladeSouls.crystal(player,key);var crystalSnapshot=crystal.save(new CompoundTag()).copy();
                var menu=new AnvilMenu(80,player.getInventory());menu.getSlot(0).set(blank);menu.getSlot(1).set(crystal);menu.createResult();var out=menu.getSlot(2).getItem();check(!out.isEmpty(),"named crystal output "+key);var after=BladeStateAccess.of(out).orElseThrow();
                check(after.getProudSoulCount()==200 && after.getRefine()==82 && after.getKillCount()==4321 && out.getDamageValue()==0 && out.getEnchantmentLevel(Enchantments.MOB_LOOTING)==3,"named awakening keeps progress and Looting III "+key);
                boolean inputUnchanged=blank.save(new CompoundTag()).equals(inputSnapshot),stateUnchanged=before.serializeNBT().equals(stateSnapshot),crystalUnchanged=crystal.save(new CompoundTag()).equals(crystalSnapshot);
                rows.add(Map.of("awakening",key.toString(),"input_nbt_unchanged",inputUnchanged,"input_capability_unchanged",stateUnchanged,"crystal_nbt_unchanged",crystalUnchanged,"input_damage",blank.getDamageValue(),"input_souls",before.getProudSoulCount()));
                check(inputUnchanged && stateUnchanged && crystalUnchanged && before.getProudSoulCount()==1200 && blank.getDamageValue()==20,"named input and material unchanged "+key);
                var expected=player.level().registryAccess().registryOrThrow(SlashBladeDefinition.REGISTRY_KEY).getHolderOrThrow(ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY,key)).value().getBlade();
                check(after.getTranslationKey().equals(BladeStateAccess.of(expected).orElseThrow().getTranslationKey()),"real registered identity "+key);
                before.setProudSoulCount(999);menu.getSlot(0).set(blank);menu.createResult();check(menu.getSlot(2).getItem().isEmpty(),"insufficient awakening souls "+key);
            }
            int addons=0;for(var entry:player.level().registryAccess().registryOrThrow(SlashBladeDefinition.REGISTRY_KEY).holders().toList()) {
                if(!Set.of("prinegorerouse","si_slashblade").contains(entry.key().location().getNamespace()))continue;
                var blade=entry.value().getBlade();BladeStateAccess.of(blade).orElseThrow().setProudSoulCount(5000);var before=blade.save(new net.minecraft.nbt.CompoundTag());
                var menu=new AnvilMenu(81,player.getInventory());menu.getSlot(0).set(blade);menu.getSlot(1).set(LegacyBladeSouls.crystal(player,LegacyBladeSouls.POOL.get(0)));menu.createResult();
                check(menu.getSlot(2).getItem().isEmpty() && blade.save(new net.minecraft.nbt.CompoundTag()).equals(before),"named soul cannot overwrite addon identity "+entry.key());addons++;
            }
            var unknown=LegacyBladeSouls.crystal(player,LegacyBladeSouls.POOL.get(0));var tag=unknown.getOrCreateTag().copy();tag.putString(LegacyBladeSouls.DEFINITION,"missing:blade");unknown.setTag(tag);
            var blank=new ItemStack(SlashBladeItems.SLASHBLADE.get());BladeStateAccess.of(blank).orElseThrow().setProudSoulCount(1000);check(!LegacyBladeSouls.apply(blank,unknown,player.level().registryAccess()),"missing definition rejects safely");
            rows.add(Map.of("addon_identities_protected",addons,"native_awakenings",6,"floating_and_stand_save_expiry",true,"insufficient_material",true));
        } finally{drops.forEach(ItemEntity::discard);MinecraftForge.EVENT_BUS.unregister(collect);stand.discard();player.setItemInHand(InteractionHand.MAIN_HAND,original);player.getAbilities().instabuild=creative;}
    }
    private static void hit(ServerPlayer player,mods.flammpfeil.slashblade.entity.BladeStandEntity stand){stand.hurt(player.damageSources().playerAttack(player),1);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError("r87 blade soul: "+message);}
}
