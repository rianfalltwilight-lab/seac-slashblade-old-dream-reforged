package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** r87 AerialRave + ChargeFloating, before vanilla movement on both logical sides. */
public final class LegacyAirControl {
    private LegacyAirControl() {}
    public static void tick(PlayerTickEvent.Pre event) {
        var player=event.getEntity();
        LegacySuperArts.tick(player);
        LegacyRespiration.tick(player);
        if(!org.scex.slashbladelegacy.LegacyMode.legacy(player) || !player.isAlive())return;
        var blade=player.getMainHandItem();var state=BladeStateAccess.of(blade).orElse(null);
        if(state==null)return;
        int level=blade.getEnchantmentLevel(player.registryAccess().holderOrThrow(Enchantments.FEATHER_FALLING));
        if(level<=0)return;
        // The old HelmBreaker moved downward explicitly. The modern client applies its
        // synchronized velocity through travel, so do not erase that descent before travel.
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
