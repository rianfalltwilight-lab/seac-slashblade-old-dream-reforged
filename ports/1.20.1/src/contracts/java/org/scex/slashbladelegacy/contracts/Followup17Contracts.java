package org.scex.slashbladelegacy.contracts;

import com.mojang.authlib.GameProfile;
import java.util.*;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.*;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import org.scex.slashbladelegacy.*;

/** Exercises releaseUsing -> real item use -> combo dispatch, not just the pure graph. */
final class Followup17Contracts {
    private static int checks;
    static void run(MinecraftServer server,Map<String,Object> report) {
        checks=0;
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"SAFollowup17"));
        player.setPos(24,160,24);player.setOnGround(true);player.setYRot(0);player.setXRot(0);
        server.overworld().addNewPlayer(player);
        var blade=ContractItems.blade();
        blade.setHoverName(Component.literal("r87 follow-up"));
        blade.enchant(Enchantments.POWER_ARROWS,1);
        var state=BladeStateAccess.of(blade).orElseThrow();
        try {
            for(String key:List.of("slashblade:drive_vertical","slashblade:wave_edge","slashblade_legacy_compat:drive","slashblade_legacy_compat:wave_edge")) {
                prepare(player,blade,key,4);
                release(player,blade);
                int first=key.endsWith("wave_edge")?4:1;
                int cost=key.endsWith("wave_edge")?20:10;
                check(drives(player).size()==first && state.getProudSoulCount()==1000-cost,key+" primary release");
                clear(player);right(player,blade);
                check(state.getComboSeq().equals(LegacyCombat.id(LegacyMove.KIRIOROSI)),key+" second right-click descends from SA Kiriage");
                var spawned=drives(player);
                check(spawned.size()==1 && spawned.get(0).multiHit() && spawned.get(0).getLifetime()==20
                        && spawned.get(0).getRotationRoll()==0 && Math.abs(spawned.get(0).getDeltaMovement().length()-.75)<.00001,
                        key+" one vertical multi-hit follow-up, old speed and lifetime");
                check(state.getProudSoulCount()==1000-cost-10,key+" pays ten more souls, not another full SA");
                blade.getItem().use(player.level(),player,InteractionHand.MAIN_HAND);
                check(drives(player).size()==1 && state.getProudSoulCount()==1000-cost-10,key+" repeated input in one tick cannot duplicate follow-up");
                clear(player);right(player,blade);right(player,blade);right(player,blade);
                check(drives(player).isEmpty(),key+" no repeated charged sword after next normal combo");
            }
            prepare(player,blade,"slashblade:wave_edge",3);release(player,blade);clear(player);right(player,blade);
            check(state.getComboSeq().equals(LegacyCombat.id(LegacyMove.KIRIOROSI)) && drives(player).isEmpty(),"below A keeps second melee but no extra Drive");
            prepare(player,blade,"slashblade:drive_vertical",4);release(player,blade);clear(player);
            state.setProudSoulCount(0);int wear=blade.getDamageValue();right(player,blade);
            check(drives(player).size()==1 && blade.getDamageValue()==wear+5,"follow-up soul fallback costs five durability");
            var target=net.minecraft.world.entity.EntityType.ALLAY.create(player.level());
            target.setPos(drives(player).get(0).position().add(0,0,1));player.serverLevel().addFreshEntity(target);
            try {
                float health=target.getHealth();var drive=drives(player).get(0);drive.tick();drive.tick();
                check(target.getHealth()<health,"emitted follow-up actually damages a living entity");
            } finally {target.discard();}
            prepare(player,blade,"slashblade:drive_vertical",4);release(player,blade);clear(player);
            state.setBroken(true);right(player,blade);
            check(drives(player).isEmpty(),"broken blade cannot emit charged follow-up");
            prepare(player,blade,"slashblade:drive_vertical",4);blade.resetHoverName();release(player,blade);clear(player);right(player,blade);
            check(drives(player).isEmpty(),"enchanted but unnamed blade is not bewitched");
            blade.setHoverName(Component.literal("r87 follow-up"));
            prepare(player,blade,"slashblade:drive_vertical",4);release(player,blade);clear(player);
            player.getCapability(CapabilityInputState.INPUT_STATE).resolve().orElseThrow().getCommands().add(InputCommand.SNEAK);right(player,blade);
            check(state.getComboSeq().equals(LegacyCombat.id(LegacyMove.SAYA1)) && drives(player).isEmpty(),"sneak branches to sheath strike");
            player.getCapability(CapabilityInputState.INPUT_STATE).resolve().orElseThrow().getCommands().remove(InputCommand.SNEAK);right(player,blade);right(player,blade);
            check(drives(player).size()==1,"scabbard strikes retain charge until the next blade strike");
            prepare(player,blade,"slashblade:drive_horizontal",4);player.setOnGround(false);release(player,blade);clear(player);right(player,blade);
            check(state.getComboSeq().equals(LegacyCombat.id(LegacyMove.BATTOU)) && drives(player).size()==1,"air Quick Drive Iai continues to Battou");
            prepare(player,blade,"slashblade:drive_vertical",4);release(player,blade);clear(player);
            server.getWorldData().overworldData().setGameTime(server.overworld().getGameTime()+100);
            var expired=state.resolvCurrentComboState(player);
            report.put("legacy17_expired_sa",Map.of("resolved",expired.toString(),"stored",state.getComboSeq().toString(),"charged",blade.getOrCreateTag().getBoolean("scex_legacy_charged")));
            check(expired.equals(ComboStateRegistry.NONE.getId()) && state.getComboSeq().equals(ComboStateRegistry.NONE.getId()) && !blade.getOrCreateTag().getBoolean("scex_legacy_charged"),"fully elapsed SA reaches NONE and clears charge");
            right(player,blade);right(player,blade);right(player,blade);
            check(drives(player).isEmpty(),"fully expired SA cannot arm a later normal combo");
            for(var art:LegacyArts.Art.values()) {
                prepare(player,blade,LegacyArts.key(art).toString(),0);release(player,blade);clear(player);right(player,blade);
                var expected=LegacyCombat.next(art.pose,true,true,false,false,false,false,false,false,0,1);
                check(state.getComboSeq().equals(LegacyCombat.id(expected)),art+" follows its actual r87 combo pose");
            }
            report.put("legacy17_sa_followup",Map.of("checks",checks,"right_click_dispatch",true,"core_and_legacy_aliases",true,
                    "rank_and_blade_gates",true,"soul_and_durability_cost",true,"expired_charge_cleared",true));
        } finally {clear(player);player.stopUsingItem();player.discard();}
    }
    private static void prepare(ServerPlayer player,ItemStack blade,String key,int band) {
        clear(player);InputClock.next(player);player.setItemInHand(InteractionHand.MAIN_HAND,blade);
        player.setOnGround(true);player.setPos(24,160,24);player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.removeAllEffects();blade.setDamageValue(1);LegacyAdditionalAttack.clearCharged(blade);
        var state=BladeStateAccess.of(blade).orElseThrow();state.setBroken(false);state.setSealed(false);state.setProudSoulCount(1000);
        state.setSlashArtsKey(new ResourceLocation(key));state.setComboSeq(LegacyCombat.id(LegacyMove.SAYA1));state.setLastActionTime(player.level().getGameTime());
        var rank=player.getCapability(CapabilityConcentrationRank.RANK_POINT).resolve().orElseThrow();rank.setRawRankPoint(band*rank.getUnitCapacity());rank.setLastUpdte(player.level().getGameTime());
        player.getCapability(CapabilityInputState.INPUT_STATE).resolve().orElseThrow().getCommands().clear();
    }
    private static void release(ServerPlayer player,ItemStack blade) {blade.releaseUsing(player.level(),player,blade.getUseDuration()-20);}
    private static void right(ServerPlayer player,ItemStack blade) {
        player.stopUsingItem();player.getCapability(CapabilityInputState.INPUT_STATE).resolve().orElseThrow().getCommands().add(InputCommand.R_CLICK);
        InputClock.use(player,blade);
    }
    private static List<LegacyDrive> drives(ServerPlayer player) {return player.level().getEntitiesOfClass(LegacyDrive.class,new AABB(-100,100,-100,100,200,100),e->e.getOwner()==player && !e.isRemoved());}
    private static void clear(ServerPlayer player) {
        drives(player).forEach(Entity::discard);
        player.level().getEntitiesOfClass(LegacyArtEntity.class,new AABB(-100,100,-100,100,200,100),e->e.getOwner()==player).forEach(Entity::discard);
        player.level().getEntitiesOfClass(LegacyPhantomSword.class,new AABB(-100,100,-100,100,200,100),e->e.getOwner()==player).forEach(Entity::discard);
    }
    private static void check(boolean value,String message) {checks++;if(!value)throw new AssertionError("r87 follow-up: "+message);}
}
