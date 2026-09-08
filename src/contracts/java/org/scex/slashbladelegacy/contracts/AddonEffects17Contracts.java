package org.scex.slashbladelegacy.contracts;

import java.util.*;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.fml.ModList;
import org.scex.slashbladelegacy.*;

/** Test original addon callbacks through item update and real old melee; no synthetic HitEvents. */
final class AddonEffects17Contracts {
    static void run(ServerPlayer player,Map<String,Object> report) throws Exception {
        var saved=player.getMainHandItem();int xp=player.experienceLevel;var rows=new TreeMap<String,Object>();report.put("legacy17_addon_effects",rows);
        var enemy=EntityType.ZOMBIE.create(player.level());enemy.setNoAi(true);enemy.setNoGravity(true);enemy.setPos(24,160,2);
        enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);enemy.getAttribute(Attributes.ARMOR).setBaseValue(0);enemy.setHealth(1000);player.serverLevel().addFreshEntity(enemy);
        try {
            player.setPos(24,160,0);player.setOnGround(true);player.setYRot(0);player.setXRot(0);player.fallDistance=0;
            if(ModList.get().isLoaded("prinegorerouse")) {
                for(String effect:List.of("absolute_power","back","clear","reverse_power")) {
                    var blade=fresh(player,saved,"prinegorerouse:"+effect);player.startUsingItem(InteractionHand.MAIN_HAND);
                    blade.inventoryTick(player.level(),player,0,true);
                    var expected=switch(effect){case "absolute_power" -> MobEffects.MOVEMENT_SPEED;case "back" -> MobEffects.DAMAGE_BOOST;case "clear" -> MobEffects.NIGHT_VISION;default -> MobEffects.FIRE_RESISTANCE;};
                    check(player.hasEffect(expected),effect+" selected UpdateEvent lost");rows.put("prinegorerouse:"+effect+"/update",true);
                }
                for(String effect:List.of("back","fate","porgatory","eternity","empty")) {
                    cleanup(player);enemy.setHealth(1000);enemy.removeAllEffects();enemy.setPos(24,160,2);enemy.invulnerableTime=0;
                    var blade=fresh(player,saved,"prinegorerouse:"+effect);var state=BladeStateAccess.of(blade).orElseThrow();
                    if(effect.equals("back") || effect.equals("fate"))seed("net.xianyu.prinegorerouse.specialeffect."+(effect.equals("back")?"Back":"Fate"));
                    if(effect.equals("eternity"))enemy.getPersistentData().putInt("blackhole",3);
                    int wear=blade.getDamageValue();state.updateComboSeq(player,LegacyCombat.id(LegacyMove.BATTOU));
                    switch(effect) {
                        case "back" -> check(blade.getDamageValue()==wear,"Back repair callback");
                        case "fate" -> check(player.hasEffect(MobEffects.LUCK) && count(player,"prinegorerouse")>0,"Fate luck/sword");
                        case "porgatory" -> check(enemy.getEffect(MobEffects.MOVEMENT_SLOWDOWN)!=null && enemy.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier()==20,"Porgatory slowdown");
                        case "eternity" -> check(enemy.getHealth()<900 && enemy.getPersistentData().getInt("blackhole")==0 && count(player,"prinegorerouse")>0,"Eternity half-hit/cut");
                        case "empty" -> check(player.getMainHandItem().isEmpty() && enemy.getHealth()<=3,"Empty consumes only actual held blade");
                    }
                    rows.put("prinegorerouse:"+effect+"/hit",Map.of("damage",1000-enemy.getHealth(),"callback",true));
                }
                // Level gate still belongs to the addon; an inactive Eternity breaks its real blade.
                var low=fresh(player,saved,"prinegorerouse:eternity");player.experienceLevel=1;enemy.setHealth(1000);enemy.setPos(24,160,2);
                BladeStateAccess.of(low).orElseThrow().updateComboSeq(player,LegacyCombat.id(LegacyMove.BATTOU));
                check(BladeStateAccess.of(low).orElseThrow().isBroken(),"Eternity original level gate");rows.put("prinegorerouse:eternity/level_gate",true);
            }
            if(ModList.get().isLoaded("si_slashblade")) {
                for(String item:List.of("mrblade_final","kineticenergyblade_final","fox_faerie","bloodrev_extra","craftrev_extra")) {
                    cleanup(player);enemy.setHealth(1000);enemy.removeAllEffects();enemy.setPos(24,160,2);enemy.invulnerableTime=0;
                    var blade=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("si_slashblade:"+item)));
                    var type=blade.getItem().getClass();long capacity=(long)type.getMethod("getMaxEnergy").invoke(blade.getItem());
                    type.getMethod("setEnergy",ItemStack.class,long.class).invoke(blade.getItem(),blade,capacity);
                    player.stopUsingItem();player.removeAllEffects();player.experienceLevel=300;player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                    var state=BladeStateAccess.of(blade).orElseThrow();player.getRandom().setSeed(0);
                    state.updateComboSeq(player,LegacyCombat.id(LegacyMove.BATTOU));
                    switch(item) {
                        case "mrblade_final" -> check(enemy.getHealth()<800,"MR fractional SE");
                        case "kineticenergyblade_final" -> {int count=count(player,"si_slashblade");check(count>0,"Kinetic SE sword");state.updateComboSeq(player,LegacyCombat.id(LegacyMove.BATTOU));check(count(player,"si_slashblade")==count,"Kinetic per-blade cooldown: before="+count+", after="+count(player,"si_slashblade"));}
                        case "fox_faerie" -> check(enemy.hasEffect(MobEffects.WEAKNESS),"Fox SE debuff");
                        case "bloodrev_extra" -> check(player.getEffect(MobEffects.ABSORPTION)!=null && player.getEffect(MobEffects.ABSORPTION).getAmplifier()==3,"Blood SE absorption");
                        case "craftrev_extra" -> check(enemy.isOnFire() && count(player,"si_slashblade")>0,"Craft SE sword/fire");
                    }
                    rows.put("si_slashblade:"+item+"/hit",true);
                }
            }
        } finally {cleanup(player);enemy.discard();player.stopUsingItem();player.removeAllEffects();player.setItemInHand(InteractionHand.MAIN_HAND,saved);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);player.experienceLevel=xp;}
    }
    private static ItemStack fresh(ServerPlayer player,ItemStack original,String effect) {
        player.stopUsingItem();player.removeAllEffects();player.setHealth(player.getMaxHealth());player.experienceLevel=300;player.getData(CapabilityConcentrationRank.RANK_POINT).setRawRankPoint(0);
        var blade=original.copy();blade.remove(DataComponents.CUSTOM_DATA);blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);
        var state=BladeStateAccess.of(blade).orElseThrow();state.setSpecialEffects(new net.minecraft.nbt.ListTag());state.addSpecialEffect(ResourceLocation.parse(effect));
        state.setBroken(false);state.setSealed(false);blade.setDamageValue(20);state.setComboSeq(ComboStateRegistry.NONE.getId());state.setKillCount(0);
        player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);return blade;
    }
    private static void seed(String type) throws Exception {
        var field=Class.forName(type).getDeclaredField("random");field.setAccessible(true);var rng=(Random)field.get(null);
        for(long i=0;i<100000;i++)if(new Random(i).nextDouble()<.1){rng.setSeed(i);return;}
        throw new AssertionError("deterministic random seed");
    }
    private static int count(ServerPlayer player,String namespace){return player.serverLevel().getEntities(player,player.getBoundingBox().inflate(100),e->BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(namespace)).size();}
    private static void cleanup(ServerPlayer player){player.serverLevel().getEntities(player,player.getBoundingBox().inflate(100),e->Set.of("prinegorerouse","si_slashblade","slashblade","slashblade_legacy_compat").contains(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace())).forEach(Entity::discard);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
