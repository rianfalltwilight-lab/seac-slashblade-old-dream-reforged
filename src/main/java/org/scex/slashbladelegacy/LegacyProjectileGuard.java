package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.SlashBladeConfig;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.item.SwordType;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.TargetSelector;
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
        return user instanceof Player && LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) && BladeStateAccess.of(user.getMainHandItem())
                .map(s->s.getComboRoot().equals(ComboStateRegistry.STANDBY.getId()) &&
                        (s.getComboSeq().getNamespace().equals(LegacyCompat.MOD_ID) || s.getComboSeq().equals(ComboStateRegistry.NONE.getId()))).orElse(false);
    }
    public static void tick(Player player) {
        if(!handles(player) || player.level().isClientSide || !player.swinging || player.swingTime==0)return;
        var state=BladeStateAccess.of(player.getMainHandItem()).orElseThrow();
        var move=LegacyCombat.move(state.resolvCurrentComboState(player));
        if(move==LegacyMove.NONE || move==LegacyMove.NOUTOU)return;
        intercept(player,player.getMainHandItem(),LegacyCombat.box(player,move),true,true);
    }
    private static boolean protectedOwner(Player player,Entity owner) {
        return owner==player || owner!=null && (owner.isAlliedTo(player) || player.isAlliedTo(owner)
                || owner instanceof OwnableEntity own && player.getUUID().equals(own.getOwnerUUID())
                || owner instanceof Player && !SlashBladeConfig.PVP_ENABLE.get());
    }
    public static void intercept(Player player,ItemStack blade,AABB box,boolean redirect,boolean wear) {
        if(player.level().isClientSide || !player.isAlive() || blade.isEmpty())return;
        boolean bewitched=redirect && SwordType.from(blade).contains(SwordType.BEWITCHED);
        boolean thorns=blade.getEnchantmentLevel(player.registryAccess().holderOrThrow(Enchantments.THORNS))>0;
        int destroyed=0;double reach=TargetSelector.getResolvedReach(player);
        for(Entity entity:player.level().getEntities(player,box,e->e instanceof Projectile || e instanceof PrimedTnt)) {
            Entity owner=entity instanceof Projectile projectile?projectile.getOwner():((PrimedTnt)entity).getOwner();
            Entity shooter=entity instanceof mods.flammpfeil.slashblade.entity.IShootable shootable?shootable.getShooter():null;
            if(!entity.isAlive() || protectedOwner(player,owner) || protectedOwner(player,shooter) || !player.hasLineOfSight(entity))continue;
            if(owner==null)owner=shooter;
            if(redirect && TargetSelector.distanceSqrBetweenEntity(entity,player)>=reach*reach)continue;
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
                if(redirect && entity instanceof AbstractHurtingProjectile && entity.hurt(player.damageSources().mobAttack(player),1))continue;
                entity.discard();destroyed++;
            }
        }
        if(wear && destroyed>0)LegacyAdditionalAttack.wear(blade,1,player);
    }
}
