package org.scex.slashbladelegacy.contracts;

import java.util.Map;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import org.scex.slashbladelegacy.*;

final class SoulEater17Contracts {
    static void run(ServerPlayer player,Map<String,Object> report) {
        var blade=player.getMainHandItem();var state=BladeStateAccess.of(blade).orElseThrow();
        var target=EntityType.ZOMBIE.create(player.level());var pos=player.position();
        player.setHealth(10);blade.setDamageValue(30);state.setProudSoulCount(1000);
        try {
            finish(player);check(blade.getDamageValue()==30 && player.getHealth()==10,"empty sheath");
            NeoForge.EVENT_BUS.post(new LivingExperienceDropEvent(target,player,5));
            check(blade.getDamageValue()==30 && state.getProudSoulCount()==1000,"defer all credit until sheath");
            player.setPos(pos.add(1,0,0));finish(player);
            check(blade.getDamageValue()==30 && player.getHealth()==10,"movement cancels SoulEater");
            player.setPos(pos);finish(player);
            check(blade.getDamageValue()==25 && state.getProudSoulCount()==1005 && player.getHealth()==11,"earned repair souls heal once");
            finish(player);check(blade.getDamageValue()==25 && state.getProudSoulCount()==1005 && player.getHealth()==11,"credit consumed");
            for(int i=0;i<4;i++)NeoForge.EVENT_BUS.post(new LivingExperienceDropEvent(target,player,0));
            finish(player);check(blade.getDamageValue()==24 && player.getHealth()==13,"minimum repair, heal capped at maxHealth / 10");
            report.put("legacy17_soul_eater",Map.of("deferred_souls",5,"repair",6,"healing",3,"stationary_hash",true,"no_duplicate",true));
        } finally {player.setPos(pos);player.setHealth(player.getMaxHealth());target.discard();}
    }
    private static void finish(ServerPlayer p) {
        var state=BladeStateAccess.of(p.getMainHandItem()).orElseThrow();state.updateComboSeq(p,LegacyCombat.id(LegacyMove.NOUTOU));
        state.setLastActionTime(p.level().getGameTime()-11);state.resolvCurrentComboState(p);LegacySheathingRepair.tick(p);
    }
    private static void check(boolean v,String message){if(!v)throw new AssertionError("r87 SoulEater: "+message);}
}
