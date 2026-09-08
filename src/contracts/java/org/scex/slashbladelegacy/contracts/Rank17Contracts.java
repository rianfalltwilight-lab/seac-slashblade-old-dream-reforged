package org.scex.slashbladelegacy.contracts;

import java.util.*;
import com.mojang.authlib.GameProfile;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.scex.slashbladelegacy.LegacyRank;

final class Rank17Contracts {
    static void run(ServerPlayer source,Map<String,Object> report) throws Exception {
        var player=new VulnerablePlayer(source.serverLevel());player.setPos(24,160,0);player.setOnGround(true);player.getAbilities().instabuild=false;
        var blade=source.getMainHandItem().copy();blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);BladeStateAccess.of(blade).orElseThrow().setSpecialEffects(new net.minecraft.nbt.ListTag());player.setItemInHand(InteractionHand.MAIN_HAND,blade);
        var enemy=EntityType.ZOMBIE.create(source.level());enemy.tickCount=100;enemy.setPos(24,160,10);
        var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);long unit=rank.getUnitCapacity(),now=source.level().getGameTime();
        try {
            int[] points={99,100,200,300,400,500,550,551,575,576,599},bands={0,1,2,3,4,5,5,6,6,7,7};
            for(int i=0;i<points.length;i++){rank.setRawRankPoint(points[i]*unit/100);rank.setLastUpdte(now);check(rank.getRank(now).level==bands[i],"strict r87 threshold "+points[i]);}
            rank.setRawRankPoint(580*unit/100);rank.setLastUpdte(now);
            check(rank.getRankPoint(now+10)==570*unit/100 && rank.getRankPoint(now+100)==500*unit/100,"one old point per tick, decay stops at band");
            check(rank.getRankPoint(now-10)==580*unit/100,"clock rewind cannot award rank");
            check(Math.abs(rank.getRankProgress(now)-.8)<.0001 && rank.getMaxCapacity()==599*unit/100,"old progress and maximum");
            rank.setRawRankPoint(0);rank.setLastUpdte(now);LegacyRank.awardAction(player,rank,"r87_contract",.3f);check(rank.getRawRankPoint()==30*unit/100,"first relative award");
            for(int i=0;i<8;i++)InputClock.next(player);LegacyRank.awardAction(player,rank,"r87_contract",.3f);check(rank.getRawRankPoint()==37*unit/100,"eight tick repeated move half award after decay");
            LegacyRank.awardAction(player,rank,"r87_contract",.3f);check(rank.getRawRankPoint()==38*unit/100,"immediate repeated move one point");
            LegacyRank.awardAction(player,rank,"r87_absolute",-.5f);LegacyRank.awardAction(player,rank,"r87_absolute",-.5f);check(rank.getRawRankPoint()==138*unit/100,"absolute award bypasses repeat cooldown");
            now=source.level().getGameTime();rank.setRawRankPoint(380*unit/100);rank.setLastUpdte(now-10);player.invulnerableTime=0;
            check(player.hurt(player.damageSources().mobAttack(enemy),2),"actual incoming damage fixture");check(rank.getRawRankPoint()==180*unit/100 && rank.getLastUpdate()==now,"r87 damage removes two raw bands");
            rank.setRawRankPoint(380*unit/100);rank.setLastUpdte(now-201);player.invulnerableTime=0;check(player.hurt(player.damageSources().mobAttack(enemy),2) && rank.getRawRankPoint()==0,"damage after 201 idle ticks clears rank");
            rank.setRawRankPoint(380*unit/100);rank.setLastUpdte(now);player.invulnerableTime=0;player.hurt(player.damageSources().fall(),2);check(rank.getRawRankPoint()==380*unit/100,"fall does not lose rank");
            player.invulnerableTime=0;player.hurt(player.damageSources().thorns(enemy),2);check(rank.getRawRankPoint()==380*unit/100,"Thorns does not lose rank");
            rank.addRankPoint(player,10000*unit);check(rank.getRawRankPoint()==599*unit/100 && rank.getRank(now).level==7,"SSS cap through real add/sync path");
            report.put("legacy17_rank",Map.of("thresholds",points,"bands",bands,"decay_points_per_tick",1,"idle_damage_reset_ticks",201,"damage_penalty_points",200,"maximum",599,"repeat_cooldown",true));
        } finally{player.discard();enemy.discard();}
    }
    private static final class VulnerablePlayer extends FakePlayer {
        VulnerablePlayer(ServerLevel level) throws Exception{super(level,new GameProfile(UUID.randomUUID(),"Rank17Contract"));var field=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");field.setAccessible(true);field.setInt(this,0);}
        @Override public boolean isInvulnerableTo(DamageSource source){return false;}
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError("r87 rank: "+message);}
}
