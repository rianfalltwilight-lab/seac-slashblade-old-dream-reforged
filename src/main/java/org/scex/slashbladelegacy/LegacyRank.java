package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/** Legacy hit awards normalized to Resharpened's rank unit, retaining native HUD and sync packets. */
public final class LegacyRank {
    private LegacyRank(){}
    public static boolean award(IConcentrationRank rank,DamageSource source) {
        if(!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) || !LegacyCompat.isEnabled(LegacyCompat.LEGACY_RANK)
                || !(source.getEntity() instanceof Player player) || player.level().isClientSide)return false;
        var state=BladeStateAccess.of(player.getMainHandItem()).orElse(null);
        if(state==null || !state.getComboRoot().equals(ComboStateRegistry.STANDBY.getId()))return false;
        var move=LegacyCombat.move(state.getComboSeq());
        if(move==LegacyMove.NONE || source.getDirectEntity()!=player)return false;
        if(!source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)
                && !source.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK))return false;
        float factor=switch(move) {
            case BATTOU,A_KIRIOROSI_FINISH,CALIBUR,HELM_BRAKER -> .5f;
            case KIRIOROSI,A_KIRIAGE -> .4f;
            case SLASH_EDGE,RETURN_EDGE -> .2f;
            case S_SLASH_BLADE -> -.2f;
            case NOUTOU -> 0;
            default -> .3f;
        };
        awardAction(player,rank,move.name(),factor);
        return true;
    }
    public static void awardAction(Player player,IConcentrationRank rank,String action,float factor) {
        if(!LegacyCompat.isEnabled(LegacyCompat.LEGACY_RANK) || factor==0)return;
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
