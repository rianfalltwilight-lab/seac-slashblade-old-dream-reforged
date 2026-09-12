package org.scex.slashbladelegacy;

import java.util.*;
import mods.flammpfeil.slashblade.ability.Untouchable;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** r87 JustGuard: seven ticks from use, followed by five frozen ticks and one Battou. */
public final class LegacyJustGuard {
    private static final Map<Player, Window> WINDOWS = new WeakHashMap<>();
    private static final class Window {
        final ItemStack blade;
        final ResourceKey<Level> dimension;
        final long start;
        long guarded = -1, processed = -1;
        double y;
        List<MobEffectInstance> effects = List.of();
        Window(Player player, ItemStack blade) {
            this.blade = blade; dimension = player.level().dimension();
            start = player.level().getGameTime() + Math.clamp(player.invulnerableTime - 10, 0, 20);
        }
    }
    private LegacyJustGuard() {}
    public static void begin(Player player, ItemStack blade) {
        if (player.level().isClientSide || !org.scex.slashbladelegacy.LegacyMode.legacy(player)
                || player.getMainHandItem() != blade || BladeStateAccess.of(blade).isEmpty()) return;
        var previous = WINDOWS.get(player);
        if (previous != null && valid(player, previous) && previous.guarded >= 0) return;
        WINDOWS.put(player, new Window(player, blade));
    }
    private static boolean valid(Player player, Window window) {
        return org.scex.slashbladelegacy.LegacyMode.legacy(player) && LegacyDamage.holding(player, window.blade)
                && player.level().dimension().equals(window.dimension);
    }
    public static void incoming(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        var window = WINDOWS.get(player);
        if (window == null || !valid(player, window) || window.guarded >= 0 || !player.isUsingItem()
                || player.getUseItem() != window.blade) return;
        var source = event.getSource();
        if (source.getEntity() == null && source.is(DamageTypeTags.BYPASSES_ARMOR)) return;
        long now = player.level().getGameTime();
        if (window.start <= 0 || now - window.start >= 7) return;
        event.setCanceled(true);
        window.guarded = now;
        window.y = player.getY() + (player.onGround() ? .5 : 0);
        window.effects = player.getActiveEffects().stream().map(MobEffectInstance::new).toList();
        player.setArrowCount(Math.max(0, player.getArrowCount() - 1));
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        Untouchable.setUntouchable(player, 20);
        LegacyCombat.resetAirAttack(player);
        LegacyAdditionalAttack.markCharged(player, LegacyCombat.id(LegacyMove.BATTOU));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BLAZE_HURT, SoundSource.PLAYERS, 1, 1);
        LegacyRank.awardAction(player, player.getData(CapabilityConcentrationRank.RANK_POINT), "JustGuard", 1);
    }
    public static void tick(Player player) {
        var window = WINDOWS.get(player);
        if (window == null) return;
        if (!valid(player, window)) { WINDOWS.remove(player); return; }
        long now = player.level().getGameTime();
        if (window.guarded < 0) {
            if (!player.isUsingItem() || now - window.start >= 7) WINDOWS.remove(player);
            return;
        }
        long age = now - window.guarded;
        if (age <= 0 || window.processed == now) return;
        window.processed = now;
        if (age == 1) {
            BladeStateAccess.of(window.blade).orElseThrow().updateComboSeq(player, LegacyCombat.id(LegacyMove.BATTOU));
            if (!valid(player, window)) { WINDOWS.remove(player); return; }
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(player.getX(), window.y, player.getZ());
        player.hurtMarked = true;
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.teleport(player.getX(), window.y, player.getZ(), player.getYRot(), player.getXRot());
            serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
        }
        if (age >= 5) {
            // The original only restores when there was a nonempty snapshot.
            if (!window.effects.isEmpty()) {
                player.removeAllEffects();
                window.effects.forEach(effect -> player.addEffect(new MobEffectInstance(effect)));
            }
            WINDOWS.remove(player);
        }
    }
    public static void clear(Player player) { WINDOWS.remove(player); }
}
