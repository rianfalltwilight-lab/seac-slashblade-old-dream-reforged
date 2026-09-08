package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.item.SwordType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import org.apache.commons.lang3.mutable.MutableFloat;

/** r87 attack amplifier and 1.7.10 attack order; modern protection/SE hooks remain observable. */
public final class LegacyDamage {
    private record Hit(Player player, Entity target, ItemStack blade, AABB bounds, DamageSource source, String rankAction, float rankFactor) {}
    private static final ThreadLocal<Hit> HIT = new ThreadLocal<>();
    private LegacyDamage() {}

    public static float amplifier(Player player, ItemStack blade) {
        var state = BladeStateAccess.of(blade).orElseThrow();
        int rank = LegacyCombat.rank(player);
        if (rank < 3 || state.isBroken() || state.isSealed()) return 2 - state.getBaseAttackModifier();
        if (rank == 7 || rank >= 5 && SwordType.from(blade).contains(SwordType.FIERCEREDGE))
            return Math.min(player.experienceLevel, 10f + state.getRefine());
        return 0;
    }

    public static void update(Player player) {
        if (!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT)) return;
        var blade = player.getMainHandItem();
        BladeStateAccess.of(blade).ifPresent(state -> {
            float value = amplifier(player, blade);
            if (state.getAttackAmplifier() != value) state.setAttackAmplifier(value);
        });
    }

    public static boolean scoped(Player player, Entity target, ItemStack blade) {
        Hit hit = HIT.get();
        return hit != null && hit.player == player && hit.target == target && hit.blade == blade
                && target.getBoundingBox().intersects(hit.bounds);
    }

    public static boolean holding(Player player, ItemStack blade) {
        return player.isAlive() && !blade.isEmpty() && player.getMainHandItem() == blade
                && BladeStateAccess.of(blade).isPresent();
    }

    /** Suppress only this hurt call's native award; successful hits award their actual SA move below. */
    public static boolean ownsRankSource(DamageSource source) {
        Hit hit=HIT.get();
        return hit!=null && hit.source==source && LegacyCompat.isEnabled(LegacyCompat.LEGACY_RANK);
    }

    private static boolean hurt(Player player,Entity target,DamageSource source,float amount,LegacyMove move) {
        Hit previous=HIT.get();
        HIT.set(new Hit(previous.player,previous.target,previous.blade,previous.bounds,source,previous.rankAction,previous.rankFactor));
        int invulnerability=target.invulnerableTime;
        boolean accepted=false;
        try {
            target.invulnerableTime=0;
            accepted=target.hurt(source,amount);
            if(accepted) {
                var rank=player.getData(mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank.RANK_POINT);
                if(previous.rankAction==null)LegacyRank.awardMove(player,rank,move);
                else LegacyRank.awardAction(player,rank,previous.rankAction,previous.rankFactor);
            }
            return accepted;
        } finally {
            // Preserve a protection listener's replacement timer as well as the previous timer.
            if(!accepted && target.invulnerableTime==0)target.invulnerableTime=invulnerability;
            HIT.set(previous);
        }
    }

    public static float base(Player player, ItemStack blade) {
        // Recompute the same attribute operations on a snapshot. No attack cooldown ticker or
        // temporary mutation of the player's actual attributes is involved in an area hit.
        var attribute = new AttributeInstance(Attributes.ATTACK_DAMAGE, ignored -> {});
        attribute.replaceFrom(player.getAttribute(Attributes.ATTACK_DAMAGE));
        blade.getAttributeModifiers().forEach(EquipmentSlot.MAINHAND, (key, modifier) -> {
            if (key.equals(Attributes.ATTACK_DAMAGE)) {
                attribute.removeModifier(modifier.id());attribute.addTransientModifier(modifier);
            }
        });
        // Vanilla 1.7.10 PotionAttackDamage: Strength +130% total per level, Weakness -0.5.
        // These substitutions are scoped to blade damage; unrelated held tools keep modern rules.
        var strength=player.getEffect(MobEffects.DAMAGE_BOOST);
        if(strength!=null) {
            var id=ResourceLocation.withDefaultNamespace("effect.strength");attribute.removeModifier(id);
            attribute.addTransientModifier(new AttributeModifier(id,1.3*(strength.getAmplifier()+1),AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        var weakness=player.getEffect(MobEffects.WEAKNESS);
        if(weakness!=null) {
            var id=ResourceLocation.withDefaultNamespace("effect.weakness");attribute.removeModifier(id);
            attribute.addTransientModifier(new AttributeModifier(id,-.5*(weakness.getAmplifier()+1),AttributeModifier.Operation.ADD_VALUE));
        }
        return (float) attribute.getValue();
    }

    public static float enchantmentBonus(Player player, ItemStack blade, Entity target, DamageSource source, float base) {
        if (!(target instanceof LivingEntity)) return 0;
        var enchantments = blade.getAllEnchantments(player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT));
        float bonus = 0;
        for (var entry : enchantments.entrySet()) {
            var enchantment = entry.getKey(); int level = entry.getIntValue();
            if (enchantment.is(Enchantments.SHARPNESS)) bonus += level * 1.25f;
            else if (enchantment.is(Enchantments.SMITE) && target.getType().is(EntityTypeTags.UNDEAD)) bonus += level * 2.5f;
            else if (enchantment.is(Enchantments.BANE_OF_ARTHROPODS) && target.getType().is(EntityTypeTags.ARTHROPOD)) bonus += level * 2.5f;
        }
        MutableFloat result = new MutableFloat(base + bonus);
        for (var entry : enchantments.entrySet()) {
            var enchantment = entry.getKey();
            // Keep unknown addon enchantments on their public data-component API, on the real blade.
            if (!enchantment.is(Enchantments.SHARPNESS) && !enchantment.is(Enchantments.SMITE)
                    && !enchantment.is(Enchantments.BANE_OF_ARTHROPODS))
                enchantment.value().modifyDamage((ServerLevel) player.level(), entry.getIntValue(), blade, target, source, result);
        }
        return result.floatValue() - base;
    }

    public static boolean hit(Player player, Entity target, LegacyMove move, AABB bounds, boolean postAttackEvent) {
        return hit(player,target,move,bounds,postAttackEvent,1);
    }

    /** Explicit SA coefficient belongs to the addon; the shared melee order remains r87. */
    public static boolean hit(Player player, Entity target, LegacyMove move, AABB bounds, boolean postAttackEvent, float coefficient) {
        return hit(player,target,move,bounds,postAttackEvent,coefficient,null,0);
    }
    public static boolean hit(Player player, Entity target, LegacyMove move, AABB bounds, boolean postAttackEvent, float coefficient,String rankAction,float rankFactor) {
        ItemStack blade = player.getMainHandItem();
        if (player.level().isClientSide || !holding(player, blade)) return false;
        var state = BladeStateAccess.of(blade).orElseThrow();
        boolean previousClick = state.onClick(); Hit previous = HIT.get();
        state.setOnClick(true); HIT.set(new Hit(player, target, blade, bounds, null,rankAction,rankFactor));
        try {
            if (postAttackEvent && NeoForge.EVENT_BUS.post(new AttackEntityEvent(player, target)).isCanceled()) return false;
            if (!holding(player, blade) || !target.isAttackable() || target.skipAttackInteraction(player)) return false;
            if (move.scabbard && target instanceof LivingEntity living) {
                float damage = LegacyCombat.rank(player) < 3 || state.isBroken() ? 2 : 5;
                if (LegacyCombat.rank(player) >= 3 && !state.isBroken() && SwordType.from(blade).contains(SwordType.FIERCEREDGE))
                    damage += state.getAttackAmplifier() * .5f;
                DamageSource source = player.damageSources().mobAttack(player);
                damage += Math.max(0, enchantmentBonus(player, blade, target, source, damage));
                damage = Math.min(damage, living.getHealth() - 1);
                if (damage <= 0 || !holding(player, blade)) return false;
                if (!hurt(player,living,source,damage,move)) return false;
                if (holding(player, blade) && !NeoForge.EVENT_BUS.post(new mods.flammpfeil.slashblade.event.SlashBladeEvent.HitEvent(blade, state, living, player)).isCanceled())
                    LegacyCombat.impact(player, living, move);
                return true;
            }
            float damage = base(player, blade)*coefficient;
            DamageSource source = player.damageSources().playerAttack(player);
            float bonus = enchantmentBonus(player, blade, target, source, damage);
            boolean critical = player.fallDistance > 0 && !player.onGround() && !player.onClimbable()
                    && !player.isInWater() && !player.hasEffect(MobEffects.BLINDNESS) && !player.isPassenger()
                    && target instanceof LivingEntity;
            var criticalEvent = CommonHooks.fireCriticalHit(player, target, critical, critical ? 1.5f : 1);
            if (criticalEvent.isCriticalHit() && damage > 0) damage *= criticalEvent.getDamageMultiplier();
            damage += bonus;
            if (damage <= 0 || !holding(player, blade)) return false;
            int fire = blade.getEnchantmentLevel(player.registryAccess().holderOrThrow(Enchantments.FIRE_ASPECT));
            boolean preliminaryFire = target instanceof LivingEntity && fire > 0 && !target.isOnFire();
            int previousFire=target.getRemainingFireTicks();
            if (preliminaryFire) target.igniteForSeconds(1);
            if (!hurt(player,target,source,damage,move)) {
                if (preliminaryFire && target.getRemainingFireTicks()==20) target.setRemainingFireTicks(previousFire);
                return false;
            }
            int knockback = blade.getEnchantmentLevel(player.registryAccess().holderOrThrow(Enchantments.KNOCKBACK)) + (player.isSprinting() ? 1 : 0);
            if (knockback > 0) {
                double yaw = Math.toRadians(player.getYRot());
                target.push(-Math.sin(yaw) * knockback * .5, .1, Math.cos(yaw) * knockback * .5);
                player.setDeltaMovement(player.getDeltaMovement().multiply(.6, 1, .6)); player.setSprinting(false);
            }
            if (criticalEvent.isCriticalHit()) player.crit(target);
            if (bonus > 0) player.magicCrit(target);
            player.setLastHurtMob(target);
            // Use the actual held blade for effects which consume durability or change SE state.
            if (holding(player, blade)) LegacyEnchantments.postHit(player,blade,target,source);
            Entity parent = target instanceof PartEntity<?> part ? part.getParent() : target;
            if (holding(player, blade) && parent instanceof LivingEntity living) blade.hurtEnemy(living, player);
            if (target instanceof LivingEntity) {
                player.awardStat(Stats.DAMAGE_DEALT, Math.round(damage * 10));
                if (fire > 0) target.igniteForSeconds(fire * 4f);
            }
            player.causeFoodExhaustion(.3f);
            return true;
        } finally {
            state.setOnClick(previousClick);
            if (previous == null) HIT.remove(); else HIT.set(previous);
        }
    }
}
