package org.scex.slashbladelegacy.contracts;

import java.util.*;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
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

/** Actual optional addon jars and definitions; only the test clock is synthetic. */
final class Addon17Contracts {
    static void run(ServerPlayer player,Map<String,Object> report) throws Exception {
        var rows=new TreeMap<String,Object>();report.put("legacy17_addons",rows);
        var saved=player.getMainHandItem();int xp=player.experienceLevel;
        var target=EntityType.ZOMBIE.create(player.level());target.setPos(24,160,2);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.setNoAi(true);
        player.serverLevel().addFreshEntity(target);
        try {
            for(var definition:player.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements().toList()) {
                var id=definition.key().location();if(!Set.of("prinegorerouse","si_slashblade").contains(id.getNamespace()))continue;
                var blade=definition.value().getBlade(player.registryAccess());var state=BladeStateAccess.of(blade).orElseThrow();
                if(blade.getItem().getClass().getName().equals("org.scex.sislashblade.ElectricBladeItem")) {
                    var type=blade.getItem().getClass();long capacity=(long)type.getMethod("getMaxEnergy").invoke(blade.getItem());
                    type.getMethod("setEnergy",ItemStack.class,long.class).invoke(blade.getItem(),blade,capacity);
                }
                var model=state.getModel();var texture=state.getTexture();var effects=Set.copyOf(state.getSpecialEffects());var sa=state.getSlashArtsKey();
                player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                player.removeAllEffects();player.experienceLevel=0;player.setOnGround(true);player.fallDistance=0;
                player.getData(CapabilityConcentrationRank.RANK_POINT).setRawRankPoint(0);state.setComboSeq(ComboStateRegistry.NONE.getId());
                var damage=new ArrayList<Float>();
                for(var expected:new LegacyMove[]{LegacyMove.SAYA1,LegacyMove.SAYA2,LegacyMove.BATTOU}) {
                    target.setHealth(1000);target.setPos(24,160,2);InputClock.use(player,blade);
                    check(state.getComboSeq().equals(LegacyCombat.id(expected)),id+" input "+expected+" got "+state.getComboSeq());
                    damage.add(1000-target.getHealth());check(target.getHealth()<1000,id+" no hit "+expected);
                }
                check(state.getModel().equals(model) && state.getTexture().equals(texture) && Set.copyOf(state.getSpecialEffects()).equals(effects) && state.getSlashArtsKey().equals(sa),id+" identity changed");
                var copy=ItemStack.parse(player.registryAccess(),blade.save(player.registryAccess())).orElseThrow();
                check(Set.copyOf(BladeStateAccess.of(copy).orElseThrow().getSpecialEffects()).equals(effects),id+" SE codec");
                rows.put(id.toString(),Map.of("ordinary_damage",damage,"identity_and_codec",true,"sa",sa.toString()));
            }
            if(ModList.get().isLoaded("prinegorerouse")) {
                check(rows.keySet().stream().anyMatch(k->k.startsWith("prinegorerouse:")),"Negore definitions absent");
                var blade=saved.copy();var state=BladeStateAccess.of(blade).orElseThrow();state.setSpecialEffects(new net.minecraft.nbt.ListTag());
                state.addSpecialEffect(ResourceLocation.parse("prinegorerouse:phantom"));state.setBroken(false);state.setSealed(false);
                player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.experienceLevel=100;player.setHealth(5);
                target.setHealth(target.getMaxHealth()-100);state.updateComboSeq(player,LegacyCombat.id(LegacyMove.BATTOU));
                check(player.getHealth()==player.getMaxHealth(),"Negore Phantom SE missing old hit event");
                var offhand=blade.copy();var off=BladeStateAccess.of(offhand).orElseThrow();off.setSpecialEffects(new net.minecraft.nbt.ListTag());off.addSpecialEffect(ResourceLocation.parse("prinegorerouse:oracle"));
                player.setItemInHand(InteractionHand.OFF_HAND,offhand);
                ((Random)Class.forName("net.xianyu.prinegorerouse.specialeffect.Oracle").getField("random").get(null)).setSeed(0);
                long before=count(player,"prinegorerouse");state.updateComboSeq(player,LegacyCombat.id(LegacyMove.SAYA1));
                check(count(player,"prinegorerouse")>before,"Negore Oracle SE missing public slash event");
                player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                state.setComboSeq(LegacyCombat.id(LegacyMove.SAYA1));state.setLastActionTime(player.level().getGameTime());state.setSlashArtsKey(ResourceLocation.parse("prinegorerouse:zenith12th"));
                var result=state.doChargeAction(player,20);check(result.equals(state.getComboSeq()),"Negore SA did not start from ordinary combo");
                check(result.getNamespace().equals("prinegorerouse"),"Negore SA identity overwritten: "+result);
                before=count(player,"prinegorerouse");state.setLastActionTime(player.level().getGameTime()-3);ComboStateRegistry.REGISTRY.get(result).tickAction(player);
                check(count(player,"prinegorerouse")>=before+13,"Negore Zenith13 swords not spawned");
                rows.put("negore_specials",Map.of("phantom_hit",true,"oracle_slash",true,"charged_sa",result.toString(),"actual_projectiles",true));
            }
            if(ModList.get().isLoaded("si_slashblade")) {
                var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse("si_slashblade:fox_faerie"));var blade=new ItemStack(item);
                var type=item.getClass();var get=type.getMethod("getEnergy",ItemStack.class);long capacity=(long)type.getMethod("getMaxEnergy").invoke(item);
                type.getMethod("setEnergy",ItemStack.class,long.class).invoke(item,blade,capacity);
                var state=BladeStateAccess.of(blade).orElseThrow();player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.removeAllEffects();player.experienceLevel=100;
                target.setHealth(1000);state.updateComboSeq(player,LegacyCombat.id(LegacyMove.BATTOU));
                check(target.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS),"SI Fox SE missing old hit callback");
                state.setSpecialEffects(new net.minecraft.nbt.ListTag());target.removeAllEffects();target.setHealth(1000);target.setPos(24,160,12);
                state.setTargetEntityId(target);state.setSlashArtsKey(ResourceLocation.parse("si_slashblade:slash_dimension"));
                state.setComboSeq(LegacyCombat.id(LegacyMove.SAYA1));state.setLastActionTime(player.level().getGameTime());long before=(long)get.invoke(item,blade);
                var result=state.doChargeAction(player,20);
                check(result.equals(state.getComboSeq()) && result.toString().equals("si_slashblade:slash_dimension"),"SI SA not actually started: "+result);
                check(target.getHealth()<1000,"SI selected distant SA target was clipped by ordinary reach");
                long after=(long)get.invoke(item,blade);check(before-after>=10000,"SI dimension EU charge lost");
                rows.put("si_specials",Map.of("fox_se",true,"charged_sa",result.toString(),"target_distance",12,"damage",1000-target.getHealth(),"eu_cost",before-after));
            }
        } finally {
            target.discard();player.setItemInHand(InteractionHand.MAIN_HAND,saved);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);player.experienceLevel=xp;player.removeAllEffects();
            for(var entity:player.serverLevel().getEntities(player,player.getBoundingBox().inflate(80),e->Set.of("prinegorerouse","si_slashblade","slashblade").contains(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace())))entity.discard();
        }
    }
    private static long count(ServerPlayer player,String namespace){return player.serverLevel().getEntities(player,player.getBoundingBox().inflate(80),e->BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(namespace)).size();}
    private static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
}
