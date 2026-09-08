package org.scex.slashbladelegacy.contracts;

import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import org.scex.slashbladelegacy.*;
import static org.scex.slashbladelegacy.LegacyMove.*;

/** Focused new-engine contracts. Historical 1.12/VMD assertions are not reused as r87 acceptance. */
final class Legacy17Contracts {
    private static int checks;
    static void run(MinecraftServer server,Map<String,Object> report) throws Exception {
        checks=0;var level=server.overworld();
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"Legacy17Contract"));
        player.setPos(24,160,0);player.setYRot(0);player.setXRot(0);player.setOnGround(true);
        level.addNewPlayer(player);
        check(level.getEntity(player.getUUID())==player,"world-resolvable projectile owner fixture");
        player.getAbilities().instabuild=false;player.experienceLevel=0;
        var definition=server.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY,ResourceLocation.parse("slashblade:sange")));
        var blade=definition.value().getBlade(server.registryAccess());
        blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);
        var state=BladeStateAccess.of(blade).orElseThrow();state.setSpecialEffects(new net.minecraft.nbt.ListTag());
        state.setSealed(false);state.setBroken(false);blade.setDamageValue(1);state.setKillCount(0);
        state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(level.getGameTime());
        player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.getData(CapabilityInputState.INPUT_STATE).getCommands().clear();
        var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);rank.setRawRankPoint(0);rank.setLastUpdte(level.getGameTime());
        var target=net.minecraft.world.entity.EntityType.ZOMBIE.create(level);target.setPos(24,160,2);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);target.getAttribute(Attributes.ARMOR).setBaseValue(0);
        target.setHealth(500);target.setNoAi(true);target.setNoGravity(true);check(level.addFreshEntity(target),"target query fixture");
        var rows=new ArrayList<Map<String,Object>>();report.put("legacy17_melee",rows);
        int[] slashEvents={0},hitEvents={0},attackEvents={0};
        Consumer<SlashBladeEvent.DoSlashEvent> slash=e->{if(e.getUser()==player)slashEvents[0]++;};
        Consumer<SlashBladeEvent.HitEvent> hit=e->{if(e.getUser()==player)hitEvents[0]++;};
        Consumer<AttackEntityEvent> attack=e->{if(e.getEntity()==player)attackEvents[0]++;};
        NeoForge.EVENT_BUS.addListener(slash);NeoForge.EVENT_BUS.addListener(hit);NeoForge.EVENT_BUS.addListener(attack);
        try {
            for(var expected:new LegacyMove[]{SAYA1,SAYA2,BATTOU}) {
                float before=target.getHealth();int wear=blade.getDamageValue();InputClock.use(player,blade);
                float damage=before-target.getHealth();
                rows.add(Map.of("move",expected.name(),"damage",damage,"wear",blade.getDamageValue()-wear));
                check(LegacyCombat.move(state.getComboSeq())==expected,"ground right "+expected);
                check(damage==(expected.scabbard?2:3),"r87 low rank no cooldown "+expected+" damage="+damage);
                check(blade.getDamageValue()-wear==(expected.scabbard?0:1),"r87 wear "+expected);
            }
            check(slashEvents[0]==3 && hitEvents[0]==3 && attackEvents[0]==3,"single public hooks per hit");
            check(!state.onClick(),"click flag cleanup");
            player.resetAttackStrengthTicker();float before=target.getHealth();
            state.updateComboSeq(player,LegacyCombat.id(KIRIAGE));
            check(before-target.getHealth()==3,"cooldown zero still full damage");
            target.setHealth(2);state.updateComboSeq(player,LegacyCombat.id(SAYA1));check(target.getHealth()==1,"nonlethal floor");
            state.updateComboSeq(player,LegacyCombat.id(SAYA2));check(target.getHealth()==1,"repeated nonlethal floor");
            target.setHealth(500);target.setPos(24,160,4.6);
            state.updateComboSeq(player,LegacyCombat.id(SAYA1));check(target.getHealth()==500,"exact r87 sheath boundary");
            player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).setBaseValue(.1);
            state.updateComboSeq(player,LegacyCombat.id(BATTOU));check(target.getHealth()==497,"old Battou not clipped by modern reach");
            player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).setBaseValue(3);
            blade.enchant(player.registryAccess().holderOrThrow(Enchantments.SHARPNESS),1);blade.setDamageValue(0);
            target.setHealth(500);target.setPos(24,160,-5);state.updateComboSeq(player,LegacyCombat.id(BATTOU));
            check(target.getHealth()==495.75f,"perfect bewitched rear area");
            check(blade.getDamageValue()==11,"perfect Battou hit wear plus ten");
            target.setPos(24,160,2);target.setHealth(500);
            state.updateComboSeq(player,LegacyCombat.id(KIRIAGE));check(target.getHealth()==495.75f,"Sharpness I = 1.25");
            blade.enchant(player.registryAccess().holderOrThrow(Enchantments.SHARPNESS),5);
            target.setHealth(500);state.updateComboSeq(player,LegacyCombat.id(KIRIAGE));check(target.getHealth()==490.75f,"Sharpness V = 6.25");
            target.setHealth(500);player.setOnGround(false);player.fallDistance=1;
            state.updateComboSeq(player,LegacyCombat.id(KIRIAGE));check(target.getHealth()==489.25f,"critical base then enchant bonus: "+target.getHealth());
            player.fallDistance=0;player.setOnGround(true);
            rank.setRawRankPoint(3L*rank.getUnitCapacity());rank.setLastUpdte(level.getGameTime());
            LegacyDamage.update(player);check(state.getAttackAmplifier()==0,"rank B amplifier zero");
            player.experienceLevel=80;state.setRefine(7);state.setKillCount(1000);
            rank.setRawRankPoint(5L*rank.getUnitCapacity());rank.setLastUpdte(level.getGameTime());
            LegacyDamage.update(player);check(state.getAttackAmplifier()==17,"fiercer level limited by ten plus refine");
            state.setKillCount(0);rank.setRawRankPoint((long)(5.8*rank.getUnitCapacity()));rank.setLastUpdte(level.getGameTime());
            LegacyDamage.update(player);check(state.getAttackAmplifier()==17,"SSS level amplifier without fiercer");
            state.setBroken(true);LegacyDamage.update(player);check(state.getAttackAmplifier()==2-state.getBaseAttackModifier(),"broken amplifier");
            target.setPos(24,160,4);check(!LegacyCombat.box(player,BATTOU).intersects(target.getBoundingBox()),"broken area shrinks");
            state.setBroken(false);blade.setDamageValue(1);target.setPos(24,160,2);target.setHealth(500);
            rank.setRawRankPoint(0);rank.setLastUpdte(level.getGameTime());
            Consumer<AttackEntityEvent> cancel=e->{if(e.getEntity()==player)e.setCanceled(true);};
            NeoForge.EVENT_BUS.addListener(cancel);
            try{state.updateComboSeq(player,LegacyCombat.id(BATTOU));check(target.getHealth()==500,"protection event cancels old melee");}
            finally{NeoForge.EVENT_BUS.unregister(cancel);}
            var replacement=new ItemStack(Items.STICK);
            Consumer<SlashBladeEvent.DoSlashEvent> swap=e->{if(e.getUser()==player)player.setItemInHand(InteractionHand.MAIN_HAND,replacement);};
            NeoForge.EVENT_BUS.addListener(swap);
            try{state.updateComboSeq(player,LegacyCombat.id(BATTOU));check(player.getMainHandItem()==replacement && target.getHealth()==500,"SE swap preserves replacement and stops hit");}
            finally{NeoForge.EVENT_BUS.unregister(swap);player.setItemInHand(InteractionHand.MAIN_HAND,blade);}
            blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);target.setHealth(500);target.hurtTime=0;target.hurtDuration=0;
            state.setComboSeq(ComboStateRegistry.NONE.getId());InputClock.next(player);
            int attacksBefore=attackEvents[0],hitsBefore=hitEvents[0];player.resetAttackStrengthTicker();player.attack(target);
            check(target.getHealth()==497 && LegacyCombat.move(state.getComboSeq())==KIRIAGE,"actual Player.attack left single hit no cooldown");
            check(attackEvents[0]==attacksBefore+1 && hitEvents[0]==hitsBefore+1,"left does not duplicate protection or hit callbacks");
            player.setItemInHand(InteractionHand.OFF_HAND,blade.copy());state.setComboSeq(ComboStateRegistry.NONE.getId());
            InputClock.use(player,blade);check(LegacyCombat.move(state.getComboSeq())==SAYA1,"offhand does not select post-1.7 Force graph");
            for(var move:LegacyMove.values())if(move!=NONE) {
                var combo=ComboStateRegistry.REGISTRY.get(LegacyCombat.id(move));
                check(combo.getStartFrame()==0 && combo.getEndFrame()==0,"VMD-free registered state "+move);
                check(LegacyBladePose.matrix(move,.5f,false).isFinite(),"finite old pose "+move);
            }
            var saved=blade.save(server.registryAccess());var restored=ItemStack.parse(server.registryAccess(),saved).orElseThrow();
            check(BladeStateAccess.of(restored).orElseThrow().getComboSeq().equals(state.getComboSeq()),"combo item roundtrip");
            blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);rank.setRawRankPoint(0);rank.setLastUpdte(level.getGameTime());
            player.removeAllEffects();LegacyDamage.update(player);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST,100,0));
            check(Math.abs(LegacyDamage.base(player,blade)-6.9)<.001,"r87 Strength I 130 percent");
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS,100,0));
            check(Math.abs(LegacyDamage.base(player,blade)-5.75)<.001,"r87 Weakness before Strength");
            player.removeAllEffects();
            state.setComboSeq(LegacyCombat.id(SAYA1));state.setLastActionTime(level.getGameTime());state.setSlashArtsKey(ResourceLocation.parse("slashblade:judgement_cut"));
            var art=state.doChargeAction(player,20);
            check(art!=null && !art.equals(ComboStateRegistry.NONE.getId()) && art.equals(state.getComboSeq()),"SA actually starts from a held ordinary combo: "+art+" / "+state.getComboSeq());
            target.setHealth(500);target.setPos(24,160,12);
            mods.flammpfeil.slashblade.util.AttackManager.doMeleeAttack(player,target,true,true,2);
            check(target.getHealth()==494,"SA own range and coefficient use old melee: "+target.getHealth());
            player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);target.discard();
            SoulEater17Contracts.run(player,report);
            Guard17Contracts.run(player,report);
            Addon17Contracts.run(player,report);
            AddonArts17Contracts.run(player,report);
            AddonEffects17Contracts.run(player,report);
            AddonTarget17Contracts.run(player,report);
            Enchant17Contracts.run(player,report);
            Repair17Contracts.run(player,report);
            BladeSoul17Contracts.run(player,report);
            Broken17Contracts.run(player,report);
            Rank17Contracts.run(player,report);
            Melee17Contracts.run(player,report);
            Range17Contracts.run(player,report);
            Drive17Contracts.run(player,report);
            BaseArts17Contracts.run(player,report);
            ProjectileTail17Contracts.run(player,report);
            var beforeSb=player.getMainHandItem();var beforeSbPos=player.position();
            try {
                var sb=blade.copy();sb.remove(DataComponents.CUSTOM_DATA);sb.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);
                var sbState=BladeStateAccess.of(sb).orElseThrow();sbState.setBroken(false);sbState.setSealed(false);sbState.setSpecialEffects(new net.minecraft.nbt.ListTag());
                rank.setRawRankPoint(0);rank.setLastUpdte(level.getGameTime());player.setPos(0,160,0);player.setYRot(0);player.setXRot(0);player.setOnGround(true);player.removeAllEffects();
                SbContracts.run(player,sb,report);
            } finally {player.setPos(beforeSbPos);player.setItemInHand(InteractionHand.MAIN_HAND,beforeSb);}
            report.put("legacy17_checks",checks);
            report.put("boundary","server behavior and addon SA launch contracts; client combat, remaining SE cases, lifecycle and performance still pending");
            SiContracts.run(player,report);
        } finally {
            NeoForge.EVENT_BUS.unregister(slash);NeoForge.EVENT_BUS.unregister(hit);NeoForge.EVENT_BUS.unregister(attack);
            target.discard();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            player.discard();
        }
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
