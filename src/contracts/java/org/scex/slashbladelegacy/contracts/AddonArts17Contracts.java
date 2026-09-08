package org.scex.slashbladelegacy.contracts;

import java.util.*;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.scex.slashbladelegacy.*;

/** Every registered addon SA through real ItemSlashBlade.releaseUsing, with actual entity creation. */
final class AddonArts17Contracts {
    static void run(ServerPlayer player,Map<String,Object> report) throws Exception {
        var rows=new TreeMap<String,Object>();report.put("legacy17_addon_arts",rows);
        var saved=player.getMainHandItem();int xp=player.experienceLevel;long day=player.level().getDayTime();
        var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);
        var arts=new LinkedHashMap<String,Integer>();
        if(ModList.get().isLoaded("prinegorerouse")) {
            arts.put("prinegorerouse:zenith12th",13);arts.put("prinegorerouse:magnetic_storm_sword",53);
            arts.put("prinegorerouse:divine_cross_sa",1);arts.put("prinegorerouse:burning_fire_sa",3);
            arts.put("prinegorerouse:cosmic_line",3);arts.put("prinegorerouse:over_the_horizon",21);
        }
        if(ModList.get().isLoaded("si_slashblade")) {
            arts.put("si_slashblade:energy_level_transition",1);arts.put("si_slashblade:kinetic_impact",1);
            arts.put("si_slashblade:spear",25);arts.put("si_slashblade:slash_dimension",6);
            arts.put("si_slashblade:high_energy_particle_flow",10);arts.put("si_slashblade:blood_revolution",35);arts.put("si_slashblade:craft_revolution",8);
        }
        try {
            for(var entry:arts.entrySet()) {
                cleanup(player);InputClock.next(player);var id=ResourceLocation.parse(entry.getKey());
                var definition=player.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements()
                        .filter(h->h.key().location().getNamespace().equals(id.getNamespace()))
                        .filter(h->BladeStateAccess.of(h.value().getBlade(player.registryAccess())).orElseThrow().getSlashArtsKey().equals(id)).findFirst().orElseThrow(()->new AssertionError("No real blade for "+id));
                var blade=definition.value().getBlade(player.registryAccess());var state=BladeStateAccess.of(blade).orElseThrow();
                boolean needsEnchantment=!blade.isEnchanted();
                player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                player.setPos(24,160,0);player.setYRot(0);player.setXRot(0);player.setOnGround(true);player.fallDistance=0;player.removeAllEffects();player.experienceLevel=300;
                rank.setRawRankPoint(0);rank.setLastUpdte(player.level().getGameTime());state.setSpecialEffects(new net.minecraft.nbt.ListTag());state.setProudSoulCount(100000);
                state.setComboSeq(LegacyCombat.id(LegacyMove.SAYA1));state.setLastActionTime(player.level().getGameTime());
                long energy=0;
                if(id.getNamespace().equals("si_slashblade")) {
                    var type=blade.getItem().getClass();energy=(long)type.getMethod("getMaxEnergy").invoke(blade.getItem());
                    type.getMethod("setEnergy",ItemStack.class,long.class).invoke(blade.getItem(),blade,energy);
                }
                var enemy=EntityType.ZOMBIE.create(player.level());enemy.setNoAi(true);enemy.setNoGravity(true);enemy.setPos(24,160,12);
                enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);enemy.getAttribute(Attributes.ARMOR).setBaseValue(0);enemy.setHealth(1000);player.serverLevel().addFreshEntity(enemy);state.setTargetEntityId(enemy);
                try {
                    if(needsEnchantment) {
                        blade.releaseUsing(player.level(),player,blade.getUseDuration(player)-20);
                        check(state.getComboSeq().equals(LegacyCombat.id(LegacyMove.SAYA1)),"plain blade bypassed r87 enchantment requirement");
                        blade.enchant(player.registryAccess().holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING),1);
                    }
                    blade.releaseUsing(player.level(),player,blade.getUseDuration(player)-20);
                    var actual=state.getComboSeq();
                    check(actual.equals(id) || id.getNamespace().equals("si_slashblade") && actual.toString().equals("si_slashblade:legacy_kiriorosi"),"release did not run "+id+": "+actual);
                    if(id.getNamespace().equals("prinegorerouse")) {
                        for(int t:new int[]{2,3}) {state.setLastActionTime(player.level().getGameTime()-t);ComboStateRegistry.REGISTRY.get(id).tickAction(player);}
                        var end=ComboStateRegistry.REGISTRY.get(id).getNextOfTimeout(player);
                        check(LegacyCombat.visualMove(end)==LegacyMove.NOUTOU,"foreign recovery repeats slash "+end);
                    }
                    var spawned=entities(player,id.getNamespace());check(spawned.size()>=entry.getValue(),id+" generated "+spawned.size()+", need "+entry.getValue());
                    for(var entity:spawned) {
                        var tag=new net.minecraft.nbt.CompoundTag();entity.saveWithoutId(tag);
                        check(Double.isFinite(entity.getX()+entity.getY()+entity.getZ()+entity.getDeltaMovement().lengthSqr()),id+" nonfinite projectile");
                        check(!tag.isEmpty(),id+" entity did not serialize");
                    }
                    var row=new LinkedHashMap<String,Object>();row.put("blade",definition.key().location().toString());row.put("state",actual.toString());row.put("entities",spawned.size());row.put("initial_damage",1000-enemy.getHealth());row.put("requires_added_enchantment",needsEnchantment);
                    if(energy>0) {
                        long after=(long)blade.getItem().getClass().getMethod("getEnergy",ItemStack.class).invoke(blade.getItem(),blade);
                        check(after<energy,id+" lost EU/durability cost");row.put("eu_cost",energy-after);
                        if(id.getPath().contains("revolution") || id.getPath().equals("high_energy_particle_flow"))check(energy-after>=1000000,id+" million EU missing");
                    }
                    rows.put(id.toString(),row);
                } finally {enemy.discard();}
            }
        } finally {cleanup(player);player.serverLevel().setDayTime(day);player.stopUsingItem();player.removeAllEffects();player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);player.setItemInHand(InteractionHand.MAIN_HAND,saved);player.experienceLevel=xp;}
    }
    private static List<Entity> entities(ServerPlayer player,String namespace){return player.serverLevel().getEntities(player,player.getBoundingBox().inflate(100),e->BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(namespace));}
    private static void cleanup(ServerPlayer player){for(var ns:List.of("prinegorerouse","si_slashblade","slashblade","slashblade_legacy_compat"))entities(player,ns).forEach(Entity::discard);}
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
}
