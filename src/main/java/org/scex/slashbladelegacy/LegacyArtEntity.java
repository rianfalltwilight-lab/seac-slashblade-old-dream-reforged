package org.scex.slashbladelegacy;

import java.util.*;
import mods.flammpfeil.slashblade.ability.StunManager;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.Projectile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** r87's finite SA managers. Only the dimension field is visible; all decisions are server-owned. */
public final class LegacyArtEntity extends EntityAbstractSummonedSword {
    public enum Mode { DIMENSION, SPEAR, SAKURA, MAXIMUM, JUDGEMENT }
    private static final EntityDataAccessor<Integer> MODE=SynchedEntityData.defineId(LegacyArtEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AGE=SynchedEntityData.defineId(LegacyArtEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE=SynchedEntityData.defineId(LegacyArtEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEED=SynchedEntityData.defineId(LegacyArtEntity.class,EntityDataSerializers.INT);
    private UUID ownerId,sourceId;
    private boolean dimension;
    private static final String TELEPORT="slashblade_legacy_compat.teleport_until";
    public LegacyArtEntity(EntityType<? extends Projectile> type,Level level){super(type,level);setNoGravity(true);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(MODE,0);builder.define(AGE,0);builder.define(LIFE,10);builder.define(SEED,0);}
    public Mode mode(){return Mode.values()[Math.clamp(entityData.get(MODE),0,Mode.values().length-1)];}
    public int age(){return entityData.get(AGE);}
    public int lifetime(){return entityData.get(LIFE);}
    public int seed(){return entityData.get(SEED);}
    public boolean dimension(){return dimension;}
    @Override public void setOwner(Entity owner){super.setOwner(owner);ownerId=owner==null?null:owner.getUUID();}
    @Override public Entity getOwner(){return level() instanceof ServerLevel server?ownerId==null?null:server.getEntity(ownerId):super.getOwner();}
    public static LegacyArtEntity spawn(Player owner,ItemStack blade,Mode mode,Vec3 position,int life,float damage,boolean cut) {
        var entity=new LegacyArtEntity(SummonedBladeMode.ART.get(),owner.level());
        entity.setOwner(owner);entity.sourceId=LegacyRangeAttack.sourceId(blade);entity.setPos(position);
        entity.entityData.set(MODE,mode.ordinal());entity.entityData.set(LIFE,Math.clamp(life,1,200));entity.entityData.set(SEED,owner.getRandom().nextInt(50));
        entity.setDamage(damage);entity.dimension=cut;entity.setColor(0x3333FF);owner.level().addFreshEntity(entity);return entity;
    }
    @Override public void tick() {
        baseTick();if(level().isClientSide)return;
        entityData.set(AGE,age()+1);
        if(!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) || !(getOwner() instanceof Player owner) || !owner.isAlive()){discard();return;}
        var blade=LegacyRangeAttack.sourceBlade(owner,sourceId);
        if(blade.isEmpty() || mode()!=Mode.DIMENSION && mode()!=Mode.JUDGEMENT && !LegacyDamage.holding(owner,blade)){discard();return;}
        switch(mode()) {
            case DIMENSION->field(owner,blade);
            case SPEAR->{setPos(owner.position());var area=new AABB(position(),position()).inflate(1.5);LegacyProjectileGuard.sweep(this,owner,area,1);if(age()%2==0)LegacyArts.melee(owner,blade,area,LegacyMove.HIRA_TUKI,"Spear",-.2f,false);}
            case SAKURA,MAXIMUM->{
                if(age()==1){owner.setDeltaMovement(Vec3.ZERO);LegacyArts.syncMotion(owner);}
                else if(mode()==Mode.MAXIMUM){owner.setDeltaMovement(owner.getDeltaMovement().multiply(1,0,1));LegacyArts.syncMotion(owner);}
                if(age()==1 || age()==5) {
                    var pose=age()==1?LegacyMove.SLASH_EDGE:LegacyMove.RETURN_EDGE;
                    if(age()==5)LegacyArts.recovery(owner,mode()==Mode.SAKURA?LegacyArts.Art.SAKURA:LegacyArts.Art.MAXIMUM);
                    LegacyArts.sound(owner,SoundEvents.BLAZE_HURT,1,1);
                    var area=LegacyCombat.box(owner,pose).inflate(0,.5,0);
                    LegacyProjectileGuard.sweep(this,owner,area,1);
                    LegacyArts.melee(owner,blade,area,pose,"Spear",-.2f,false);
                    if(LegacyDamage.holding(owner,blade))LegacyArts.drive(owner,blade,mode()==Mode.SAKURA?.5f:LegacyArts.driveDamage(owner,blade),mode()==Mode.SAKURA?.1f:1.25f,90-Math.abs(pose.direction),false,20);
                }
            }
            case JUDGEMENT->judgement(owner,blade);
        }
        if(age()>=lifetime())discard();
    }
    private void field(Player owner,ItemStack blade) {
        if(age()<8 && age()%2==0)level().playSound(null,blockPosition(),SoundEvents.WITHER_HURT,SoundSource.PLAYERS,.2f,.5f+random.nextFloat()*.25f);
        var area=getBoundingBox();LegacyProjectileGuard.sweep(this,owner,area,(float)getDamage());
        if(age()%2!=0)return;
        var seen=new HashSet<UUID>();
        for(var entity:LegacyTargets.within(owner,area)) {
            var parent=entity instanceof net.neoforged.neoforge.entity.PartEntity<?> part?part.getParent():entity;
            if(!(parent instanceof LivingEntity target) || !seen.add(parent.getUUID()) || blade.isEmpty())continue;
            if(net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this,new EntityHitResult(entity)))continue;
            if(!LegacyProjectileDamage.dimension(this,owner,blade,target,dimension?Math.max(1,getDamage()):0))continue;
            if(target instanceof net.minecraft.world.entity.monster.EnderMan)target.getPersistentData().putLong(TELEPORT,level().getGameTime()+200);
            target.setDeltaMovement(Vec3.ZERO);
            if(age()>3) {
                if(dimension)target.setDeltaMovement(0,.5,0);
                else {
                    int punch=blade.getEnchantmentLevel(owner.registryAccess().holderOrThrow(Enchantments.PUNCH));
                    var velocity=Vec3.directionFromRotation(0,owner.getYRot()).scale(punch>0?-.5*punch:.5);
                    target.setDeltaMovement(velocity.x,.2,velocity.z);
                }
            }
            target.hurtMarked=true;
        }
    }
    private void judgement(Player owner,ItemStack blade) {
        owner.setDeltaMovement(Vec3.ZERO);LegacyArts.syncMotion(owner);
        var server=(ServerLevel)level();var area=new AABB(position(),position()).inflate(32,16,32);
        if(age()<3)LegacyArts.sound(owner,SoundEvents.ENDERMAN_TELEPORT,1,1);
        if(age()<8){server.sendParticles(ParticleTypes.WITCH,owner.getX(),owner.getY()+1,owner.getZ(),20,2,1,2,.1);LegacyArts.sound(owner,SoundEvents.BLAZE_HURT,.2f,.5f);}
        if(age()==2)for(var entity:LegacyTargets.within(owner,area))if(entity instanceof LivingEntity target) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,40,30,true,false));
            StunManager.setStun(target,40);LegacyFreeze.apply(target,40);
            server.sendParticles(ParticleTypes.PORTAL,target.getX(),target.getY()+target.getBbHeight()/2,target.getZ(),5,target.getBbWidth()/2,target.getBbHeight()/2,target.getBbWidth()/2,.5);
        }
        if(age()==25) {
            float damage=Math.min(1,1+BladeStateAccess.of(blade).orElseThrow().getAttackAmplifier()*LegacyArts.power(owner,blade)/5f);
            for(var entity:LegacyTargets.within(owner,area))if(entity instanceof LivingEntity target) {
                if(!LegacyDamage.holding(owner,blade))return;
                if(!LegacyDamage.hit(owner,target,LegacyMove.SLASH_DIM,area,true,1,"JudgmentCut",.1f))continue;
                owner.magicCrit(target);target.invulnerableTime=0;
                LegacyArts.spread(owner,blade,target.position().add(0,target.getEyeHeight()/2,0),target.getYRot(),damage);
                for(int i=0;i<2;i++)spawn(owner,blade,Mode.DIMENSION,target.position().add((random.nextFloat()-.5)*5,target.getBbHeight()*random.nextFloat(),(random.nextFloat()-.5)*5),10+i*3,1,true);
            }
        }
        if(age()==30)LegacyArts.finishSuper(owner,blade);
    }
    public static void teleport(net.neoforged.neoforge.event.entity.EntityTeleportEvent.EnderEntity event) {
        var entity=event.getEntity();if(!(entity instanceof net.minecraft.world.entity.monster.EnderMan) || !entity.getPersistentData().contains(TELEPORT))return;
        long left=entity.getPersistentData().getLong(TELEPORT)-entity.level().getGameTime();
        if(LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) && left>=0 && left<=200)event.setCanceled(true);else entity.getPersistentData().remove(TELEPORT);
    }
    @Override public void burst(){discard();}
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);if(ownerId!=null)tag.putUUID("LegacyOwner",ownerId);if(sourceId!=null)tag.putUUID("LegacySource",sourceId);
        tag.putInt("LegacyMode",mode().ordinal());tag.putInt("LegacyAge",age());tag.putInt("LegacyLife",lifetime());tag.putInt("LegacySeed",seed());tag.putBoolean("LegacyDimension",dimension);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);ownerId=tag.hasUUID("LegacyOwner")?tag.getUUID("LegacyOwner"):null;sourceId=tag.hasUUID("LegacySource")?tag.getUUID("LegacySource"):null;
        entityData.set(MODE,Math.clamp(tag.getInt("LegacyMode"),0,Mode.values().length-1));entityData.set(AGE,Math.clamp(tag.getInt("LegacyAge"),0,200));
        entityData.set(LIFE,Math.clamp(tag.getInt("LegacyLife"),1,200));entityData.set(SEED,Math.clamp(tag.getInt("LegacySeed"),0,49));dimension=tag.getBoolean("LegacyDimension");
        if(!Double.isFinite(getDamage()) || getDamage()<0)setDamage(1);setNoGravity(true);
    }
}
