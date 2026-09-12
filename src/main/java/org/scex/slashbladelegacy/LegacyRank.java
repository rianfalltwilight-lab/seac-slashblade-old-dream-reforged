package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/** Legacy hit awards normalized to Resharpened's rank unit, retaining native HUD and sync packets. */
public final class LegacyRank {
    private LegacyRank(){}
    /** Re-send the current server rank when the client receives a new player/level instance.
     * This neither awards points nor changes death/login persistence or r87's low-rank damage. */
    public static void synchronize(Player player) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer server)
                || !LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT)) return;
        LegacyMode.bindRank(player);
        var rank=player.getData(mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank.RANK_POINT);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(server,
                new mods.flammpfeil.slashblade.network.RankSyncMessage(rank.getRankPoint(player.level().getGameTime())));
        LegacyDamage.update(player);
    }
    public static boolean award(IConcentrationRank rank,DamageSource source) {
        if(LegacyProjectileDamage.award(rank,source))return true;
        if(LegacyDamage.ownsRankSource(source))return true;
        if(!org.scex.slashbladelegacy.LegacyMode.legacy(source.getEntity()) || !LegacyCompat.isEnabled(LegacyCompat.LEGACY_RANK)
                || !(source.getEntity() instanceof Player player) || player.level().isClientSide)return false;
        var state=BladeStateAccess.of(player.getMainHandItem()).orElse(null);
        if(state==null)return false;
        var move=LegacyCombat.move(state.getComboSeq());
        if(move==LegacyMove.NONE || source.getDirectEntity()!=player)return false;
        if(!source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)
                && !source.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK))return false;
        awardMove(player,rank,move);
        return true;
    }
    public static void awardMove(Player player,IConcentrationRank rank,LegacyMove move) {
        float factor=switch(move) {
            case BATTOU,A_KIRIOROSI_FINISH,CALIBUR,HELM_BRAKER -> .5f;
            case KIRIOROSI,A_KIRIAGE -> .4f;
            case SLASH_EDGE,RETURN_EDGE -> .2f;
            case S_SLASH_BLADE -> -.2f;
            case SLASH_DIM -> .6f;
            case NONE,NOUTOU -> 0;
            default -> .3f;
        };
        awardAction(player,rank,move==LegacyMove.STINGER?LegacyMove.RAPID_SLASH.name():move.name(),factor);
    }
    public static void hurt(net.minecraft.world.entity.LivingEntity victim,IConcentrationRank rank,DamageSource source) {
        if(!(victim instanceof Player) || victim.level().isClientSide)return;
        if(source.is(net.minecraft.world.damagesource.DamageTypes.THORNS) || source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)
                || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR) && source.getEntity()!=null)return;
        if(source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker && attacker.getLastHurtMobTimestamp()==attacker.tickCount)return;
        long now=victim.level().getGameTime();
        // Damage after a long idle period clears the raw stored rank, including the preserved band.
        long next=now-rank.getLastUpdate()>200?0:Math.max(0,rank.getRawRankPoint()-2*rank.getUnitCapacity());
        rank.setRawRankPoint(next);rank.setLastUpdte(now);rank.addRankPoint(victim,0);
    }
    public static void awardAction(Player player,IConcentrationRank rank,String action,float factor) {
        if(!org.scex.slashbladelegacy.LegacyMode.enabled(player,LegacyCompat.LEGACY_RANK) || factor==0)return;
        int legacyPoints=(int)(100*Math.abs(factor));
        long now=player.level().getGameTime();
        String key="slashblade_legacy_compat.rank_cd."+action;
        var data=player.getPersistentData();
        if(factor>0) {
            long last=data.getLong(key);
            if(!data.contains(key) || last<now || last>now+30)data.putLong(key,now+20);
            else if(last-now<20){legacyPoints/=2;data.putLong(key,Math.min(now+30,last+10));}
            else {legacyPoints=1;data.putLong(key,now+30);}
        }
        int current=rank.getRank(now).level;
        for(int i=0;i<Math.max(0,Math.min(5,current)-2);i++)legacyPoints=(int)(legacyPoints*.8f);
        rank.addRankPoint(player,Math.max(1,legacyPoints)*rank.getUnitCapacity()/100);
    }
}
