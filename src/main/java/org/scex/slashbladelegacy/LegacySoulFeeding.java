package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.common.NeoForge;

/** Optional plain-soul feeding; existing stand operations retain priority without sneaking. */
public final class LegacySoulFeeding {
    private LegacySoulFeeding() {}
    public static void sneaking(SlashBladeEvent.BladeStandAttackEvent event) {
        if (event.getDamageSource().getEntity() instanceof ServerPlayer player && player.isShiftKeyDown()) feed(event);
    }
    public static void fallback(SlashBladeEvent.BladeStandAttackEvent event) {
        if (!event.getBladeStand().isOnFire()) feed(event);
    }
    private static int value(ItemStack soul) {
        if (soul.isEmpty() || soul.isEnchanted()) return 0;
        var tag=soul.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if (tag.contains("SpecialAttackType") || tag.contains("SpecialEffectType") || tag.contains(LegacyBladeSouls.DEFINITION)) return 0;
        if (soul.is(SlashBladeItems.PROUDSOUL_TINY.get())) return 100;
        if (soul.is(SlashBladeItems.PROUDSOUL.get())) return 200;
        if (soul.is(SlashBladeItems.PROUDSOUL_INGOT.get()) || soul.is(SlashBladeItems.PROUDSOUL_SPHERE.get())) return 400;
        if (soul.is(SlashBladeItems.PROUDSOUL_CRYSTAL.get()) || soul.is(SlashBladeItems.PROUDSOUL_TRAPEZOHEDRON.get())) return 500;
        return 0;
    }
    private static void feed(SlashBladeEvent.BladeStandAttackEvent event) {
        if (event.isCanceled() || !(event.getDamageSource().getEntity() instanceof ServerPlayer player)
                || event.getDamageSource().getDirectEntity()!=player || !LegacyMode.legacy(player)
                || !LegacyCompat.isEnabled(LegacyCompat.SOUL_FEEDING) || event.getBlade().isEmpty()) return;
        var soul=player.getMainHandItem();int amount=value(soul);if(amount==0)return;
        event.setCanceled(true);
        var blade=event.getBlade();var state=event.getSlashBladeState();int before=state.getProudSoulCount();
        if(before==Integer.MAX_VALUE)return;
        var gain=new SlashBladeEvent.AddProudSoulEvent(blade,state,amount);
        NeoForge.EVENT_BUS.post(gain);
        if(gain.getNewCount()<=0
                || player.getMainHandItem()!=soul || soul.isEmpty() || event.getBladeStand().getItem()!=blade
                || state.getProudSoulCount()!=before)return;
        state.setProudSoulCount((int)Math.min(Integer.MAX_VALUE,(long)before+gain.getNewCount()));
        soul.shrink(1);
        event.getBladeStand().setItem(blade);
        player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT,
                event.getBladeStand().getX(),event.getBladeStand().getY()+1,event.getBladeStand().getZ(),8,.2,.3,.2,.1);
    }
}
