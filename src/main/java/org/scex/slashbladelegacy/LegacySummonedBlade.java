package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.util.TargetSelector;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.ability.StunManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import mods.flammpfeil.slashblade.entity.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.util.Mth;
import java.util.Comparator;
import java.util.UUID;

/** Legacy flight parameters, modern projectile collision/packet machinery.
 * Server-only damage; source identity never substitutes the currently equipped blade.
 */
public final class LegacySummonedBlade extends EntityAbstractSummonedSword {
    private UUID sourceId,targetId,attachedId,ownerId;
    private Entity ownerCache;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> AGE=
            net.minecraft.network.syncher.SynchedEntityData.defineId(LegacySummonedBlade.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> SPIN=
            net.minecraft.network.syncher.SynchedEntityData.defineId(LegacySummonedBlade.class,net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private int flightAge,attachedAge;
    private boolean ended;
    private float aimYaw,aimPitch;
    public LegacySummonedBlade(EntityType<? extends Projectile> type,Level level) {
        super(type,level); setNoGravity(true); setPierce((byte)0);
    }
    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(AGE,0);builder.define(SPIN,-1f);
    }
    public float frozenSpin() { return entityData.get(SPIN); }
    @Override public void setOwner(Entity owner) {
        super.setOwner(owner);ownerCache=owner;ownerId=owner==null?null:owner.getUUID();
    }
    @Override public Entity getOwner() {
        if(level().isClientSide) return super.getOwner();
        if(ownerId==null) return null;
        Entity found=((ServerLevel)level()).getEntity(ownerId);
        if(found!=null) { ownerCache=found; return found; }
        return ownerCache!=null && !ownerCache.isRemoved() && ownerCache.level()==level()?ownerCache:null;
    }
    public void initialize(Player owner,int power,int color,UUID source,Entity locked) {
        setOwner(owner); sourceId=source; setDamage(power); setColor(color);
        int pattern=owner.getRandom().nextInt(6), side=pattern<3?1:-1, height=1-pattern%3;
        float[] rolls={210,180,150,-30,0,30}; setRoll(rolls[pattern]);
        Vec3 offset=Vec3.directionFromRotation(0,owner.getYRot()-90*side)
                .add(0,height*0.5,0).subtract(owner.getLookAngle());
        setPos(owner.getX()+offset.x,owner.getY()+owner.getEyeHeight()*0.5+offset.y,owner.getZ()+offset.z);
        aimYaw=owner.getYRot(); aimPitch=owner.getXRot();
        setDeltaMovement(Vec3.directionFromRotation(aimPitch,aimYaw).scale(1.75));
        if(locked instanceof LivingEntity living && eligible(living) && distanceToSqr(living)<225) targetId=locked.getUUID();
    }
    private ItemStack sourceBlade() {
        if (!(getOwner() instanceof Player player) || sourceId==null) return ItemStack.EMPTY;
        ItemStack result=ItemStack.EMPTY;
        for(int i=0;i<player.getInventory().getContainerSize();i++) {
            var candidate=player.getInventory().getItem(i);
            var data=candidate.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
            if(data.hasUUID(SummonedBladeMode.SOURCE) && sourceId.equals(data.getUUID(SummonedBladeMode.SOURCE))
                    && BladeStateAccess.of(candidate).isPresent()) {
                if(!result.isEmpty()) return ItemStack.EMPTY; // ambiguous copied identity: fail closed
                result=candidate;
            }
        }
        return result;
    }
    private boolean eligible(LivingEntity target) {
        return getOwner() instanceof LivingEntity owner && owner.isAlive() && target.isAlive()
                && target!=owner && owner.level()==level() && owner.distanceToSqr(target)<=64*64
                && owner.hasLineOfSight(target) && TargetSelector.test.test(owner,target);
    }
    private boolean clearPath(Entity target) {
        return level().clip(new ClipContext(position(),target.getBoundingBox().getCenter(),
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;
    }
    @Override public void tick() {
        if(isRemoved() || ended) return;
        if(!level().isClientSide) {
            if(!(getOwner() instanceof LivingEntity owner) || !owner.isAlive() || owner.level()!=level()
                    || distanceToSqr(owner)>64*64 || sourceBlade().isEmpty()) { discard(); return; }
            if(attachedId!=null) {
                var attached=((ServerLevel)level()).getEntity(attachedId);
                if(!(attached instanceof LivingEntity living) || !eligible(living)) { discard(); return; }
                if(getHitEntity()==null) setHitEntity(attached);
                setPos(attached.getX(),attached.getY()+attached.getEyeHeight()*0.5,attached.getZ());
                if(++attachedAge>=200) { strike(living,Math.max(1,getDamage()/2)); burst(); return; }
                setDelay(210); // own persisted 200-tick timer, not superclass default fuse
            } else if(++flightAge>=100) { burst(); return; }
            else if(flightAge>10) home();
            entityData.set(AGE,flightAge);
        }
        Vec3 motion=getDeltaMovement();
        if(getHitEntity()==null && entityData.get(AGE)<=10) setDeltaMovement(Vec3.ZERO);
        super.tick();
        // Base projectile applies 0.99 drag; the original summoned blade deliberately does not.
        if(attachedId==null && !isRemoved()) setDeltaMovement(motion);
    }
    private void home() {
        LivingEntity target=targetId==null?null:(((ServerLevel)level()).getEntity(targetId) instanceof LivingEntity e?e:null);
        if(target==null || !eligible(target) || distanceToSqr(target)>=225 || !clearPath(target)) {
            target=level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(15),
                    e -> eligible(e) && distanceToSqr(e)<225 && clearPath(e)).stream()
                    .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            targetId=target==null?null:target.getUUID();
        }
        if(target==null) return;
        Vec3 delta=target.getBoundingBox().getCenter().subtract(position());
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.horizontalDistanceSqr())));
        float dy=Mth.clamp(Mth.wrapDegrees(yaw-aimYaw),-10,10);
        float dp=Mth.clamp(Mth.wrapDegrees(pitch-aimPitch),-10,10);
        aimYaw+=dy; aimPitch+=dp;
        double turnFactor=1-Math.min((Math.abs(dy)+Math.abs(dp))/10,0.75);
        double speed=(0.75*turnFactor+getDeltaMovement().length()*9)/10;
        setDeltaMovement(Vec3.directionFromRotation(aimPitch,aimYaw).scale(speed));
    }
    @Override protected EntityHitResult getRayTrace(Vec3 start,Vec3 end) {
        EntityHitResult nearest=null;
        double nearestDistance=Double.MAX_VALUE;
        for(var candidate:level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().expandTowards(end.subtract(start)).inflate(1),
                e -> e.canBeHitByProjectile() && eligible(e) && clearPath(e))) {
            var box=candidate.getBoundingBox().inflate(0.3);
            var intersection=box.contains(start)?java.util.Optional.of(start):box.clip(start,end);
            if(intersection.isPresent() && start.distanceToSqr(intersection.get())<nearestDistance) {
                nearestDistance=start.distanceToSqr(intersection.get());
                nearest=new EntityHitResult(candidate,intersection.get());
            }
        }
        return nearest;
    }
    @Override protected void onHitEntity(EntityHitResult result) {
        if(level().isClientSide || attachedId!=null || isRemoved()) return;
        if(result.getEntity() instanceof LivingEntity target && eligible(target) && clearPath(target)) {
            if(strike(target,Math.max(1,getDamage()))) {
                attachedId=target.getUUID(); attachedAge=0; setHitEntity(target); setDelay(210);
                entityData.set(SPIN,(level().getGameTime()%6+random.nextFloat())*60);
                setDeltaMovement(Vec3.ZERO);
            }
        }
    }
    @Override protected void onHitBlock(BlockHitResult hit) {
        if(!level().isClientSide) { setPos(hit.getLocation().subtract(getDeltaMovement().normalize().scale(0.02))); burst(); }
    }
    private boolean strike(LivingEntity target,double damage) {
        if(level().isClientSide || !eligible(target) || !clearPath(target)) return false;
        var blade=sourceBlade();
        if(blade.isEmpty() || !(getOwner() instanceof LivingEntity owner)) return false;
        target.invulnerableTime=0;
        float amount=(float)(damage*AttackManager.getSlashBladeDamageScale(owner)
                *mods.flammpfeil.slashblade.SlashBladeConfig.SLASHBLADE_DAMAGE_MULTIPLIER.get());
        if(!target.hurt(damageSources().indirectMagic(this,owner),amount)) return false;
        blade.getItem().hurtEnemy(blade,target,owner);
        target.setDeltaMovement(0,0.1,0); target.hurtTime=1; StunManager.setStun(target);
        return true;
    }
    @Override public void burst() {
        if(level().isClientSide || ended || isRemoved()) return;
        ended=true;
        for(var target:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(1))) strike(target,1);
        level().playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.GLASS_BREAK,
                net.minecraft.sounds.SoundSource.PLAYERS,0.5f,1.2f);
        ((ServerLevel)level()).sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,getX(),getY(),getZ(),8,0.2,0.2,0.2,0.05);
        discard();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if(sourceId!=null) tag.putUUID("LegacySource",sourceId);
        if(ownerId!=null) tag.putUUID("LegacyOwner",ownerId);
        if(targetId!=null) tag.putUUID("LegacyTarget",targetId);
        if(attachedId!=null) tag.putUUID("LegacyAttached",attachedId);
        tag.putInt("LegacyFlightAge",flightAge);tag.putInt("LegacyAttachedAge",attachedAge);
        tag.putFloat("LegacyYaw",aimYaw);tag.putFloat("LegacyPitch",aimPitch);
        tag.putFloat("LegacyRoll",getRoll());tag.putFloat("LegacySpin",frozenSpin());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        sourceId=tag.hasUUID("LegacySource")?tag.getUUID("LegacySource"):null;
        ownerId=tag.hasUUID("LegacyOwner")?tag.getUUID("LegacyOwner"):null;
        targetId=tag.hasUUID("LegacyTarget")?tag.getUUID("LegacyTarget"):null;
        attachedId=tag.hasUUID("LegacyAttached")?tag.getUUID("LegacyAttached"):null;
        flightAge=Math.max(0,tag.getInt("LegacyFlightAge"));attachedAge=Math.max(0,tag.getInt("LegacyAttachedAge"));
        aimYaw=tag.getFloat("LegacyYaw");aimPitch=tag.getFloat("LegacyPitch");
        tickCount=flightAge; setNoGravity(true);
        setRoll(tag.getFloat("LegacyRoll"));entityData.set(SPIN,tag.getFloat("LegacySpin"));entityData.set(AGE,flightAge);
    }
}
