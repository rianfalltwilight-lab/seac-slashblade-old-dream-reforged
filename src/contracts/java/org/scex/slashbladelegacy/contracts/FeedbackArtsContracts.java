package org.scex.slashbladelegacy.contracts;

import java.lang.reflect.Proxy;
import java.util.*;
import mods.flammpfeil.slashblade.RegistryEvents;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.*;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import org.scex.slashbladelegacy.*;

/** Feedback regression: obtainable SA spheres, real release, protected water impacts and optional addon hooks. */
final class FeedbackArtsContracts {
    static void run(ServerPlayer player,Map<String,Object> report) throws Exception {
        var original=player.getMainHandItem();var pos=player.position();boolean modern=LegacyMode.modern(player);
        var rows=new LinkedHashMap<String,Object>();report.put("feedback_arts_20261008",rows);
        var blade=new ItemStack(SlashBladeItems.SLASHBLADE.get());blade.set(DataComponents.CUSTOM_NAME,Component.literal("SA feedback"));
        blade.enchant(player.registryAccess().holderOrThrow(Enchantments.POWER),1);
        var target=EntityType.ZOMBIE.create(player.level());target.setNoAi(true);target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.setHealth(1000);
        player.serverLevel().addFreshEntity(target);
        try {
            player.getPersistentData().putBoolean(LegacyMode.MODERN,false);
            var spheres=creativeSpheres();
            for(var art:List.of(LegacyArts.Art.MAXIMUM,LegacyArts.Art.DIMENSION_SPIRAL)) {
                var id=LegacyArts.key(art);check(SlashArtsRegistry.REGISTRY.containsKey(id),"registered "+id);
                var sphere=spheres.stream().filter(s->s.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("SpecialAttackType").equals(id.toString())).findFirst().orElseThrow(()->new AssertionError("No creative sphere "+id));
                prepare(player,blade,target);var stand=RegistryEvents.BladeStand.create(player.level());stand.currentType=SlashBladeItems.BLADESTAND_1.get();stand.setPos(player.position());stand.setItem(blade);
                player.serverLevel().addFreshEntity(stand);
                try {
                    var material=sphere.copy();player.setItemInHand(InteractionHand.MAIN_HAND,material);
                    NeoForge.EVENT_BUS.post(new SlashBladeEvent.BladeStandAttackEvent(stand.getItem(),BladeStateAccess.of(stand.getItem()).orElseThrow(),stand,player.damageSources().playerAttack(player)));
                    check(BladeStateAccess.of(stand.getItem()).orElseThrow().getSlashArtsKey().equals(id),"sphere installation "+id);
                }finally{stand.discard();}
                prepare(player,blade,target);BladeStateAccess.of(blade).orElseThrow().setSlashArtsKey(id);release(player,blade,20);
                if(art==LegacyArts.Art.MAXIMUM) {
                    var managers=owned(player,LegacyArtEntity.class);check(managers.size()==1,"Maximum release creates one manager");
                    var manager=managers.getFirst();for(int i=0;i<5;i++)manager.tick();
                    check(owned(player,LegacyDrive.class).size()==2,"Maximum two heavy drives");
                }else {
                    check(owned(player,LegacyDrive.class).size()==5 && owned(player,LegacyArtEntity.class).size()==1,"normal Spiral five drives and field");
                    prepare(player,blade,target);BladeStateAccess.of(blade).orElseThrow().setSlashArtsKey(id);release(player,blade,17);
                    var drives=owned(player,LegacyDrive.class);
                    check(drives.size()==10 && drives.stream().allMatch(d->d.dimension() && d.getLifetime()==15),"just Spiral two dimension rings");
                }
                rows.put(art.key,Map.of("creative_sphere",true,"stand_install",true,"release",true));
            }
            prepare(player,blade,target);var water=LegacyArts.water(player,blade);
            check(water.water() && water.multiHit() && water.getRotationRoll()==90 && water.getLifetime()==10 && water.getColor()==0x0000FF,"horizontal blue multihit water");
            check(Math.abs(water.getDeltaMovement().length()-1.5)<.00001,"water launch speed");
            target.setRemainingFireTicks(100);target.setPos(water.position());water.setDeltaMovement(Vec3.ZERO);water.tick();water.tick();
            check(!target.isOnFire() && target.getHealth()<1000,"water accepted impact extinguishes");
            target.setRemainingFireTicks(100);float health=target.getHealth();
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent> protection=e->{if(e.getEntity()==target)e.setCanceled(true);};
            NeoForge.EVENT_BUS.addListener(protection);
            try{water.tick();water.tick();}finally{NeoForge.EVENT_BUS.unregister(protection);}
            check(target.isOnFire() && target.getHealth()==health,"protected water impact cannot extinguish");
            var saved=new CompoundTag();water.saveWithoutId(saved);var restored=new LegacyDrive(SummonedBladeMode.DRIVE.get(),player.level());restored.load(saved);
            check(restored.water() && restored.multiHit(),"water save roundtrip");
            rows.put("water",Map.of("horizontal",true,"multi_hit",true,"extinguish",true,"protection",true,"serialization",true));
            if(ModList.get().isLoaded("slashblade_addon"))addon(player,blade,target,rows);
            if(ModList.get().isLoaded("si_slashblade"))fox(player,rows);
        } finally {clear(player);target.discard();player.getPersistentData().putBoolean(LegacyMode.MODERN,modern);player.stopUsingItem();player.setDeltaMovement(Vec3.ZERO);player.setItemInHand(InteractionHand.MAIN_HAND,original);player.setPos(pos);}
    }
    private static List<ItemStack> creativeSpheres() throws Exception {
        var type=Class.forName("mods.flammpfeil.slashblade.SlashBladeCreativeGroup");
        var method=Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().equals("fillSASpheres")).findFirst().orElseThrow();
        var output=method.getParameterTypes()[0];var result=new ArrayList<ItemStack>();
        Object collector=Proxy.newProxyInstance(output.getClassLoader(),new Class<?>[]{output},(proxy,called,args)->{
            if(args!=null && args.length>0 && args[0] instanceof ItemStack item)result.add(item);return null;
        });
        method.setAccessible(true);method.invoke(null,collector);return result;
    }
    private static void addon(ServerPlayer player,ItemStack blade,LivingEntity target,Map<String,Object> rows) throws Exception {
        prepare(player,blade,target);
        Class.forName("cn.mmf.slashblade_addon.specialattacks.GaleSwords").getMethod("doSlash",LivingEntity.class,boolean.class,double.class,float.class).invoke(null,player,false,7d,2f);
        var gale=foreignSwords(player);check(gale.size()==6 && gale.stream().allMatch(s->s.getColor()==0xFF3333 && s.getDelay()==7),"Gale red and seven-tick delay");
        var first=gale.getFirst();first.tickCount=7;first.rideTick();first.tickCount=8;first.rideTick();check(!first.isPassenger(),"Gale fires after one delay, not two");
        prepare(player,blade,target);
        Class.forName("cn.mmf.slashblade_addon.specialattacks.LightingSwords").getMethod("doSlash",LivingEntity.class,boolean.class,double.class,float.class).invoke(null,player,false,7d,2f);
        var delays=foreignSwords(player).stream().map(EntityAbstractSummonedSword::getDelay).sorted().toList();
        check(delays.equals(List.of(7,7,8,18)),"Cloud old stagger and final sword: "+delays);
        prepare(player,blade,target);
        var waterMethod=Class.forName("cn.mmf.slashblade_addon.specialattacks.WaterDrive").getMethod("doSlash",LivingEntity.class,float.class,int.class,Vec3.class,boolean.class,double.class,float.class);
        var result=waterMethod.invoke(null,player,0f,10,Vec3.ZERO,false,7d,2f);check(result instanceof LegacyDrive d && d.water(),"SJAP water invokes legacy implementation");
        prepare(player,blade,target);player.getPersistentData().putBoolean(LegacyMode.MODERN,true);
        Class.forName("cn.mmf.slashblade_addon.specialattacks.GaleSwords").getMethod("doSlash",LivingEntity.class,boolean.class,double.class,float.class).invoke(null,player,false,7d,2f);
        check(foreignSwords(player).stream().allMatch(s->s.getDelay()>=20 && LegacyAddonArtTiming.repeatedDelay(s)==s.getDelay()),"modern Gale keeps native timing");
        clear(player);player.getPersistentData().putBoolean(LegacyMode.MODERN,false);
        rows.put("sjap_hooks",Map.of("gale_delay_ticks",7,"cloud_delays",delays,"no_double_delay",true,"water_hook",true,"modern_preserved",true));
    }
    private static void fox(ServerPlayer player,Map<String,Object> rows) throws Exception {
        clear(player);var registry=player.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY);
        var blade=registry.getOrThrow(ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY,ResourceLocation.parse("si_slashblade:legacy/fox_elf"))).value().getBlade(player.registryAccess());
        blade.getItem().getClass().getMethod("setEnergy",ItemStack.class,long.class).invoke(blade.getItem(),blade,1000000L);
        player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.setOnGround(true);player.setDeltaMovement(Vec3.ZERO);player.hurtMarked=false;
        ComboStateRegistry.REGISTRY.get(ResourceLocation.parse("si_slashblade:spear")).clickAction(player);
        check(player.getDeltaMovement().horizontalDistance()>6.9 && player.hurtMarked,"SI spear server motion synchronized");
        rows.put("si_spear",Map.of("dash_velocity",player.getDeltaMovement().horizontalDistance(),"motion_sync",true));
    }
    private static void prepare(ServerPlayer player,ItemStack blade,LivingEntity target) {
        clear(player);InputClock.next(player);player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.setPos(64,160,8);player.setYRot(0);player.setXRot(0);player.setOnGround(true);player.setDeltaMovement(Vec3.ZERO);
        target.setPos(64,160,16);target.setHealth(1000);target.invulnerableTime=0;target.clearFire();
        var state=BladeStateAccess.of(blade).orElseThrow();state.setBroken(false);state.setSealed(false);state.setProudSoulCount(1000);state.setTargetEntityId(target);state.setComboSeq(LegacyCombat.id(LegacyMove.SAYA1));state.setLastActionTime(player.level().getGameTime());
        var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);rank.setRawRankPoint(0);rank.setLastUpdte(player.level().getGameTime());
    }
    private static void release(ServerPlayer player,ItemStack blade,int ticks){blade.releaseUsing(player.level(),player,blade.getUseDuration(player)-ticks);}
    private static <T extends Entity> List<T> owned(ServerPlayer player,Class<T> type){return player.level().getEntitiesOfClass(type,player.getBoundingBox().inflate(100),e->!e.isRemoved() && (e instanceof mods.flammpfeil.slashblade.entity.Projectile p && p.getOwner()==player));}
    private static List<EntityAbstractSummonedSword> foreignSwords(ServerPlayer player){return owned(player,EntityAbstractSummonedSword.class).stream().filter(e->e.getClass().getName().startsWith("cn.mmf.slashblade_addon.")).toList();}
    private static void clear(ServerPlayer player){owned(player,mods.flammpfeil.slashblade.entity.Projectile.class).forEach(Entity::discard);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError("SA feedback: "+message);}
}
