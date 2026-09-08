package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

/** r87 Respiration on the actively used blade grants breathing and swimming acceleration. */
public final class LegacyRespiration {
    private LegacyRespiration() {}
    public static void tick(Player player) {
        if (!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) || !player.isUsingItem()) return;
        var blade = player.getMainHandItem();
        if (player.getUseItem() != blade || BladeStateAccess.of(blade).isEmpty()) return;
        int level = blade.getEnchantmentLevel(player.registryAccess().holderOrThrow(Enchantments.RESPIRATION));
        if (level <= 0) return;
        if (player.isInWater()) player.moveRelative(.1f + .05f * level, new Vec3(player.xxa, 0, player.zza));
        if (!player.level().isClientSide) player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 2, level - 1, true, true));
    }
}
