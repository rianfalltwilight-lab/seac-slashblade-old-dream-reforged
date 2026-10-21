package org.scex.slashbladelegacy;

import org.scex.slashbladelegacy.BladeStateAccess;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.TickEvent;

/** r87 AerialRave + ChargeFloating, before vanilla movement on both logical sides. */
public final class LegacyAirControl {
    private LegacyAirControl() {}
    public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.START)return;
        var player=event.player;
        LegacySuperArts.tick(player);
        LegacyRespiration.tick(player);
        if(!org.scex.slashbladelegacy.LegacyMode.legacy(player) || !player.isAlive())return;
        var blade=player.getMainHandItem();var state=BladeStateAccess.of(blade).orElse(null);
        if(state==null)return;
        int level=blade.getEnchantmentLevel(Enchantments.FALL_PROTECTION);
        if(level<=0)return;
        // The old HelmBreaker moved the player downward.
        // The modern client applies synchronized velocity in travel().
        // Preserve that downward velocity until travel().
        if(LegacyCombat.move(state.resolvCurrentComboState(player))==LegacyMove.HELM_BRAKER)return;
        var velocity=player.getDeltaMovement();double y=velocity.y;
        if(player.swinging && !player.onGround() && y<0)y=0;
        if(player.isUsingItem() && player.getUseItem()==blade) {
            if(y<0)y/=level;
            player.fallDistance=0;
        }
        if(y!=velocity.y)player.setDeltaMovement(velocity.x,y,velocity.z);
        // Local prediction executes the same pre-travel rule; no per-tick velocity packets.
    }
}
