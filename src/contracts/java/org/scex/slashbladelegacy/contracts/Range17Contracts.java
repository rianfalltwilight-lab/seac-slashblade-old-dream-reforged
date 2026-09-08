package org.scex.slashbladelegacy.contracts;

import java.util.*;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import org.scex.slashbladelegacy.*;
import static org.scex.slashbladelegacy.LegacyRangeAttack.Art;

final class Range17Contracts {
    private static final AABB AREA=new AABB(-200,80,-200,800,240,800);
    static void run(ServerPlayer player,Map<String,Object> report) {
        var level=player.serverLevel();var original=player.getMainHandItem();var pos=player.position();
        var blade=new ItemStack(SlashBladeItems.SLASHBLADE.get());
        blade.set(DataComponents.CUSTOM_NAME,Component.literal("r87 phantom contract"));
        blade.enchant(player.registryAccess().holderOrThrow(Enchantments.POWER),3);
        // Existing validation tickets cover chunk [-1,-1]; distant unloaded chunks hide newly added entities from queries.
        player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.setPos(-8,160,-8);player.setYRot(0);player.setXRot(0);
        var state=BladeStateAccess.of(blade).orElseThrow();state.setProudSoulCount(1000);
        state.setColorCode(0x3333FF);check(LegacyRangeAttack.color(state)==0x3333FF,"ARGB is not an inverted legacy color");
        state.setEffectColorInverse(true);check(LegacyRangeAttack.color(state)==-0x3333FF,"explicit inverse color preserved");state.setEffectColorInverse(false);
        var input=player.getData(CapabilityInputState.INPUT_STATE);input.getCommands().clear();input.getLastPressTimes().clear();LegacyRangeAttack.clear(player);
        var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);rank.setRawRankPoint(0);rank.setLastUpdte(level.getGameTime());
        try {
            press(player,EnumSet.of(InputCommand.M_DOWN));check(shots(player).isEmpty() && state.getProudSoulCount()==1000,"no ordinary shot on down");
            InputClock.next(player);press(player,EnumSet.noneOf(InputCommand.class));
            var diagnostic=new LinkedHashMap<String,Object>();diagnostic.put("souls",state.getProudSoulCount());diagnostic.put("shots",shots(player).size());diagnostic.put("held_same",player.getMainHandItem()==blade);
            diagnostic.put("state",state.serializeNBT().toString());diagnostic.put("types",mods.flammpfeil.slashblade.item.SwordType.from(blade).toString());diagnostic.put("alive",player.isAlive());
            diagnostic.put("owner_in_world",level.getEntity(player.getUUID())==player);diagnostic.put("data",player.getPersistentData().toString());diagnostic.put("blade",blade.save(player.registryAccess()).toString());
            report.put("legacy17_range_release_diagnostic",diagnostic);
            check(shots(player).size()==1 && state.getProudSoulCount()==999,"release single cost one");
            check(shots(player).getFirst().getColor()==0x3333FF,"normal color transmitted without alpha sign");
            var single=shots(player).getFirst();check(single.art()==Art.SINGLE && single.getDamage()==1 && single.interval()==7 && single.lifetime()==30,"single r87 parameters");
            var initial=single.position();for(int i=0;i<7;i++)single.tick();check(single.position().equals(initial),"single stays seven ticks");
            single.tick();check(single.position().distanceToSqr(initial)>1,"single moves at eight");clear(player);
            press(player,EnumSet.of(InputCommand.M_DOWN));
            for(int i=0;i<7;i++)clock(player);check(shots(player).isEmpty(),"no formation before eighth tick");
            clock(player);check(shots(player).size()==6 && shots(player).stream().allMatch(s->s.art()==Art.SPIRAL && s.getDamage()==1) && state.getProudSoulCount()==989,"six spiral at eight, low rank one");
            press(player,EnumSet.noneOf(InputCommand.class));check(shots(player).size()==7 && state.getProudSoulCount()==988,"charged release still makes one ordinary sword");
            clear(player);player.getPersistentData().remove("slashblade_legacy_compat.spiral");
            for(int band:new int[]{3,4,5}) {
                rank.setRawRankPoint((long)band*rank.getUnitCapacity());rank.setLastUpdte(level.getGameTime());
                LegacyRangeAttack.perform(player,blade,Art.BLISTERING);
                int count=band==3?4:band==4?6:8;
                check(shots(player).size()==count && shots(player).stream().allMatch(s->s.getDamage()==6 && s.art()==Art.BLISTERING),"blistering count/damage rank "+band);
                var first=shots(player).getFirst();for(int i=0;i<12;i++)first.tick();check(!first.hasFired(),"blistering waits for release");
                int souls=state.getProudSoulCount();LegacyRangeAttack.perform(player,blade,Art.SINGLE);check(shots(player).size()==count && state.getProudSoulCount()==souls,"blistering release consumes no single soul");
                clock(player);first.tick();check(first.hasFired() && first.age()==0,"blistering first fires following release");clear(player);
            }
            long now=level.getGameTime();
            var forward=EnumSet.of(InputCommand.SNEAK,InputCommand.FORWARD,InputCommand.M_DOWN);
            check(LegacyRangeAttack.chargedArt(forward,now-13,now)==Art.HEAVY_RAIN && LegacyRangeAttack.chargedArt(forward,now-14,now)==Art.BLISTERING,"back typeahead strict fourteen");
            check(LegacyRangeAttack.chargedArt(forward,-1,now)==Art.BLISTERING,"missing back press is not rain");
            rank.setRawRankPoint(3L*rank.getUnitCapacity());rank.setLastUpdte(now);LegacyRangeAttack.perform(player,blade,Art.HEAVY_RAIN);
            check(shots(player).size()==20 && shots(player).stream().allMatch(s->s.getDamage()==1 && s.interval()>=10 && s.interval()<=19),"twenty rain low rank stagger ten through nineteen");
            for(var shot:shots(player)) {
                var tag=new CompoundTag();shot.save(tag);var restored=(LegacyPhantomSword)EntityType.loadEntityRecursive(tag,level,e->e);
                check(restored!=null && restored.getOwner()==player && restored.art()==shot.art() && restored.interval()==shot.interval() && restored.getDamage()==shot.getDamage(),"rain full save restore");
            }
            clear(player);rank.setRawRankPoint(5L*rank.getUnitCapacity());rank.setLastUpdte(level.getGameTime());LegacyRangeAttack.perform(player,blade,Art.HEAVY_RAIN);check(shots(player).size()==30,"thirty rain S and above");clear(player);
            state.setTargetEntityId(-1);int souls=state.getProudSoulCount();LegacyRangeAttack.perform(player,blade,Art.STORM);check(shots(player).isEmpty() && state.getProudSoulCount()==souls,"storm needs locked target before payment");
            var target=EntityType.ZOMBIE.create(level);target.setPos(-8,160,2);target.getAttribute(Attributes.ARMOR).setBaseValue(0);level.addFreshEntity(target);
            state.setTargetEntityId(target);LegacyRangeAttack.perform(player,blade,Art.STORM);
            check(shots(player).size()==6 && shots(player).stream().allMatch(s->s.getDamage()==1.5 && s.lifetime()==70),"storm always six, half power");
            var storm=shots(player).getFirst();for(int i=0;i<40;i++)storm.tick();check(!storm.hasFired(),"storm holds forty");
            storm.tick();check(storm.hasFired() && storm.interval()==40 && storm.lifetime()==70,"storm fire keeps original lifetime");storm.tick();check(storm.age()==42,"storm flight next tick");clear(player);target.discard();state.setTargetEntityId(-1);
            // Source movement and copies cannot substitute a different blade for callbacks.
            LegacyRangeAttack.perform(player,blade,Art.SINGLE);var sourceShot=shots(player).getFirst();
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.getInventory().setItem(9,blade);sourceShot.tick();check(!sourceShot.isRemoved(),"moving source keeps sword");
            player.getInventory().setItem(10,blade.copy());sourceShot.tick();check(sourceShot.isRemoved(),"duplicate source expires sword");
            player.getInventory().setItem(10,ItemStack.EMPTY);player.getInventory().setItem(9,ItemStack.EMPTY);player.setItemInHand(InteractionHand.MAIN_HAND,blade);clear(player);
            // Real collision against a boss-like direct-source gate, without a preparatory left click.
            var boss=new DirectSourceZombie(level);boss.setPos(-8,160,-3.5);boss.getAttribute(Attributes.ARMOR).setBaseValue(0);level.addFreshEntity(boss);
            LegacyRangeAttack.perform(player,blade,Art.SINGLE);var hit=shots(player).getFirst();hit.setPos(-8,160.5,-4);hit.setDeltaMovement(0,0,1);
            rank.setRawRankPoint(0);rank.setLastUpdte(level.getGameTime());int wear=blade.getDamageValue();float health=boss.getHealth();hit.tick();
            check(boss.getHealth()==health-3 && boss.accepted==1,"directMagic accepted on first projectile collision");
            check(blade.getDamageValue()==wear+1 && rank.getRawRankPoint()==rank.getUnitCapacity()*20/100,"actual source callback and old phantom rank exactly once");
            var attachedTag=new CompoundTag();hit.save(attachedTag);hit.discard();var attached=(LegacyPhantomSword)EntityType.loadEntityRecursive(attachedTag,level,e->e);
            for(int i=0;i<200;i++)attached.tick();check(attached.isRemoved() && boss.accepted==3,"attached reload ends in half damage plus burst once");
            health=boss.getHealth();attached.tick();attached.burst();check(boss.getHealth()==health,"expiry idempotent");boss.discard();
            var allay=EntityType.ALLAY.create(level);allay.setPos(-8,160,-4.3);level.addFreshEntity(allay);
            var flyingBlade=new LegacySummonedBlade(SummonedBladeMode.BLADE.get(),level);
            flyingBlade.initialize(player,3,LegacyRangeAttack.color(state),LegacyRangeAttack.sourceId(blade),allay);
            flyingBlade.setPos(-8,160.4,-4.5);flyingBlade.setDeltaMovement(0,0,1);wear=blade.getDamageValue();flyingBlade.tick();
            check(allay.getHealth()==17 && flyingBlade.getHitEntity()==allay && blade.getDamageValue()==wear+1,"SB reaches non-Enemy default target through actual base collision");
            flyingBlade.discard();allay.discard();
            // Canceled projectile impact and damage both retain protection and do not consume blade durability.
            var protectedTarget=EntityType.ZOMBIE.create(level);protectedTarget.setPos(-8,160,-3);level.addFreshEntity(protectedTarget);
            LegacyRangeAttack.perform(player,blade,Art.SINGLE);var denied=shots(player).getFirst();denied.setPos(-8,160.5,-3.5);denied.setDeltaMovement(0,0,1);
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.ProjectileImpactEvent> cancel=e->{if(e.getProjectile()==denied)e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(cancel);
            wear=blade.getDamageValue();try{denied.tick();}finally{NeoForge.EVENT_BUS.unregister(cancel);}
            check(protectedTarget.getHealth()==20 && blade.getDamageValue()==wear,"projectile impact cancellation honored");denied.discard();protectedTarget.discard();
            var refused=EntityType.ZOMBIE.create(level);refused.setPos(-8,160,-3);level.addFreshEntity(refused);
            LegacyRangeAttack.perform(player,blade,Art.SINGLE);var refusedShot=shots(player).getFirst();refusedShot.setPos(-8,160.5,-3.5);refusedShot.setDeltaMovement(0,0,1);
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent> noDamage=e->{if(e.getEntity()==refused)e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(noDamage);
            wear=blade.getDamageValue();long oldRank=rank.getRawRankPoint();refused.invulnerableTime=17;
            try{refusedShot.tick();}finally{NeoForge.EVENT_BUS.unregister(noDamage);}
            check(refused.getHealth()==20 && blade.getDamageValue()==wear && rank.getRawRankPoint()==oldRank && refused.invulnerableTime==17,"damage cancellation preserves wear rank and invulnerability");
            LegacyFreeze.apply(refused,20);var freeze=new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(refused);NeoForge.EVENT_BUS.post(freeze);check(freeze.isCanceled(),"rain freeze stops target tick");
            for(int i=0;i<20;i++)clock(player);var thaw=new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(refused);NeoForge.EVENT_BUS.post(thaw);check(!thaw.isCanceled(),"rain freeze ends after twenty");
            refusedShot.discard();refused.discard();
            // Do not run a queued eighth-tick action after switching to another actual stack.
            clear(player);press(player,EnumSet.of(InputCommand.M_DOWN));player.setItemInHand(InteractionHand.MAIN_HAND,blade.copy());for(int i=0;i<8;i++)clock(player);
            press(player,EnumSet.noneOf(InputCommand.class));check(shots(player).isEmpty(),"switching blade invalidates queued art and release");player.setItemInHand(InteractionHand.MAIN_HAND,blade);
            report.put("legacy17_range",Map.of("input_release",true,"charge_tick",8,"spiral",6,"storm",6,"blistering_counts",List.of(4,6,8),"rain_counts",List.of(20,30),"first_direct_source_hit",true,"source_identity",true,"save_attachment_expiry",true,"protection",true));
        }finally {
            clear(player);LegacyRangeAttack.clear(player);input.getCommands().clear();
            player.getPersistentData().remove("slashblade_legacy_compat.spiral");player.getPersistentData().remove("slashblade_legacy_compat.blistering_until");
            player.setItemInHand(InteractionHand.MAIN_HAND,original);player.setPos(pos);
        }
    }
    private static List<LegacyPhantomSword> shots(ServerPlayer player) {return player.level().getEntitiesOfClass(LegacyPhantomSword.class,AREA,e->!e.isRemoved() && e.getOwner()==player);}
    private static void clear(ServerPlayer player) {for(var shot:shots(player))shot.discard();}
    private static void clock(ServerPlayer player) {InputClock.next(player);player.getData(CapabilityInputState.INPUT_STATE).getScheduler().onTick(player);}
    private static void press(ServerPlayer player,EnumSet<InputCommand> after) {
        var state=player.getData(CapabilityInputState.INPUT_STATE);var before=state.getCommands().clone();
        for(var command:after)if(!before.contains(command))state.getLastPressTimes().put(command,player.level().getGameTime());
        state.getCommands().clear();state.getCommands().addAll(after);NeoForge.EVENT_BUS.post(new InputCommandEvent(player,state,before,after));
    }
    private static class DirectSourceZombie extends net.minecraft.world.entity.monster.Zombie {
        int accepted;
        DirectSourceZombie(net.minecraft.world.level.Level level){super(EntityType.ZOMBIE,level);}
        @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount) {
            if(!(source.getDirectEntity() instanceof net.minecraft.world.entity.player.Player))return false;
            boolean result=super.hurt(source,amount);if(result)accepted++;return result;
        }
    }
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError("r87 range: "+message);}
}
