package org.scex.slashbladelegacy.contracts;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import org.scex.slashbladelegacy.*;
import static org.scex.slashbladelegacy.LegacyMove.*;

/** Real Item.use / Player.attack and loaded-world damage; input clock steps are not client tests. */
final class DualWieldContracts {
    private static int checks;
    static void run(MinecraftServer server,Map<String,Object> report) {
        checks=0;var level=server.overworld();
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"DualContract"));
        player.setPos(24,160,0);player.setYRot(0);player.setXRot(0);player.setOnGround(true);
        player.getAbilities().instabuild=false;level.addNewPlayer(player);
        var definitions=server.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements().toList();
        var definition=definitions.stream().filter(d->d.key().location().toString().equals("slashblade:sange")).findFirst().orElseThrow();
        var main=clean(definition.value().getBlade(server.registryAccess()));var off=clean(main.copy());
        var state=BladeStateAccess.of(main).orElseThrow();var offState=BladeStateAccess.of(off).orElseThrow();
        player.setItemInHand(InteractionHand.MAIN_HAND,main);player.setItemInHand(InteractionHand.OFF_HAND,off);
        // Different enchantments expose accidentally using the controller blade's damage.
        off.enchant(player.registryAccess().holderOrThrow(Enchantments.SHARPNESS),2);
        var target=EntityType.ZOMBIE.create(level);target.setPos(24,160,2);target.setOnGround(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);target.getAttribute(Attributes.ARMOR).setBaseValue(0);
        target.setHealth(500);target.setNoAi(true);target.setNoGravity(true);check(level.addFreshEntity(target),"target enters real entity query");
        var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);
        var rows=new ArrayList<Map<String,Object>>();report.put("dual_sequence",rows);
        try {
            low(player);int mainWear=main.getDamageValue(),offWear=off.getDamageValue();
            for(var expected:new LegacyMove[]{FORCE1,FORCE2,FORCE3,FORCE4,FORCE5,FORCE6}) {
                low(player);target.setPos(24,160,2);target.setOnGround(true);
                float hp=target.getHealth();int mw=main.getDamageValue(),ow=off.getDamageValue();
                InputClock.use(player,main);
                boolean borrowed=expected==FORCE1 || expected==FORCE2 || expected==FORCE6;
                check(LegacyCombat.move(state.getComboSeq())==expected,"actual use advances "+expected);
                check(close(hp-target.getHealth(),expected.scabbard?2:borrowed?5.5:3),"actual striking enchantment "+expected+": "+(hp-target.getHealth()));
                check(main.getDamageValue()-mw==(!borrowed && !expected.scabbard?1:0),"main wear "+expected);
                check(off.getDamageValue()-ow==(borrowed?1:0),"idle offhand explicit wear "+expected);
                check(player.getMainHandItem()==main && player.getOffhandItem()==off && !state.onClick() && !offState.onClick(),"hand and callback restoration "+expected);
                check(target.getDeltaMovement().y==0,"grounded dual hit does not lift "+expected);
                rows.add(Map.of("move",expected.name(),"damage",hp-target.getHealth(),"main_wear",main.getDamageValue()-mw,"off_wear",off.getDamageValue()-ow));
            }
            check(main.getDamageValue()==mainWear+1 && off.getDamageValue()==offWear+3,"six-hit durability distribution");
            offState.setComboSeq(LegacyCombat.id(FORCE5));offState.setLastActionTime(level.getGameTime());
            int priorOffWear=off.getDamageValue();state.updateComboSeq(player,LegacyCombat.id(FORCE1));
            check(off.getDamageValue()==priorOffWear+2 && offState.getComboSeq().equals(LegacyCombat.id(FORCE5)),"already drawn offhand retains its old callback wear and combo");
            offState.setComboSeq(ComboStateRegistry.NONE.getId());
            var savedMain=ItemStack.parse(server.registryAccess(),main.save(server.registryAccess())).orElseThrow();
            var savedOff=ItemStack.parse(server.registryAccess(),off.save(server.registryAccess())).orElseThrow();
            check(savedMain.getDamageValue()==main.getDamageValue() && savedOff.getDamageValue()==off.getDamageValue()
                    && BladeStateAccess.of(savedMain).orElseThrow().getComboSeq().equals(state.getComboSeq()),"both blades roundtrip components after dual attacks");
            var drives=level.getEntitiesOfClass(mods.flammpfeil.slashblade.entity.EntityDrive.class,player.getBoundingBox().inflate(32));
            check(drives.size()==1 && drives.getFirst() instanceof LegacyDrive,"Force6 exactly one drive and no modern duplicate");
            check(close(drives.getFirst().getSpeed(),.1) && close(drives.getFirst().getRotationRoll(),-180),"Force6 old finisher speed and roll");
            drives.forEach(Entity::discard);
            int proud=state.getProudSoulCount();rank.setRawRankPoint(rank.getUnitCapacity()*5);rank.setLastUpdte(level.getGameTime());
            state.setComboSeq(LegacyCombat.id(FORCE4));InputClock.use(player,main);
            check(level.getEntitiesOfClass(LegacyDrive.class,player.getBoundingBox().inflate(32)).size()==1 && state.getProudSoulCount()==proud,"rank S Force5 drive with no soul cost");
            level.getEntitiesOfClass(LegacyDrive.class,player.getBoundingBox().inflate(32)).forEach(Entity::discard);

            player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);state.setComboSeq(LegacyCombat.id(FORCE1));low(player);
            InputClock.use(player,main);check(LegacyCombat.move(state.getComboSeq())==SAYA1,"removing offhand exits Force graph");
            InputClock.use(player,main);check(LegacyCombat.move(state.getComboSeq())==SAYA2,"single graph continues");
            InputClock.use(player,main);check(LegacyCombat.move(state.getComboSeq())==BATTOU,"single Battou retained");
            player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.DIAMOND_SWORD));state.setComboSeq(ComboStateRegistry.NONE.getId());
            InputClock.use(player,main);check(LegacyCombat.move(state.getComboSeq())==SAYA1,"ordinary offhand sword does not enable Force");
            player.setItemInHand(InteractionHand.OFF_HAND,off);state.setComboSeq(ComboStateRegistry.NONE.getId());
            InputClock.next(player);var result=off.getItem().use(level,player,InteractionHand.OFF_HAND);
            check(result.getResult()==net.minecraft.world.InteractionResult.FAIL && state.getComboSeq().equals(ComboStateRegistry.NONE.getId()),"direct offhand use cannot dispatch a second attack");
            target.setHealth(500);target.hurtDuration=0;target.hurtTime=0;InputClock.next(player);player.attack(target);
            check(LegacyCombat.move(state.getComboSeq())==KIRIAGE && close(500-target.getHealth(),3),"actual standalone left click remains mainhand r87");
            player.setOnGround(false);state.setComboSeq(ComboStateRegistry.NONE.getId());InputClock.use(player,main);
            check(LegacyCombat.move(state.getComboSeq())==A_SLASH_EDGE,"dual equipment preserves aerial rave");
            player.setOnGround(true);player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;

            // Force3/4 inherit the later default area, not the much flatter Saya box.
            target.setPos(24,162.5,2);target.setHealth(500);state.updateComboSeq(player,LegacyCombat.id(FORCE3));
            check(target.getHealth()<500,"Force sheath reaches the original vertical area");
            target.setPos(24,160,2);target.setHealth(2);state.updateComboSeq(player,LegacyCombat.id(FORCE4));
            check(target.getHealth()==1,"Force sheath stays nonlethal");
            target.setHealth(500);target.setOnGround(true);
            Consumer<AttackEntityEvent> cancel=e->{if(e.getEntity()==player)e.setCanceled(true);};
            NeoForge.EVENT_BUS.addListener(cancel);int mw=main.getDamageValue(),ow=off.getDamageValue();
            try{state.updateComboSeq(player,LegacyCombat.id(FORCE1));check(target.getHealth()==500 && main.getDamageValue()==mw && off.getDamageValue()==ow,"protection cancels damage and all wear");}
            finally{NeoForge.EVENT_BUS.unregister(cancel);}
            check(player.getMainHandItem()==main && player.getOffhandItem()==off && !offState.onClick(),"canceled strike restores actual hands");
            final var originalOff=off;
            Consumer<SlashBladeEvent.HitEvent> throwing=e->{if(e.getUser()==player && e.getBlade()==originalOff)throw new IllegalStateException("intentional dual SE failure");};
            NeoForge.EVENT_BUS.addListener(throwing);
            try{try{state.updateComboSeq(player,LegacyCombat.id(FORCE2));throw new AssertionError("SE did not throw");}catch(IllegalStateException expected){check(expected.getMessage().contains("intentional"),"original SE exception propagates");}}
            finally{NeoForge.EVENT_BUS.unregister(throwing);}
            check(player.getMainHandItem()==main && player.getOffhandItem()==off && !state.onClick() && !offState.onClick(),"exception restores hands and flags");
            var replacement=new ItemStack(Items.STICK);
            Consumer<AttackEntityEvent> swap=e->{if(e.getEntity()==player)player.setItemInHand(InteractionHand.MAIN_HAND,replacement);};
            NeoForge.EVENT_BUS.addListener(swap);target.setHealth(500);
            try{state.updateComboSeq(player,LegacyCombat.id(FORCE1));}
            finally{NeoForge.EVENT_BUS.unregister(swap);}
            check(player.getMainHandItem()==replacement && target.getHealth()==500,"callback replacement retained, damage aborted");
            final var originalMainItem=main.getItem();
            check(player.getInventory().items.stream().anyMatch(i->i.getItem()==originalMainItem),"borrowed controller preserved in inventory");
            player.getInventory().clearContent();main=clean(definition.value().getBlade(server.registryAccess()));off=clean(main.copy());
            player.setItemInHand(InteractionHand.MAIN_HAND,main);player.setItemInHand(InteractionHand.OFF_HAND,off);
            state=BladeStateAccess.of(main).orElseThrow();low(player);target.setHealth(500);target.setPos(24,160,1);target.setOnGround(true);
            var input=player.getData(CapabilityInputState.INPUT_STATE).getCommands();input.clear();input.addAll(EnumSet.of(InputCommand.SNEAK,InputCommand.FORWARD));
            InputClock.use(player,main);long start=level.getGameTime();
            check(LegacyCombat.move(state.getComboSeq())==STINGER,"dual forward+sneak is Stinger");
            float beforeTick=target.getHealth();LegacyCombat.tickMotion(player,start+1);
            check(close(player.getDeltaMovement().z,1.5) && target.getDeltaMovement().equals(Vec3.ZERO),"Stinger velocity and stun without lift");
            check(target.getHealth()<beforeTick,"Stinger manager hits near target");
            float once=target.getHealth();LegacyCombat.tickMotion(player,start+1);LegacyCombat.tickMotion(player,start+2);
            check(target.getHealth()==once,"manager only hits each target once, including repeated tick call");
            target.setPos(24,160,3.5);target.setHealth(500);LegacyCombat.tickMotion(player,start+3);
            check(target.getHealth()==500,"Stinger manager uses radius 1.5, not initial forward melee box");
            LegacyCombat.tickMotion(player,start+4);check(LegacyCombat.move(state.getComboSeq())==STINGER,"manager completion does not overwrite another combo");
            state.setComboSeq(ComboStateRegistry.NONE.getId());InputClock.use(player,main);start=level.getGameTime();
            player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);target.setPos(24,160,1);float unchanged=target.getHealth();
            LegacyCombat.tickMotion(player,start+1);check(target.getHealth()==unchanged,"offhand removal cancels pending Stinger");
            input.clear();

            player.setItemInHand(InteractionHand.OFF_HAND,off);low(player);
            var victim=EntityType.ZOMBIE.create(level);victim.setPos(24,160,2);victim.setNoAi(true);victim.setHealth(1);
            int mainKills=state.getKillCount(),offKills=BladeStateAccess.of(off).orElseThrow().getKillCount();
            target.setPos(24,160,20);check(level.addFreshEntity(victim),"lethal hit fixture");
            try {
                state.updateComboSeq(player,LegacyCombat.id(FORCE1));
                check(!victim.isAlive() && state.getKillCount()==mainKills+1
                        && BladeStateAccess.of(off).orElseThrow().getKillCount()==offKills+1,"old dual callback credits each blade one kill");
            } finally {victim.discard();}

            var accepted=new ArrayList<String>();
            var nonBlades=new ArrayList<String>();report.put("dual_non_blade_definitions",nonBlades);
            for(var d:definitions) {
                var candidate=d.value().getBlade(server.registryAccess());
                if(BladeStateAccess.of(candidate).isEmpty()){nonBlades.add(d.key().location().toString());continue;}
                candidate=clean(candidate);
                if(mods.flammpfeil.slashblade.item.SwordType.from(candidate).contains(mods.flammpfeil.slashblade.item.SwordType.NOSCABBARD))continue;
                target.setPos(24,160,20);player.setItemInHand(InteractionHand.MAIN_HAND,candidate);player.setItemInHand(InteractionHand.OFF_HAND,clean(candidate.copy()));
                InputClock.use(player,candidate);
                check(LegacyCombat.move(BladeStateAccess.of(candidate).orElseThrow().getComboSeq())==FORCE1,"registered blade input "+d.key().location());
                accepted.add(d.key().location().toString());
            }
            report.put("dual_registered_blades",accepted);
            check(accepted.size()>=20,"real registry blade coverage");
            check(LegacyDualWield.mainHandPose(FORCE6)==FORCE5 && LegacyDualWield.mainHandPose(FORCE1)==NONE
                    && LegacyDualWield.mainHandPose(FORCE3)==null,"original mainHandCombo visual mapping");
            for(var move:new LegacyMove[]{FORCE1,FORCE2,FORCE3,FORCE4,FORCE5,FORCE6,STINGER})
                for(float progress:new float[]{0,.25f,.5f,1})check(LegacyBladePose.matrix(move,progress,false).isFinite(),"finite source pose "+move);
            check(LegacyBladePose.offhandCarry().isFinite(),"finite original ninja carry matrix");
        } finally {
            target.discard();level.getEntitiesOfClass(LegacyDrive.class,new AABB(0,140,-32,60,180,40)).forEach(Entity::discard);
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);LegacyCombat.tickMotion(player,level.getGameTime()+20);
            player.discard();report.put("dual_checks",checks);
        }
    }
    private static ItemStack clean(ItemStack blade) {
        blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);blade.remove(DataComponents.CUSTOM_DATA);
        var s=BladeStateAccess.of(blade).orElseThrow();s.setSpecialEffects(new net.minecraft.nbt.ListTag());
        s.setSealed(false);s.setBroken(false);s.setComboSeq(ComboStateRegistry.NONE.getId());s.setMaxDamage(500);blade.setDamageValue(1);return blade;
    }
    private static void low(ServerPlayer player) {var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);rank.setRawRankPoint(0);rank.setLastUpdte(player.level().getGameTime());LegacyDamage.update(player);}
    private static boolean close(double a,double b){return Math.abs(a-b)<.001;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
