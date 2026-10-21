package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.ability.StunManager;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Track synchronous hits. Preserve protection events and addon hit callbacks. */
public final class LegacyProjectileDamage {
    private LegacyProjectileDamage() {}
    private static final class Hit {
        final Player owner;final DamageSource source;final String action;final float award;
        Hit(Player owner,DamageSource source,String action,float award){this.owner=owner;this.source=source;this.action=action;this.award=award;}
    }
    private static final ThreadLocal<Hit> ACTIVE=new ThreadLocal<>();
    public static boolean award(IConcentrationRank rank,DamageSource source) {
        var hit=ACTIVE.get();
        if(hit==null || hit.source!=source || !LegacyCompat.isEnabled(LegacyCompat.LEGACY_RANK))return false;
        // Award rank only after the target accepts damage.
        return true;
    }
    public static boolean strike(Entity projectile,Player owner,ItemStack blade,LivingEntity target,double damage,
                                 boolean breaking,double lift,boolean freeze) {
        return strike(projectile,owner,blade,target,damage,breaking,lift,freeze,0);
    }
    public static boolean strike(Entity projectile,Player owner,ItemStack blade,LivingEntity target,double damage,
                                 boolean breaking,double lift,boolean freeze,double cut) {
        if(!magic(projectile,owner,blade,target,damage,breaking?"BreakPhantomSword":"PhantomSword",breaking?.1f:.2f,cut))return false;
        target.setDeltaMovement(0,lift,0);target.hurtTime=1;StunManager.setStun(target);
        if(freeze)LegacyFreeze.apply(target,20);
        return true;
    }
    /** Apply old direct magic with the art's rank type and optional health cut. */
    public static boolean magic(Entity projectile,Player owner,ItemStack blade,LivingEntity target,double damage,
                                String action,float award,double healthCut) {
        return apply(projectile,owner,blade,target,damage,action,award,healthCut,false,false);
    }
    /** Apply the r87 dimension health cut and hit callback. Do not add magic damage. */
    public static boolean dimension(Entity field,Player owner,ItemStack blade,LivingEntity target,double cut) {
        boolean accepted=apply(field,owner,blade,target,0,"SlashDimMagic",-.1f,cut,true,false);
        if(accepted)target.invulnerableTime=0;
        return accepted;
    }
    public static boolean absolute(Entity marker,Player owner,ItemStack blade,LivingEntity target,int damage) {
        return apply(marker,owner,blade,target,damage,"PhantomSword",.2f,0,false,true);
    }
    private static boolean apply(Entity projectile,Player owner,ItemStack blade,LivingEntity target,double damage,
                                 String action,float award,double healthCut,boolean nonlethal,boolean absolute) {
        if(projectile.level().isClientSide || blade.isEmpty() || !LegacyTargets.attackable(owner,target))return false;
        // Name the player as the direct source, as r87 directMagic does.
        var source=nonlethal || absolute?new DamageSource(owner.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(nonlethal?LegacyDamageTypes.DIMENSION:LegacyDamageTypes.UPTHRUST),owner,owner):owner.damageSources().indirectMagic(owner,owner);
        var previous=ACTIVE.get();
        var hit=new Hit(owner,source,action,award);
        ACTIVE.set(hit);
        int invulnerability=target.invulnerableTime;
        float cut=(float)Math.min(Math.max(0,healthCut),Math.max(0,target.getHealth()-1));
        boolean hurt=false;
        try {
            target.invulnerableTime=0;
            float amount=nonlethal?0:absolute?(float)Math.max(1,damage):(float)(Math.max(1,damage)*BladeApi.damageScale(owner));
            hurt=target.hurt(source,amount);
            if(!hurt)return false;
            // Apply the saved health cut only after hurt accepts the hit.
            // Keep the r87 arithmetic. A lethal magic hit can still kill the target.
            if(cut>0 && target.isAlive()) {
                target.setHealth(Math.max(nonlethal?1:0,target.getHealth()-cut));
                if(target.getHealth()<=0)target.die(source);
            }
            LegacyRank.awardAction(owner,LegacyCapabilities.rank(owner),hit.action,hit.award);
            blade.getItem().hurtEnemy(blade,target,owner);
            return true;
        }finally {
            if(!hurt && target.invulnerableTime==0)target.invulnerableTime=invulnerability;
            if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);
        }
    }
}
