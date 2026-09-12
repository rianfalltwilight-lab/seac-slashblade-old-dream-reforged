package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.SlashBladeConfig;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.item.SwordType;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.*;

public final class LegacyProjectileGuard {
    private LegacyProjectileGuard(){}
    public static boolean handles(LivingEntity user) {
        return user instanceof Player && org.scex.slashbladelegacy.LegacyMode.legacy(user) && BladeStateAccess.of(user.getMainHandItem())
                .map(s->
                        (s.getComboSeq().getNamespace().equals(LegacyCompat.MOD_ID) || s.getComboSeq().equals(ComboStateRegistry.NONE.getId()))).orElse(false);
    }
    public static void tick(Player player) {
        if(barrierAvailable(player,player.getTicksUsingItem()))barrier(player);
        if(!handles(player) || player.level().isClientSide || !player.swinging || player.swingTime==0)return;
        var state=BladeStateAccess.of(player.getMainHandItem()).orElseThrow();
        var move=LegacyCombat.move(state.resolvCurrentComboState(player));
        if(move==LegacyMove.NONE || move==LegacyMove.NOUTOU)return;
        intercept(player,player.getMainHandItem(),LegacyCombat.box(player,move),true,true);
    }
    public static boolean barrierAvailable(Player player,int elapsed) {
        var blade=player.getMainHandItem();
        return org.scex.slashbladelegacy.LegacyMode.legacy(player) && player.isAlive() && player.onGround()
                && player.isShiftKeyDown() && player.isUsingItem() && player.getUseItem()==blade && elapsed>=15
                && BladeStateAccess.of(blade).map(state->!state.isBroken()).orElse(false)
                && blade.getEnchantmentLevel(player.registryAccess().holderOrThrow(Enchantments.THORNS))>0;
    }
    private static void barrier(Player player) {
        if(player.level().isClientSide)return;
        var blade=player.getMainHandItem();
        if(player.tickCount%7==0)player.level().playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                net.minecraft.sounds.SoundSource.PLAYERS,1,.75f+player.getRandom().nextFloat()*.05f);
        for(Entity entity:player.level().getEntities(player,player.getBoundingBox().inflate(2),e->e instanceof Projectile || e instanceof PrimedTnt)) {
            if(!LegacyDamage.holding(player,blade) || BladeStateAccess.of(blade).orElseThrow().isBroken())break;
            if(!entity.isAlive() || entity instanceof mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword)continue;
            Entity owner=entity instanceof Projectile projectile?projectile.getOwner():((PrimedTnt)entity).getOwner();
            Entity shooter=entity instanceof mods.flammpfeil.slashblade.entity.IShootable shootable?shootable.getShooter():null;
            if(protectedOwner(player,owner) || protectedOwner(player,shooter))continue;
            player.magicCrit(entity);
            player.level().playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.ANVIL_LAND,
                    net.minecraft.sounds.SoundSource.PLAYERS,.8f,1.5f+player.getRandom().nextFloat()*.5f);
            entity.setDeltaMovement(Vec3.ZERO);entity.discard();
            LegacyAdditionalAttack.wear(blade,1,player);
        }
    }
    public static boolean protectedOwner(Player player,Entity owner) {
        return owner==player || owner!=null && (owner.isAlliedTo(player) || player.isAlliedTo(owner)
                || owner instanceof OwnableEntity own && player.getUUID().equals(own.getOwnerUUID())
                || owner instanceof Player && !SlashBladeConfig.PVP_ENABLE.get());
    }
    public static boolean destructible(Player player,Entity entity) {
        if(!entity.isAlive() || !(entity instanceof Projectile || entity instanceof PrimedTnt))return false;
        Entity owner=entity instanceof Projectile projectile?projectile.getOwner():((PrimedTnt)entity).getOwner();
        Entity shooter=entity instanceof mods.flammpfeil.slashblade.entity.IShootable shootable?shootable.getShooter():null;
        return !protectedOwner(player,owner) && !protectedOwner(player,shooter);
    }
    /** r87 summoned attacks ask fireballs to deflect before destruction, and score either accepted outcome. */
    public static boolean destruct(Player player,Entity entity,float amount) {
        if(player.level().isClientSide || !destructible(player,entity))return false;
        int deflection=deflectFireball(player,entity,amount);
        if(deflection<0)return false;
        if(deflection==0) {
            entity.setDeltaMovement(Vec3.ZERO);entity.discard();
            ((net.minecraft.server.level.ServerLevel)player.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                    entity.getX(),entity.getY()+entity.getBbHeight()/2,entity.getZ(),10,entity.getBbWidth()/2,entity.getBbHeight()/2,entity.getBbWidth()/2,.02);
        }
        LegacyRank.awardAction(player,player.getData(mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank.RANK_POINT),"DestructObject",.1f);
        return true;
    }
    /** 1 reflected, 0 destructible, -1 protected/refused; a rejected modern deflection is not destruction. */
    private static int deflectFireball(Player player,Entity entity,float amount) {
        if(!(entity instanceof AbstractHurtingProjectile fireball))return 0;
        var source=player.damageSources().mobAttack(player);
        if(fireball.isInvulnerableTo(source))return -1;
        // 1.21 Fireball.hurt always returns false. Player.attack first uses this public tagged path.
        if(fireball.getType().is(net.minecraft.tags.EntityTypeTags.REDIRECTABLE_PROJECTILE))
            return fireball.deflect(ProjectileDeflection.AIM_DEFLECT,player,player,true)?1:-1;
        var oldOwner=fireball.getOwner();
        if(!fireball.hurt(source,amount))return 0;
        // Since 1.21, hurt only accepts the hit; Player.attack performs deflection separately.
        // Keep a modded hurt callback's owner change, otherwise invoke the modern deflection API.
        if(fireball.getOwner()==oldOwner && !fireball.deflect(ProjectileDeflection.AIM_DEFLECT,player,player,true))return -1;
        return 1;
    }
    public static void sweep(Entity attack,Player player,AABB box,float amount) {
        for(var target:player.level().getEntities(attack,box,e->destructible(player,e)))
            if(!net.neoforged.neoforge.event.EventHooks.onProjectileImpact((Projectile)attack,new EntityHitResult(target)))destruct(player,target,amount);
    }
    public static void intercept(Player player,ItemStack blade,AABB box,boolean redirect,boolean wear) {
        if(player.level().isClientSide || !player.isAlive() || blade.isEmpty())return;
        boolean bewitched=redirect && SwordType.from(blade).contains(SwordType.BEWITCHED);
        boolean thorns=blade.getEnchantmentLevel(player.registryAccess().holderOrThrow(Enchantments.THORNS))>0;
        int destroyed=0;
        for(Entity entity:player.level().getEntities(player,box,e->e instanceof Projectile || e instanceof PrimedTnt)) {
            Entity owner=entity instanceof Projectile projectile?projectile.getOwner():((PrimedTnt)entity).getOwner();
            Entity shooter=entity instanceof mods.flammpfeil.slashblade.entity.IShootable shootable?shootable.getShooter():null;
            if(!entity.isAlive() || protectedOwner(player,owner) || protectedOwner(player,shooter))continue;
            if(owner==null)owner=shooter;
            if(bewitched && entity instanceof Projectile projectile) {
                Vec3 direction=player.getLookAngle();
                if(thorns)direction=owner!=null && (projectile instanceof AbstractArrow || projectile instanceof AbstractHurtingProjectile)
                        ?owner.getEyePosition().subtract(entity.position()).normalize():entity.getDeltaMovement().scale(-1).normalize();
                if(direction.lengthSqr()<1e-8)direction=player.getLookAngle();
                projectile.setOwner(player);projectile.shoot(direction.x,direction.y,direction.z,1.5f,0);
                if(projectile instanceof AbstractArrow arrow)arrow.setCritArrow(true);
                if(projectile instanceof AbstractHurtingProjectile fireball)fireball.accelerationPower=.1;
                projectile.hurtMarked=true;
            }else {
                // Unenchanted blades retain fireballs' own deflection callback before destruction.
                if(redirect && deflectFireball(player,entity,1)!=0)continue;
                entity.discard();destroyed++;
            }
        }
        if(wear && destroyed>0)LegacyAdditionalAttack.wear(blade,1,player);
    }
}
