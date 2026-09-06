package org.scex.slashbladelegacy.contracts;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import org.scex.slashbladelegacy.*;

final class AdvancedContracts {
    static void run(ServerPlayer player,Map<String,Object> report) {
        var level=player.serverLevel();var saved=player.getMainHandItem();var savedOff=player.getOffhandItem();
        var blade=saved.copy();blade.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        var state=BladeStateAccess.of(blade).orElseThrow();
        state.setComboRoot(ComboStateRegistry.STANDBY.getId());state.setComboSeq(ComboStateRegistry.NONE.getId());
        state.setBroken(false);state.setSealed(false);state.setBaseAttackModifier(7);state.setAttackAmplifier(2);state.setDefaultBewitched(true);
        blade.enchant(level.registryAccess().holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.POWER),1);
        player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.setItemInHand(InteractionHand.OFF_HAND,net.minecraft.world.item.ItemStack.EMPTY);
        player.setPos(32,160,0);player.setYRot(0);player.setXRot(0);player.setOnGround(true);
        player.getData(mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState.INPUT_STATE).getCommands().clear();
        var target=EntityType.ZOMBIE.create(level);target.setPos(32,160,2);level.getChunk(2,0);level.addFreshEntity(target);
        var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);long oldRank=rank.getRawRankPoint(),oldUpdate=rank.getLastUpdate();
        var drives=new ArrayList<LegacyDrive>();
        try {
            // Direct fixed magic damage must not be multiplied by the held blade's attack attributes.
            var single=new LegacyDrive(SummonedBladeMode.DRIVE.get(),level);drives.add(single);
            single.initialize(player,blade,4,0,0,false);single.tick();require(target.getHealth()==16,"Fixed drive damage");
            single.tick();require(target.getHealth()==16,"Single-hit drive duplicated damage");
            var save=new net.minecraft.nbt.CompoundTag();single.addAdditionalSaveData(save);
            var restored=new LegacyDrive(SummonedBladeMode.DRIVE.get(),level);drives.add(restored);
            restored.readAdditionalSaveData(save);restored.setOwner(player);restored.setPos(single.position());restored.setDeltaMovement(Vec3.ZERO);
            restored.tick();require(target.getHealth()==16,"Reload forgot drive hit history");
            target.setHealth(20);
            var multi=new LegacyDrive(SummonedBladeMode.DRIVE.get(),level);drives.add(multi);multi.initialize(player,blade,4,0,0,true);
            multi.tick();require(target.getHealth()==20,"Multi-hit drive hit odd tick");multi.tick();require(target.getHealth()==16,"Multi-hit tick2");
            multi.tick();require(target.getHealth()==16,"Multi-hit odd interval");multi.tick();require(target.getHealth()==12,"Multi-hit tick4");
            player.setItemInHand(InteractionHand.MAIN_HAND,saved);multi.tick();require(multi.isRemoved(),"Missing source drive survives");
            player.setItemInHand(InteractionHand.MAIN_HAND,blade);
            var flight=new LegacyDrive(SummonedBladeMode.DRIVE.get(),level);drives.add(flight);flight.initialize(player,blade,1,.1f,0,false);
            target.setPos(40,160,0);double initial=flight.getZ();flight.tick();require(Math.abs(flight.getZ()-initial-.105)<.00001,"Drive acceleration 1.05");
            for(int i=1;i<20;i++)flight.tick();require(flight.isRemoved(),"Drive lifetime20");
            rank.setLastUpdte(level.getGameTime());rank.setRawRankPoint(rank.getUnitCapacity()*5);
            require(LegacyCombat.rank(player)==5,"Rank fixture");
            state.setProudSoulCount(30);
            state.doChargeAction(player,16);
            state.setComboSeq(ComboStateRegistry.NONE.getId());
            LegacyAdditionalAttack.attack(player,LegacyMove.SAYA1);
            require(state.getProudSoulCount()==30,"Scabbard consumed charged follow-up");
            LegacyAdditionalAttack.attack(player,LegacyMove.KIRIAGE);
            require(state.getProudSoulCount()==20,"Charged follow-up soul cost");
            int emitted=level.getEntitiesOfClass(LegacyDrive.class,player.getBoundingBox().inflate(6)).size();
            require(emitted==1,"Charged follow-up count");
            LegacyAdditionalAttack.attack(player,LegacyMove.KIRIAGE);
            require(level.getEntitiesOfClass(LegacyDrive.class,player.getBoundingBox().inflate(6)).size()==emitted,"Repeated charged follow-up");
            LegacyAdditionalAttack.attack(player,LegacyMove.FORCE5);
            var actual=level.getEntitiesOfClass(LegacyDrive.class,player.getBoundingBox().inflate(6));
            require(actual.size()==2 && actual.stream().anyMatch(d->Math.abs(d.getSpeed()-.1)<.00001),"Force5 finisher");
            // Exercise actual dual-wield use, not only the pure graph selector.
            actual.forEach(LegacyDrive::discard);target.setPos(32,160,2);target.setHealth(20);target.invulnerableTime=0;
            target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
            blade.enchant(level.registryAccess().holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS),2);
            state.setKillCount(1000);state.setAttackAmplifier(2);state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(level.getGameTime());
            int scabbardWear=blade.getDamageValue();InputClock.use(player,blade);
            require(Math.abs(target.getHealth()-12.5)<.001 && blade.getDamageValue()==scabbardWear,"FiercerEdge and enchanted scabbard damage");
            report.put("enchanted_scabbard_damage",7.5);
            target.setHealth(20);target.invulnerableTime=0;
            var off=blade.copy();off.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            var offState=BladeStateAccess.of(off).orElseThrow();offState.setComboSeq(ComboStateRegistry.NONE.getId());
            player.setItemInHand(InteractionHand.OFF_HAND,off);
            state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(level.getGameTime());
            int mainWear=blade.getDamageValue(),offWear=off.getDamageValue();
            InputClock.use(player,blade);
            require(LegacyCombat.move(state.getComboSeq())==LegacyMove.FORCE1,"Actual dual input graph");
            require(player.getMainHandItem()==blade && player.getOffhandItem()==off && !state.onClick() && !offState.onClick(),"Dual hand/flag restoration");
            require(blade.getDamageValue()==mainWear && off.getDamageValue()==offWear+2,"Dual legacy durability distribution: main="+(blade.getDamageValue()-mainWear)+", off="+(off.getDamageValue()-offWear)+", health="+target.getHealth()+", attack="+player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
            state.updateComboSeq(player,LegacyCombat.id(LegacyMove.RAPID_SLASH));
            blade.setCount(0);LegacyCombat.tickMotion(player,level.getGameTime()+3);
            report.put("destroyed_blade_stops_motion",true);
            report.put("advanced_drive",Map.of("fixed_damage",4,"single_hit_save",true,"multi_hit_every_2ticks",true,
                    "source_missing_guard",true,"lifetime",20,"acceleration",1.05,"charge_cost_once",true,"force5_finisher",true,"dual_durability",true));
        } finally {
            drives.forEach(LegacyDrive::discard);level.getEntitiesOfClass(LegacyDrive.class,player.getBoundingBox().inflate(64)).forEach(LegacyDrive::discard);
            target.discard();player.setItemInHand(InteractionHand.MAIN_HAND,saved);player.setItemInHand(InteractionHand.OFF_HAND,savedOff);
            rank.setRawRankPoint(oldRank);rank.setLastUpdte(oldUpdate);player.setPos(0,160,0);
        }
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}

