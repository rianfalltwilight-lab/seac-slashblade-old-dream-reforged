package org.scex.slashbladelegacy.contracts;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;

final class AddonTarget17Contracts {
    static void run(ServerPlayer player,Map<String,Object> report)throws Exception {
        if(!net.neoforged.fml.ModList.get().isLoaded("prinegorerouse"))return;
        var level=player.serverLevel();var target=EntityType.ALLAY.create(level);target.setPos(player.position().add(0,0,.8));target.setNoAi(true);level.addFreshEntity(target);
        var wolf=EntityType.WOLF.create(level);wolf.setPos(player.position().add(0,0,.4));wolf.setNoAi(true);wolf.tame(player);level.addFreshEntity(wolf);
        try {
            var method=Class.forName("net.xianyu.prinegorerouse.specialattack.BlackHoleAttack").getDeclaredMethod("findNearestHostileTarget",LivingEntity.class);method.setAccessible(true);
            require(method.invoke(null,player)==target,"black-hole SA selects non-Enemy without prior hit, excludes pet");
            var type=(EntityType<?>)Class.forName("net.xianyu.prinegorerouse.registry.NrEntitiesRegistry").getField("Enchanted_Sword").get(null);var sword=type.create(level);
            sword.setPos(player.position());sword.getClass().getMethod("setOwner",Entity.class).invoke(sword,player);
            var base=Class.forName("net.xianyu.prinegorerouse.entity.EntityNRBlisteringSword");var search=base.getDeclaredMethod("searchNearbyTargets");search.setAccessible(true);search.invoke(sword);
            var selected=base.getDeclaredField("dynamicTarget");selected.setAccessible(true);require(selected.get(sword)==target,"addon homing uses same attackable policy");
            target.discard();search.invoke(sword);require(selected.get(sword)!=wolf,"homing never falls back to own pet");
            report.put("legacy17_addon_target_policy",Map.of("non_enemy_first_target",true,"black_hole_and_homing",true,"own_pet_excluded",true));
        }finally{target.discard();wolf.discard();}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError("NR targeting: "+message);}
}
