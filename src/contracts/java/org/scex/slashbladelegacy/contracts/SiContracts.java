package org.scex.slashbladelegacy.contracts;

import java.util.*;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.InteractionHand;
import org.scex.slashbladelegacy.*;

/** Runs against the actual optional SI addon jar; no compile-time replacement API. */
final class SiContracts {
    static void run(ServerPlayer player,Map<String,Object> report) throws Exception {
        if(!net.neoforged.fml.ModList.get().isLoaded("si_slashblade")) {
            report.put("si_energy_persistence","SKIP: SI addon absent");return;
        }
        var saved=player.getMainHandItem();var pos=player.position();int xp=player.experienceLevel;
        var results=new TreeMap<String,Object>();
        report.put("si_energy_persistence",results);
        player.setPos(24,160,0);player.setYRot(0);player.setXRot(0);player.setOnGround(true);player.experienceLevel=0;
        try {
            for(var item:BuiltInRegistries.ITEM) {
                if(!item.getClass().getName().equals("org.scex.sislashblade.ElectricBladeItem"))continue;
                var blade=new ItemStack(item);var type=item.getClass();
                var get=type.getMethod("getEnergy",ItemStack.class);
                long capacity=(long)type.getMethod("getMaxEnergy").invoke(item);
                type.getMethod("setEnergy",ItemStack.class,long.class).invoke(item,blade,capacity);
                // Isolate durability accounting from Unbreaking RNG and native SE energy spending.
                blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);
                var state=BladeStateAccess.of(blade).orElseThrow();state.setSpecialEffects(new net.minecraft.nbt.ListTag());
                player.setItemInHand(InteractionHand.MAIN_HAND,blade);
                var target=net.minecraft.world.entity.EntityType.ZOMBIE.create(player.serverLevel());
                target.setPos(24,160,1.5);
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100000);
                target.setHealth(100000);player.serverLevel().addFreshEntity(target);
                try {
                    require(player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class,player.getBoundingBox().inflate(4)).contains(target),"SI test target is not in a loaded accessible chunk");
                    state.updateComboSeq(player,LegacyCombat.id(LegacyMove.SAYA1));
                    require((long)get.invoke(item,blade)==capacity,"Scabbard spent SI energy");
                    target.invulnerableTime=0;
                    state.updateComboSeq(player,LegacyCombat.id(LegacyMove.BATTOU));
                } finally {target.discard();}
                long after=(long)get.invoke(item,blade);
                results.put(BuiltInRegistries.ITEM.getKey(item).toString(),Map.of("capacity",capacity,"after",after,"damage",blade.getDamageValue(),"target_health",target.getHealth(),"combo",state.getComboSeq().toString(),"creative",player.getAbilities().instabuild));
                require(after>=0 && after<capacity,"Blade edge failed to spend SI energy: "+BuiltInRegistries.ITEM.getKey(item)+" "+results);
                var restored=ItemStack.parseOptional(player.registryAccess(),(net.minecraft.nbt.CompoundTag)blade.save(player.registryAccess()));
                require(restored.is(item) && (long)get.invoke(item,restored)==after,"SI energy lost across item serialization");
                results.put(BuiltInRegistries.ITEM.getKey(item).toString(),Map.of("capacity",capacity,"edge_cost",capacity-after,"scabbard_free",true,"item_roundtrip",true));
                for(var arc:player.serverLevel().getEntitiesOfClass(mods.flammpfeil.slashblade.entity.EntitySlashEffect.class,player.getBoundingBox().inflate(20)))arc.discard();
            }
            require(!results.isEmpty(),"SI loaded without electric blade coverage");
            report.put("si_energy_persistence",results);
        } finally {player.setItemInHand(InteractionHand.MAIN_HAND,saved);player.setPos(pos);player.experienceLevel=xp;}
    }
    private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
}


