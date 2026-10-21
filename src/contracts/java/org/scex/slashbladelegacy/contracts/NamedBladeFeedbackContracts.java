package org.scex.slashbladelegacy.contracts;

import java.util.*;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import mods.flammpfeil.slashblade.util.InputCommand;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.scex.slashbladelegacy.*;

/** Executable default-art, resource-cost and optional API behavior checks. */
final class NamedBladeFeedbackContracts {
    static void run(ServerPlayer player,Map<String,Object> report) {
        var rows=new LinkedHashMap<String,Object>();report.put("named_blade_feedback",rows);
        var saved=player.getMainHandItem();var pos=player.position();int xp=player.experienceLevel;
        var blade=new ItemStack(SlashBladeItems.SLASHBLADE.get());blade.enchant(player.registryAccess().holderOrThrow(Enchantments.POWER),1);
        var state=BladeStateAccess.of(blade).orElseThrow();var target=EntityType.ZOMBIE.create(player.level());
        target.setNoAi(true);target.setNoGravity(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);
        player.serverLevel().addFreshEntity(target);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.setPos(-8,160,-8);player.setOnGround(true);player.setYRot(0);player.setXRot(0);
            target.setPos(-8,160,0);state.setTargetEntityId(target);state.setProudSoulCount(1000);state.setDefaultBewitched(true);
            state.setTranslationKey("item.slashblade.koseki");state.setSlashArtsKey(id("slashblade:drive_vertical"));
            check(SlashArts.getRegistryKey(state.getSlashArts()).equals(LegacyArts.key(LegacyArts.Art.WITHER)),"Koseki resolves Wither");
            check(state.getSlashArtsKey().equals(id("slashblade:drive_vertical")),"stored original SA is retained");
            state.setSlashArtsKey(id("slashblade:wave_edge"));check(SlashArts.getRegistryKey(state.getSlashArts()).equals(id("slashblade:wave_edge")),"transferred SA is retained");
            state.setTranslationKey("item.slashblade_addon.kamuy_none");state.setSlashArtsKey(id("slashblade:circle_slash"));
            check(SlashArts.getRegistryKey(state.getSlashArts()).equals(LegacyNamedBlades.PRAYER),"Kamuy resolves Prayer");
            var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);rank.setRawRankPoint(0);rank.setLastUpdte(player.level().getGameTime());
            state.setProudSoulCount(20);state.setComboSeq(LegacyCombat.id(LegacyMove.SAYA1));state.setLastActionTime(player.level().getGameTime());
            blade.releaseUsing(player.level(),player,blade.getUseDuration(player)-20);
            check(state.getProudSoulCount()==10 && rank.getRankPoint(player.level().getGameTime())==rank.getUnitCapacity(),"Prayer release pays 10 souls exactly once and awards rank");
            state.setProudSoulCount(9);long before=rank.getRawRankPoint();LegacyNamedBlades.prayer(player);
            check(state.getProudSoulCount()==9 && rank.getRawRankPoint()==before,"Prayer with insufficient souls grants no rank");
            rank.setRawRankPoint(rank.getMaxCapacity()-1);rank.setLastUpdte(player.level().getGameTime());state.setProudSoulCount(10);LegacyNamedBlades.prayer(player);
            check(rank.getRawRankPoint()==rank.getMaxCapacity(),"Prayer refreshes top rank");
            rows.put("default_arts_and_prayer_cost",true);

            state.setTranslationKey("item.slashblade_addon.terra_blade");state.setSlashArtsKey(id("slashblade_addon:rapid_blistering_swords"));
            check(SlashArts.getRegistryKey(state.getSlashArts()).equals(LegacyNamedBlades.TERRA),"Terra resolves green sword SA");
            LegacyNamedBlades.seed(blade);check(state.hasSpecialEffect(LegacyNamedBlades.MANA_BURST) && state.hasSpecialEffect(LegacyNamedBlades.MANA_REPAIR),"Terra seeds both mana effects");
            state.removeSpecialEffect(LegacyNamedBlades.MANA_BURST);LegacyNamedBlades.seed(blade);check(!state.hasSpecialEffect(LegacyNamedBlades.MANA_BURST),"transferred mana effect stays removed");
            state.addSpecialEffect(LegacyNamedBlades.MANA_BURST);
            rank.setRawRankPoint(0);rank.setLastUpdte(player.level().getGameTime());state.setProudSoulCount(1000);LegacyNamedBlades.terra(player);
            var swords=entities(player,LegacyPhantomSword.class);
            check(swords.size()==3 && swords.stream().allMatch(s->s.getColor()==3524113 && s.art()==LegacyRangeAttack.Art.STORM),"Terra emits three green target-orbit swords without mana");
            check(swords.stream().map(LegacyPhantomSword::interval).sorted().toList().equals(List.of(7,9,11)),"Terra staggered old launch intervals");
            check(state.getProudSoulCount()==960,"Terra 40 souls");swords.forEach(Entity::discard);
            rows.put("terra_defaults_and_swords",true);

