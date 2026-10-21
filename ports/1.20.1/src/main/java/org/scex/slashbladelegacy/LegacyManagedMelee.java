package org.scex.slashbladelegacy;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Apply the same legacy hit to both upstream melee overloads. */
public final class LegacyManagedMelee {
    private LegacyManagedMelee() {}

    public static boolean handle(LivingEntity user,Entity target,boolean reset,float ratio) {
        if(!LegacyMode.legacy(user) || !(user instanceof Player player)
                || BladeStateAccess.of(player.getMainHandItem()).isEmpty())return false;
        if(player.level().isClientSide || !Float.isFinite(ratio) || ratio<=0 || !LegacyTargets.attackable(player,target))return true;
        LegacyDamage.update(player);
        var state=BladeStateAccess.of(player.getMainHandItem()).orElseThrow();
        var move=LegacyCombat.move(state.getComboSeq());
        if(move==LegacyMove.NONE)move=LegacyMove.SLASH_DIM;
        // The SA has already selected the target area.
        boolean hit=LegacyDamage.hit(player,target,move,target.getBoundingBox(),true,ratio);
        if(hit && reset)target.invulnerableTime=0;
        return true;
    }
}
