package org.scex.slashbladelegacy.contracts;

import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.*;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.scex.slashbladelegacy.*;

/** Real release/registry/impact/serialization contracts, including the old dimension's zero magic damage. */
final class BaseArts17Contracts {
    static void run(ServerPlayer player,Map<String,Object> report) {
        var original=player.getMainHandItem();var position=player.position();var rows=new LinkedHashMap<String,Object>();report.put("legacy17_base_arts",rows);
        var blade=new ItemStack(SlashBladeItems.SLASHBLADE.get());blade.set(DataComponents.CUSTOM_NAME,Component.literal("r87 base arts"));
        blade.enchant(player.registryAccess().holderOrThrow(Enchantments.POWER),1);
        var target=EntityType.ALLAY.create(player.level());target.setNoAi(true);target.setNoGravity(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);player.serverLevel().addFreshEntity(target);
        var state=BladeStateAccess.of(blade).orElseThrow();
        try {
            for(var art:LegacyArts.Art.values()) {
                prepare(player,blade,target);state.setSlashArtsKey(LegacyArts.key(art));
                int before=blade.getDamageValue();release(player,blade,20);
                check(LegacyCombat.visualMove(state.getComboSeq())==art.pose,art+" old pose selected");
                check(state.getProudSoulCount()==1000-art.souls,art+" exact soul cost, once");
                var drives=entities(player,LegacyDrive.class);var managers=entities(player,LegacyArtEntity.class);var swords=entities(player,LegacyPhantomSword.class);
                switch(art) {
                    case DRIVE,QUICK->{check(drives.size()==1,"one Drive");var d=drives.getFirst();check(d.multiHit()==(art==LegacyArts.Art.DRIVE) && d.getLifetime()==(art==LegacyArts.Art.DRIVE?20:10),art+" mode and lifetime");check(Math.abs(d.getDeltaMovement().length()-(art==LegacyArts.Art.DRIVE?.75:1.5))<.00001,"Drive speed");}
                    case WAVE->check(drives.size()==4 && drives.stream().filter(LegacyDrive::multiHit).count()==3,"Wave three multi and one single");
                    case CIRCLE->check(drives.size()==6 && drives.stream().allMatch(d->d.multiHit() && d.getLifetime()==10 && d.getRotationRoll()==90),"Circle six old multi drives");
                    case SPEAR->{check(managers.size()==1 && Math.abs(player.getDeltaMovement().horizontalDistance()-3.5)<.00001,"Spear ground dash");target.setPos(player.position());float hp=target.getHealth();for(int i=0;i<7;i++){target.setPos(player.position());managers.getFirst().tick();}check(managers.getFirst().isRemoved() && target.getHealth()<hp && blade.getDamageValue()==before+3,"Spear three accepted even-tick callbacks");}
                    case SAKURA,MAXIMUM->{check(managers.size()==1,"two-stage manager");for(int i=0;i<5;i++)managers.getFirst().tick();var emitted=entities(player,LegacyDrive.class);check(managers.getFirst().isRemoved() && emitted.size()==2 && emitted.stream().noneMatch(LegacyDrive::multiHit),"two single drives at ticks one and five");check(LegacyCombat.visualMove(state.getComboSeq())==LegacyMove.RETURN_EDGE,"second old sweep pose");}
                    case DIMENSION->{check(managers.size()==1 && !managers.getFirst().dimension() && target.getHealth()==997,"normal locked dimension melee");float hp=target.getHealth();int wear=blade.getDamageValue();managers.getFirst().tick();managers.getFirst().tick();check(target.getHealth()==hp && blade.getDamageValue()==wear+1,"normal dimension callback without extra HP damage");}
                    case WITHER->{check(swords.size()==1 && swords.getFirst().wither() && swords.getFirst().witherBurst() && target.getHealth()==997,"rank-zero Wither initial melee and one explosive sword");}
                }
                rows.put(art.key,Map.of("release",true,"souls",art.souls,"pose",art.pose.name(),"initial_drives",drives.size(),"managers",managers.size(),"swords",swords.size()));
            }
            for(String key:List.of("judgement_cut","void_slash","drive_vertical","drive_horizontal","wave_edge","piercing","circle_slash","sakura_end")) {
                prepare(player,blade,target);var id=ResourceLocation.fromNamespaceAndPath("slashblade",key);state.setSlashArtsKey(id);release(player,blade,20);
                check(state.getSlashArtsKey().equals(id) && LegacyArts.visual(state.getComboSeq())!=null,"saved core alias "+id);
            }
            check(LegacyArts.resolve(ResourceLocation.parse("prinegorerouse:zenith12th"))==null && LegacyArts.resolve(ResourceLocation.parse("si_slashblade:slash_dimension"))==null,"foreign arts are not replaced");
            prepare(player,blade,target);state.setSlashArtsKey(LegacyArts.key(LegacyArts.Art.DRIVE));state.setProudSoulCount(0);release(player,blade,20);check(blade.getDamageValue()==6,"insufficient souls use old five durability");
            prepare(player,blade,target);state.setSlashArtsKey(LegacyArts.key(LegacyArts.Art.WITHER));state.setTargetEntityId(null);target.setPos(-8,160,-50);release(player,blade,20);check(state.getProudSoulCount()==1000 && entities(player,LegacyPhantomSword.class).isEmpty(),"Wither without a target does not charge");
            prepare(player,blade,target);state.setSlashArtsKey(LegacyArts.key(LegacyArts.Art.DRIVE));
            Consumer<SlashBladeEvent.PerformSlashArtEvent> cancelArt=e->{if(e.getEntityLiving()==player)e.setCanceled(true);};
            NeoForge.EVENT_BUS.addListener(cancelArt);try{release(player,blade,20);}finally{NeoForge.EVENT_BUS.unregister(cancelArt);}
            check(state.getProudSoulCount()==1000 && blade.getDamageValue()==1 && entities(player,LegacyDrive.class).isEmpty(),"public SA cancellation before payment or effect");
            prepare(player,blade,target);state.setSlashArtsKey(LegacyArts.key(LegacyArts.Art.DRIVE));release(player,blade,15);check(state.getProudSoulCount()==1000 && entities(player,LegacyDrive.class).isEmpty(),"tick-fifteen release is not charged");
            dimension(player,blade,target,rows);
            wither(player,blade,target,rows);
            prepare(player,blade,target);state.setSlashArtsKey(LegacyArts.key(LegacyArts.Art.DIMENSION));
            var combo=state.getSlashArts().doArts(SlashArts.ArtsType.Super,player);state.updateComboSeq(player,combo);
            var manager=entities(player,LegacyArtEntity.class).getFirst();manager.tick();manager.tick();
            check(target.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN).getAmplifier()==30,"Judgement freeze at two");
            for(int i=2;i<25;i++)manager.tick();check(target.getHealth()==997 && entities(player,LegacyDrive.class).size()==5 && entities(player,LegacyArtEntity.class).size()==3,"Judgement tick25 actual hit five Drives and two fields");
            for(int i=25;i<30;i++)manager.tick();check(manager.isRemoved() && blade.getDamageValue()==blade.getMaxDamage()/2 && LegacyCombat.visualMove(state.getComboSeq())==LegacyMove.BATTOU && state.getProudSoulCount()==1000,"Judgement old finish and half durability without soul cost");
            rows.put("core_aliases",8);rows.put("foreign_arts_preserved",true);rows.put("public_perform_cancellation",true);rows.put("super_judgement",true);
        } finally {clear(player);target.discard();player.stopUsingItem();player.removeAllEffects();player.setDeltaMovement(Vec3.ZERO);player.setItemInHand(InteractionHand.MAIN_HAND,original);player.setPos(position);}
    }
    private static void dimension(ServerPlayer player,ItemStack blade,LivingEntity target,Map<String,Object> report) {
        prepare(player,blade,target);target.setPos(-8,160,-8);target.setHealth(20);
        var field=LegacyArtEntity.spawn(player,blade,LegacyArtEntity.Mode.DIMENSION,new Vec3(-8,160,-8),10,3,true);
        int wear=blade.getDamageValue();field.tick();check(target.getHealth()==20,"field waits on odd ticks");field.tick();check(target.getHealth()==17 && blade.getDamageValue()==wear+1,"dimension cuts three without extra magic hit");
        var tag=new CompoundTag();field.save(tag);var restored=(LegacyArtEntity)EntityType.loadEntityRecursive(tag,player.level(),e->e);field.discard();player.serverLevel().addFreshEntity(restored);
        check(restored.getOwner()==player && restored.mode()==LegacyArtEntity.Mode.DIMENSION && restored.age()==2 && restored.lifetime()==10 && restored.dimension(),"field source and clock roundtrip");
        target.invulnerableTime=17;wear=blade.getDamageValue();var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);long points=rank.getRawRankPoint();
        Consumer<LivingIncomingDamageEvent> cancel=e->{if(e.getEntity()==target)e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(cancel);
        try{restored.tick();restored.tick();}finally{NeoForge.EVENT_BUS.unregister(cancel);}
        check(target.getHealth()==17 && target.invulnerableTime==17 && blade.getDamageValue()==wear && rank.getRawRankPoint()==points,"field protection keeps HP timer durability and rank");
        target.setHealth(2);restored.tick();restored.tick();check(target.getHealth()==1 && target.isAlive() && target.getDeltaMovement().y==.5,"dimension nonlethal one-HP floor and old lift");restored.discard();
        report.put("dimension",Map.of("zero_extra_magic_damage",true,"nonlethal_floor",1,"protection",true,"serialization",true));
    }
    private static void wither(ServerPlayer player,ItemStack blade,LivingEntity target,Map<String,Object> report) {
        prepare(player,blade,target);target.setPos(-8,160,0);target.setHealth(20);
        var sword=new LegacyPhantomSword(SummonedBladeMode.SWORD.get(),player.level());sword.initializeWither(player,LegacyRangeAttack.sourceId(blade),1,3,target);
        sword.setPos(target.position().add(0,.25,0));sword.setDeltaMovement(Vec3.ZERO);player.serverLevel().addFreshEntity(sword);sword.tick();
        var effect=target.getEffect(net.minecraft.world.effect.MobEffects.WITHER);
        check(sword.isRemoved() && target.getHealth()==16 && effect!=null && effect.getDuration()==100 && effect.getAmplifier()==1,"nonburst Wither direct hit plus inherited one-damage burst, old Wither II and no attachment");
        report.put("wither_direct_collision",true);
    }
    private static void prepare(ServerPlayer player,ItemStack blade,LivingEntity target) {
        clear(player);InputClock.next(player);player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.setPos(-8,160,-8);player.setYRot(0);player.setXRot(0);player.setOnGround(true);player.fallDistance=0;player.setDeltaMovement(Vec3.ZERO);player.removeAllEffects();
        target.setPos(-8,160,0);target.removeAllEffects();target.setHealth(1000);target.setDeltaMovement(Vec3.ZERO);target.invulnerableTime=0;
        blade.setDamageValue(1);var state=BladeStateAccess.of(blade).orElseThrow();state.setBroken(false);state.setSealed(false);state.setProudSoulCount(1000);state.setTargetEntityId(target);
        state.setComboSeq(LegacyCombat.id(LegacyMove.SAYA1));state.setLastActionTime(player.level().getGameTime());
        var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);rank.setRawRankPoint(0);rank.setLastUpdte(player.level().getGameTime());
        for(var key:new ArrayList<>(player.getPersistentData().getAllKeys()))if(key.startsWith("slashblade_legacy_compat.rank_cd."))player.getPersistentData().remove(key);
    }
    private static void release(ServerPlayer player,ItemStack blade,int ticks){blade.releaseUsing(player.level(),player,blade.getUseDuration(player)-ticks);}
    private static <T extends Entity> List<T> entities(ServerPlayer player,Class<T> type){return player.level().getEntitiesOfClass(type,new AABB(-100,140,-100,100,200,100),e->!e.isRemoved());}
    private static void clear(ServerPlayer player){entities(player,LegacyArtEntity.class).forEach(Entity::discard);entities(player,LegacyDrive.class).forEach(Entity::discard);entities(player,LegacyPhantomSword.class).forEach(Entity::discard);}
    private static void check(boolean value,String text){if(!value)throw new AssertionError("r87 base arts: "+text);}
}
