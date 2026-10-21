package org.scex.slashbladelegacy;

import java.util.*;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.entity.Projectile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Run r87 Drive movement and damage. Use inherited state for entity packets. */
public final class LegacyDrive extends EntityDrive {
    private static final EntityDataAccessor<Integer> AGE=SynchedEntityData.defineId(LegacyDrive.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> WATER=SynchedEntityData.defineId(LegacyDrive.class,EntityDataSerializers.BOOLEAN);
    private UUID source,ownerId;
    private boolean multi,dimension;
    private final Set<UUID> hit=new HashSet<>();
    public LegacyDrive(EntityType<? extends Projectile> type,Level level){super(type,level);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(AGE,0);entityData.define(WATER,false);}
    @Override public void setOwner(Entity owner){super.setOwner(owner);ownerId=owner==null?null:owner.getUUID();}
    @Override public Entity getOwner(){return level() instanceof ServerLevel server?ownerId==null?null:server.getEntity(ownerId):super.getOwner();}
    public int age(){return entityData.get(AGE);}
    public boolean multiHit(){return multi;}
    public boolean dimension(){return dimension;}
    public void setDimension(boolean value){dimension=value;}
    public boolean water(){return entityData.get(WATER);}
    public void setWater(boolean value){entityData.set(WATER,value);}
    public void initialize(Player player,ItemStack blade,float damage,float speed,float roll,boolean multiHit) {
        setOwner(player);source=identity(blade);multi=multiHit;setDamage(Math.max(1,damage));
        setSpeed(speed);setLifetime(20);setRotationRoll(roll);setColor(0x3333FF);
        // Reset the forward offset, as the r87 setInitialSpeed method does.
        setPos(player.position().add(0,player.getEyeHeight()/2,0));setVector(player.getYRot(),player.getXRot(),speed);
    }
    /** Use the shooter's yaw and pitch. Derive rendered angles from velocity. */
    public void setVector(float yaw,float pitch,float speed) {
        Vec3 velocity=Vec3.directionFromRotation(pitch,yaw).scale(speed);setDeltaMovement(velocity);
        setYRot((float)Math.toDegrees(Math.atan2(velocity.x,velocity.z)));
        setXRot((float)Math.toDegrees(Math.atan2(velocity.y,velocity.horizontalDistance())));
        yRotO=getYRot();xRotO=getXRot();
    }
    public static UUID identity(ItemStack blade){return LegacyRangeAttack.sourceId(blade);}
    @Override public void tick() {
        baseTick();if(level().isClientSide)return;
        int age=age()+1;entityData.set(AGE,age);
        if(!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) || !(getOwner() instanceof Player player) || !player.isAlive()) {discard();return;}
        ItemStack blade=LegacyRangeAttack.sourceBlade(player,source);if(blade.isEmpty()){discard();return;}
        var area=new AABB(position(),position()).inflate(1.5);
        LegacyProjectileGuard.sweep(this,player,area,(float)getDamage());
        if(!multi || age%2==0) {
            var seen=new HashSet<UUID>();
            for(var entity:LegacyTargets.within(player,area)) {
                if(blade.isEmpty())break;
                var target=entity instanceof net.minecraftforge.entity.PartEntity<?> part?part.getParent():entity;
                if(!(target instanceof LivingEntity living) || hit.contains(target.getUUID()) || !seen.add(target.getUUID()))continue;
                if(!multi)hit.add(target.getUUID());
                if(net.minecraftforge.event.ForgeEventFactory.onProjectileImpact(this,new EntityHitResult(entity)))continue;
                if(LegacyProjectileDamage.magic(this,player,blade,living,getDamage(),multi?"QuickDrive":"Drive",multi?.2f:.5f,dimension?Math.max(1,getDamage()):0) && water()) {
                    living.extinguishFire();
                    ((ServerLevel)level()).sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,living.getX(),living.getY()+living.getBbHeight()/2,living.getZ(),8,.5,.5,.5,.1);
                }
            }
        }
        // After the attack, test only the occupied block, as in r87.
        if(!level().getBlockState(blockPosition()).getCollisionShape(level(),blockPosition()).isEmpty()) {discard();return;}
        Vec3 velocity=getDeltaMovement().scale(1.05f);setDeltaMovement(velocity);setPos(position().add(velocity));
        if(age>=getLifetime())discard();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);if(source!=null)tag.putUUID("LegacySource",source);if(ownerId!=null)tag.putUUID("LegacyOwner",ownerId);
        tag.putBoolean("LegacyMulti",multi);tag.putBoolean("LegacyDimension",dimension);tag.putBoolean("LegacyWater",water());tag.putInt("LegacyAge",age());
        var hits=new net.minecraft.nbt.ListTag();for(var id:hit)hits.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));tag.put("LegacyHits",hits);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);source=tag.hasUUID("LegacySource")?tag.getUUID("LegacySource"):null;
        ownerId=tag.hasUUID("LegacyOwner")?tag.getUUID("LegacyOwner"):tag.hasUUID("Owner")?tag.getUUID("Owner"):null;
        multi=tag.getBoolean("LegacyMulti");dimension=tag.getBoolean("LegacyDimension");setWater(tag.getBoolean("LegacyWater"));entityData.set(AGE,net.minecraft.util.Mth.clamp(tag.getInt("LegacyAge"),0,200));
        setLifetime(Float.isFinite(getLifetime())?net.minecraft.util.Mth.clamp(getLifetime(),1,200):20);
        if(!Double.isFinite(getDamage()) || getDamage()<0)setDamage(1);
        if(!Float.isFinite(getRotationRoll()))setRotationRoll(0);
        hit.clear();var hits=tag.getList("LegacyHits",net.minecraft.nbt.Tag.TAG_STRING);
        for(int i=0;i<Math.min(4096,hits.size());i++)try{hit.add(UUID.fromString(hits.getString(i)));}catch(IllegalArgumentException ignored){}
    }
}