            if(ModList.get().isLoaded("slashblade_addon")) {
                state.addSpecialEffect(id("slashblade_addon:burst_drive"));player.experienceLevel=30;
                NeoForge.EVENT_BUS.post(new SlashBladeEvent.DoSlashEvent(blade,state,player,0,false,1,KnockBacks.cancel));
                var drives=entities(player,LegacyDrive.class);check(drives.size()==1,"Burst Drive uses the legacy projectile once");
                var drive=drives.getFirst();check(drive.getLifetime()==10 && Math.abs(drive.getDeltaMovement().length()-1.5)<.00001,"Burst Drive has Quick Drive speed and lifetime");
                var from=drive.position();drive.tick();check(Math.abs(drive.position().distanceTo(from)-1.575)<.00001,"Burst Drive uses legacy acceleration");drive.discard();
                player.setYRot(37);player.setXRot(-12);float yawOffset=.6f;
                var sa=LegacyArts.drive(player,blade,1,1.5f,0,false,10);var expected=sa.getDeltaMovement().yRot(yawOffset);sa.discard();
                var angled=new SlashBladeEvent.DoSlashEvent(blade,state,player,0,false,1,KnockBacks.cancel);angled.setYRot(yawOffset);NeoForge.EVENT_BUS.post(angled);
                drives=entities(player,LegacyDrive.class);check(drives.size()==1,"angled Burst Drive emits one projectile");
                var actual=drives.getFirst().getDeltaMovement();var nativeDirection=player.getLookAngle().yRot(yawOffset).scale(1.5);
                // Entity's view vector and Vec3.directionFromRotation address different Mth table cells.
                // Two angular cells at speed 1.5 permit 0.000288 blocks/tick; the wrong sign differs by 1.65.
                rows.put("burst_drive_yaw",Map.of("actual",actual.toString(),"expected_sa",expected.toString(),"native",nativeDirection.toString(),"sa_error",actual.distanceTo(expected),"native_error",actual.distanceTo(nativeDirection)));
                check(actual.distanceTo(expected)<.00001 && actual.distanceTo(nativeDirection)<.0003,"nonzero event yaw retains native rotation and Drive SA direction: actual="+actual+", expected="+expected+", native="+nativeDirection);
                drives.getFirst().discard();player.setYRot(0);player.setXRot(0);
                state.removeSpecialEffect(id("slashblade_addon:burst_drive"));rows.put("burst_drive_trajectory",true);
            }else rows.put("burst_drive_trajectory","SJAP absent");

