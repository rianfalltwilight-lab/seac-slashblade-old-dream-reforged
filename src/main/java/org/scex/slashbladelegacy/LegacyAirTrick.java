package org.scex.slashbladelegacy;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import mods.flammpfeil.slashblade.ability.SlayerStyleArts;
import mods.flammpfeil.slashblade.ability.Untouchable;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.item.SwordType;
import mods.flammpfeil.slashblade.util.AdvancementHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

/** AirTrick is earned by a phantom sword hitting its target, never by a forced hit. */
public final class LegacyAirTrick {
    private LegacyAirTrick() {}
    /** r87 LastHitSummonedSwords. NextAirTrick is the earliest game time for the next trick. */
    private record LastHit(UUID sword,long next) {}
    private static final Map<ServerPlayer,LastHit> LAST_HIT=new WeakHashMap<>();
    private static final int INTERVAL=5;
    /** Record a phantom sword that stuck in an entity after accepted damage. */
    public static void stuck(Player owner,Entity sword) {
        if(owner instanceof ServerPlayer player)LAST_HIT.put(player,new LastHit(sword.getUUID(),Long.MIN_VALUE));
    }
    public static void clear(Player player) { LAST_HIT.remove(player); }
    public static boolean launch(ServerPlayer player, Entity target) {
        var blade=player.getMainHandItem();
        if(!eligible(player,blade))return false;
        // r87 SummonOrDo teleports to the last stuck sword first. It summons a marker only when that fails.
        if(trick(player,blade,target))return true;
        // An absent or invalid lock fires straight ahead. Hit checks still filter targets.
        if(!LegacyTargets.attackable(player,target))target=null;
        var state=BladeStateAccess.of(blade).orElseThrow();
        if(state.getProudSoulCount()<1)return false;
        var source=LegacyRangeAttack.sourceId(blade);
        if(LegacyRangeAttack.sourceBlade(player,source)!=blade)return false;
        var sword=new LegacyPhantomSword(SummonedBladeMode.SWORD.get(),player.level());
        sword.initializeAirTrick(player,source,LegacyRangeAttack.color(state),target);
        if(!player.level().addFreshEntity(sword))return false;
        state.setProudSoulCount(state.getProudSoulCount()-1);
        return true;
    }
    private static boolean eligible(ServerPlayer player,ItemStack blade) {
        var state=BladeStateAccess.of(blade).orElse(null);
        return player.isAlive() && LegacyMode.legacy(player) && player.getMainHandItem()==blade
                && state!=null && !state.isBroken() && !state.isSealed()
                && SwordType.from(blade).contains(SwordType.BEWITCHED)
                && blade.getEnchantmentLevel(player.registryAccess().holderOrThrow(Enchantments.POWER))>0;
    }
    /** r87 doAirTrick. The sword must be in the player's level. Distance and line of sight are not checked. */
    private static boolean trick(ServerPlayer player,ItemStack blade,Entity locked) {
        var last=LAST_HIT.get(player);
        if(last==null)return false;
        var sword=((ServerLevel)player.level()).getEntity(last.sword());
        LivingEntity target=sword instanceof LegacyPhantomSword phantom?phantom.stuckTarget()
                :sword instanceof LegacySummonedBlade summoned?summoned.stuckTarget():null;
        if(target==null || !target.isAlive() || !LegacyTargets.attackable(player,target))return false;
        // A lock on another entity cancels the trick. The caller then fires a new marker.
        if(locked!=null && locked!=target)return false;
        if(player.level().getGameTime()<last.next())return false;
        return teleport(player,blade,target,last.sword());
    }
    public static void hit(ServerPlayer player,ItemStack blade,LivingEntity target,Entity sword) {
        if(!eligible(player,blade) || !target.isAlive())return;
        teleport(player,blade,target,sword.getUUID());
    }
    private static boolean teleport(ServerPlayer player,ItemStack blade,LivingEntity target,UUID sword) {
        // r87 tries a free space one block towards the user at heights .1 .. 1.4.
        Vec3 look=player.getLookAngle().multiply(1,0,1).normalize();
        for(double height:new double[]{.5,.4,.3,.2,.1,.6,.7,.8,.9,1,1.1,1.2,1.3,1.4}) {
            var destination=target.position().subtract(look).add(0,height,0);
            var bounds=player.getBoundingBox().move(destination.subtract(player.position()));
            if(!player.level().getWorldBorder().isWithinBounds(bounds) || !player.level().noCollision(player,bounds))continue;
            player.getPersistentData().remove(SlayerStyleArts.AVOID_COUNTER_PATH);
            player.getPersistentData().remove(SlayerStyleArts.AVOID_VEC_PATH);
            player.connection.teleport(destination.x,destination.y,destination.z,player.getYRot(),player.getXRot());
            player.fallDistance=0;
            Untouchable.setUntouchable(player,20);
            AdvancementHelper.grantCriterion(player,SlayerStyleArts.ADVANCEMENT_AIR_TRICK);
            player.level().playSound(null,player.blockPosition(),SoundEvents.ENDERMAN_TELEPORT,SoundSource.PLAYERS,1,1);
            // r87 starts the sword cooldown and locks the blade on the target.
            LAST_HIT.put(player,new LastHit(sword,player.level().getGameTime()+INTERVAL));
            BladeStateAccess.of(blade).ifPresent(state->state.setTargetEntityId(target));
            return true;
        }
        return false;
    }
}