            optionalBotania(player,blade,rows);
            optionalZephyr(player,blade,target,rows);
            transferableEffects(player,blade,rows);
            player.getPersistentData().putBoolean(LegacyMode.MODERN,true);LegacyMode.bind(blade,player);
            state.setTranslationKey("item.slashblade.koseki");state.setSlashArtsKey(id("slashblade:drive_vertical"));
            check(SlashArts.getRegistryKey(state.getSlashArts()).equals(id("slashblade:drive_vertical")),"modern mode retains native default");rows.put("modern_mode_default",true);
        }catch(ReflectiveOperationException failure){throw new AssertionError("named blade optional integration",failure);}
        finally {
            entities(player,LegacyPhantomSword.class).forEach(Entity::discard);entities(player,LegacyDrive.class).forEach(Entity::discard);
            target.discard();player.getPersistentData().remove(LegacyMode.MODERN);LegacyMode.bind(blade,player);player.stopUsingItem();player.swingTime=0;
            player.experienceLevel=xp;player.setItemInHand(InteractionHand.MAIN_HAND,saved);player.setPos(pos);player.setDeltaMovement(Vec3.ZERO);
        }
    }
    private static void optionalBotania(ServerPlayer player,ItemStack blade,Map<String,Object> rows) throws ReflectiveOperationException {
        if(!ModList.get().isLoaded("botania")){check(!LegacyManaBridge.consume(player,100),"no Botania needs no classes");rows.put("botania","absent: soft integration closed");return;}
        var tablet=new ItemStack(BuiltInRegistries.ITEM.get(id("botania:mana_tablet")));check(!tablet.isEmpty(),"mana tablet exists");
        var setMana=Class.forName("vazkii.botania.common.item.ManaTabletItem").getDeclaredMethod("setMana",ItemStack.class,int.class);setMana.setAccessible(true);setMana.invoke(null,tablet,100);
        int slot=(player.getInventory().selected+1)%9;var saved=player.getInventory().getItem(slot);player.getInventory().setItem(slot,tablet);
        try {
            check(LegacyManaBridge.consume(player,100) && !LegacyManaBridge.consume(player,1),"native mana is consumed exactly");
            setMana.invoke(null,tablet,100);var state=BladeStateAccess.of(blade).orElseThrow();state.setComboSeq(LegacyCombat.id(LegacyMove.KIRIAGE));player.swingTime=4;player.experienceLevel=10;
            LegacyManaBridge.playerTick(new PlayerTickEvent.Post(player));
            var bursts=player.level().getEntities(player,new AABB(-30,140,-30,30,190,30),e->BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals("botania:mana_burst"));
            check(bursts.size()==1 && !LegacyManaBridge.consume(player,1),"mana SE spawns one native burst for 100 mana");bursts.forEach(Entity::discard);player.swingTime=0;
            var at=new BlockPos(-8,158,-8);var previous=player.level().getBlockState(at);
            var poolBlock=BuiltInRegistries.BLOCK.get(id("botania:mana_pool"));player.level().setBlockAndUpdate(at,poolBlock.defaultBlockState());
            var pool=player.level().getBlockEntity(at);var receiver=Class.forName("vazkii.botania.api.mana.ManaReceiver");
            receiver.getMethod("receiveMana",int.class).invoke(pool,100);blade.setDamageValue(5);
            var drop=new ItemEntity(player.level(),at.getX()+.5,at.getY()+.5,at.getZ()+.5,blade.copy());
            try {LegacyManaBridge.entityTick(new EntityTickEvent.Post(drop));check(drop.getItem().getDamageValue()==4 && (int)receiver.getMethod("getCurrentMana").invoke(pool)==0,"pool spends 100 mana per durability");}
            finally{drop.discard();player.level().setBlockAndUpdate(at,previous);}
            rows.put("botania_native_mana_burst_pool",true);
        }finally{player.getInventory().setItem(slot,saved);}
    }
    private static void optionalZephyr(ServerPlayer player,ItemStack blade,LivingEntity target,Map<String,Object> rows) {
        var state=BladeStateAccess.of(blade).orElseThrow();state.setTranslationKey("item.slashblade_addon.zephyr");state.setBroken(false);
        player.setDeltaMovement(Vec3.ZERO);player.fallDistance=1;
        LegacyZephyr.use(new LivingEntityUseItemEvent.Tick(player,blade,71999));
        if(!ModList.get().isLoaded("thaumcraft")){check(player.getDeltaMovement().equals(Vec3.ZERO),"no Thaumcraft is inert");rows.put("zephyr","absent: soft integration closed");return;}
        check(player.getDeltaMovement().y>0,"Zephyr delegates native wind lift");
        state.setBroken(true);player.setDeltaMovement(Vec3.ZERO);LegacyZephyr.use(new LivingEntityUseItemEvent.Tick(player,blade,71999));check(player.getDeltaMovement().equals(Vec3.ZERO),"broken Zephyr has no wind");state.setBroken(false);
        var second=EntityType.ZOMBIE.create(player.level());second.setNoAi(true);second.setNoGravity(true);second.setPos(-7.5,160,-6);target.setPos(-8,160,-6);target.invulnerableTime=0;target.hurtTime=0;player.serverLevel().addFreshEntity(second);
        try {
            state.setComboSeq(LegacyCombat.id(LegacyMove.NONE));state.setLastActionTime(player.level().getGameTime());float hp=second.getHealth();
            blade.getItem().onLeftClickEntity(blade,player,target);
            check(second.getHealth()<hp,"physical left click invokes native elemental sweep");rows.put("zephyr_native_wind_and_sweep",true);
        }finally{second.discard();}
    }
    private static void transferableEffects(ServerPlayer player,ItemStack original,Map<String,Object> rows) {
        var stand=mods.flammpfeil.slashblade.RegistryEvents.BladeStand.create(player.level());stand.setPos(player.position().add(0,0,1));
        player.serverLevel().addFreshEntity(stand);
        var drops=new ArrayList<ItemEntity>();
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> collect=event->{if(event.getEntity() instanceof ItemEntity item)drops.add(item);};
        var attacks=new ArrayList<Boolean>();
        java.util.function.Consumer<SlashBladeEvent.BladeStandAttackEvent> observe=event->{if(event.getBladeStand()==stand)attacks.add(event.isCanceled());};
        NeoForge.EVENT_BUS.addListener(collect);NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,true,observe);
        boolean creative=player.getAbilities().instabuild;player.getAbilities().instabuild=false;
        var effects=new ArrayList<>(List.of(LegacyNamedBlades.MANA_BURST,LegacyNamedBlades.MANA_REPAIR));
        if(ModList.get().isLoaded("slashblade_addon"))effects.add(id("slashblade_addon:burst_drive"));
        try {
            for(var effect:effects) {
                var blade=original.copy();var from=BladeStateAccess.of(blade).orElseThrow();
                from.setTranslationKey("item.slashblade_addon.terra_blade");LegacyNamedBlades.seed(blade);
                from.setSpecialEffects(new net.minecraft.nbt.ListTag());from.addSpecialEffect(effect);stand.setItem(blade);
                player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SlashBladeItems.PROUDSOUL_CRYSTAL.get()));int count=drops.size();
                stand.hurt(player.damageSources().playerAttack(player),1);
                check(drops.size()==count+1 && player.getMainHandItem().isEmpty(),effect+" extraction creates one carrier for one crystal");
                var extracted=drops.getLast();var carrier=extracted.getItem().copy();
                check(carrier.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getString("SpecialEffectType").equals(effect.toString()),effect+" carrier retains registered identity");
                var changed=stand.getItem();LegacyNamedBlades.seed(changed);
                check(!BladeStateAccess.of(changed).orElseThrow().hasSpecialEffect(effect),effect+" extraction removes original and seed does not restore it");
                var recipient=new ItemStack(SlashBladeItems.SLASHBLADE.get());stand.setItem(recipient);player.setItemInHand(InteractionHand.MAIN_HAND,carrier);
                stand.hurt(player.damageSources().playerAttack(player),1);
                check(carrier.isEmpty() && BladeStateAccess.of(stand.getItem()).orElseThrow().hasSpecialEffect(effect),effect+" carrier transfers to another blade");
                extracted.discard();
            }
            rows.put("mana_and_burst_effect_transfer",effects.stream().map(Object::toString).toList());
            var copiedArts=new LinkedHashMap<String,Object>();rows.put("sa_copy_results",copiedArts);
            var sharpness=player.registryAccess().holderOrThrow(Enchantments.SHARPNESS);
            for(var entry:List.of(Map.entry("item.slashblade.koseki","slashblade:drive_vertical"),Map.entry("item.slashblade_addon.kamuy_none","slashblade:circle_slash"),Map.entry("item.slashblade_addon.terra_blade","slashblade_addon:rapid_blistering_swords"))) {
                var blade=original.copy();var state=BladeStateAccess.of(blade).orElseThrow();state.setTranslationKey(entry.getKey());state.setSlashArtsKey(id(entry.getValue()));
                // Legacy copying requires a capped enchantment; Sharpness has the same cap in both modes.
                blade.enchant(sharpness,sharpness.value().getMaxLevel());stand.setItem(blade);
                state=BladeStateAccess.of(stand.getItem()).orElseThrow();
                var sphere=new ItemStack(SlashBladeItems.PROUDSOUL_SPHERE.get());sphere.enchant(sharpness,1);player.setItemInHand(InteractionHand.MAIN_HAND,sphere);
                int count=drops.size(),attackCount=attacks.size();stand.hurt(player.damageSources().playerAttack(player),1);
                copiedArts.put(entry.getKey(),Map.of("new_drops",drops.size()-count,"sphere_remaining",sphere.getCount(),"stand_events",attacks.size()-attackCount,"attack_canceled",attacks.size()>attackCount && attacks.getLast()));
                check(drops.size()==count+1 && sphere.isEmpty(),entry.getKey()+" real stand attack creates one SA carrier: drops="+(drops.size()-count)+", sphere remaining="+sphere.getCount()+", stand events="+(attacks.size()-attackCount));
                var copied=drops.getLast().getItem().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getString("SpecialAttackType");
                check(copied.equals(LegacyNamedBlades.correctedArt(entry.getKey(),id(entry.getValue())).toString()) && state.getSlashArtsKey().equals(id(entry.getValue())),entry.getKey()+" carrier gets effective SA without rewriting original blade");
            }
            rows.put("corrected_sa_carriers",true);
        }finally{drops.forEach(Entity::discard);NeoForge.EVENT_BUS.unregister(collect);NeoForge.EVENT_BUS.unregister(observe);stand.discard();player.getAbilities().instabuild=creative;player.setItemInHand(InteractionHand.MAIN_HAND,original);}
    }
    private static ResourceLocation id(String text){return ResourceLocation.parse(text);}
    private static <T extends Entity> List<T> entities(ServerPlayer player,Class<T> type){return player.level().getEntitiesOfClass(type,new AABB(-100,140,-100,100,200,100),e->!e.isRemoved());}
    private static void check(boolean success,String text){if(!success)throw new AssertionError("named blade: "+text);}
}
