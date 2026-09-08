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

/** r87 flight/collision with framework packets; the optional pre-r87 adapter remains when full legacy is disabled.
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
    private Vec3 attachedOffset=Vec3.ZERO;
    private float attachedYaw,attachedPitch;
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
        float[] rolls={210,-180,150,-30,0,30}; setRoll(rolls[pattern]);
        Vec3 offset=Vec3.directionFromRotation(0,owner.getYRot()-90*side)
                .add(0,height*0.5,0).subtract(owner.getLookAngle());
        setPos(owner.getX()+offset.x,owner.getY()+(LegacyRangeAttack.enabled()?0:owner.getEyeHeight()*.5)+offset.y,owner.getZ()+offset.z);
        aimYaw=owner.getYRot(); aimPitch=owner.getXRot();
        setDeltaMovement(Vec3.directionFromRotation(aimPitch,aimYaw).scale(1.75));
        if(LegacyRangeAttack.enabled())orientLegacy();
        if(locked instanceof LivingEntity living && eligible(living) && (LegacyRangeAttack.enabled() || distanceToSqr(living)<225)) targetId=locked.getUUID();
    }
    private ItemStack sourceBlade() {
        if (!(getOwner() instanceof Player player) || sourceId==null) return ItemStack.EMPTY;
        if(LegacyRangeAttack.enabled())return LegacyRangeAttack.sourceBlade(player,sourceId);
        ItemStack result=ItemStack.EMPTY;
        for(int i=0;i<player.getInventory().getContainerSize();i++) {
            var candidate=player.getInventory().getItem(i);
            if(candidate.isEmpty() || BladeStateAccess.of(candidate).isEmpty())continue;
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
        if(LegacyRangeAttack.enabled())return getOwner() instanceof Player player && LegacyTargets.attackable(player,target);
        return getOwner() instanceof LivingEntity owner && owner.isAlive() && target.isAlive()
                && target!=owner && owner.level()==level() && owner.distanceToSqr(target)<=64*64
                && owner.hasLineOfSight(target) && (LegacyRangeAttack.enabled() && owner instanceof Player player
                    ?LegacyTargets.attackable(player,target):TargetSelector.test.test(owner,target));
    }
    private boolean clearPath(Entity target) {
        return level().clip(new ClipContext(position(),target.getBoundingBox().getCenter(),
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;
    }
    @Override public void tick() {
        if(isRemoved() || ended) return;
        if(LegacyRangeAttack.enabled()){legacyTick();return;}
        if(!level().isClientSide) {
            if(!(getOwner() instanceof LivingEntity owner) || !owner.isAlive() || owner.level()!=level()
                    || distanceToSqr(owner)>64*64 || sourceBlade().isEmpty()) { discard(); return; }
            if(attachedId!=null) {
                var attached=((ServerLevel)level()).getEntity(attachedId);
                if(!(attached instanceof LivingEntity living) || !eligible(living)) { discard(); return; }
                if(getHitEntity()==null) setHitEntity(attached);
                setPos(attached.getX(),attached.getY()+attached.getEyeHeight()*0.5,attached.getZ());
                if(++attachedAge>=200) { strike(living,Math.max(1,getDamage()/2),true); burst(); return; }
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
    private void legacyTick() {
        baseTick();if(level().isClientSide)return;
        if(!(getOwner() instanceof Player owner) || !owner.isAlive() || sourceBlade().isEmpty()){discard();return;}
        yRotO=getYRot();xRotO=getXRot();
        if(attachedId!=null) {
            var entity=((ServerLevel)level()).getEntity(attachedId);
            if(!(entity instanceof LivingEntity target) || !eligible(target)){discard();return;}
            if(getHitEntity()==null)setHitEntity(target);
            double yaw=Math.toRadians(target.getYRot());
            setPos(target.position().add(attachedOffset.x*Math.cos(yaw)-attachedOffset.z*Math.sin(yaw),attachedOffset.y,attachedOffset.x*Math.sin(yaw)+attachedOffset.z*Math.cos(yaw)));
            setYRot(target.getYRot()+attachedYaw);setXRot(target.getXRot()+attachedPitch);
            if(++attachedAge>=200){strike(target,getDamage()/2,true);burst();}return;
        }
        entityData.set(AGE,++flightAge);
        if(flightAge>=100){burst();return;}
        var impact=legacyCollision(owner);
        if(impact!=null && !net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this,impact)) {
            if(LegacyProjectileGuard.destructible(owner,impact.getEntity())){LegacyProjectileGuard.destruct(owner,impact.getEntity(),(float)getDamage());burst();return;}
            onHitEntity(impact);if(attachedId!=null || isRemoved())return;
        }
        homeLegacy(owner);
        if(flightAge>10)setPos(position().add(getDeltaMovement()));
    }
    private EntityHitResult legacyCollision(Player owner) {
        var start=position();var end=start.add(getDeltaMovement());
        var block=level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        // The old base clips the entity ray only when the block below its hit point has a collision box.
        // SB ignores the block impact itself and keeps its noClip movement through terrain.
        if(block.getType()!=HitResult.Type.MISS) {
            var below=net.minecraft.core.BlockPos.containing(block.getLocation()).below();
            if(!level().getBlockState(below).getCollisionShape(level(),below).isEmpty())end=block.getLocation();
        }
        var candidates=level().getEntities(this,getBoundingBox().expandTowards(getDeltaMovement()).inflate(1));
        for(boolean destruct:new boolean[]{true,false}) {
            EntityHitResult result=null;double distance=Double.MAX_VALUE;
            for(var candidate:candidates) {
                var parent=candidate instanceof net.neoforged.neoforge.entity.PartEntity<?> part?part.getParent():candidate;
                if(destruct?!LegacyProjectileGuard.destructible(owner,candidate):!(parent instanceof LivingEntity living) || !eligible(living))continue;
                var box=candidate.getBoundingBox().inflate(.3);
                var hit=box.contains(end)?java.util.Optional.of(end):box.clip(end,start);
                if(hit.isEmpty() && box.contains(start))hit=java.util.Optional.of(start);
                if(hit.isPresent() && end.distanceToSqr(hit.get())<distance){distance=end.distanceToSqr(hit.get());result=new EntityHitResult(parent,hit.get());}
            }
            if(result!=null)return result;
        }
        return null;
    }
    private void homeLegacy(Player owner) {
        if(targetId==null) {
            var target=level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(15),e->eligible(e) && owner.hasLineOfSight(e) && distanceToSqr(e)<225)
                    .stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            if(target!=null)targetId=target.getUUID();return; // r87 uses the target ID captured before acquisition this tick.
        }
        if(flightAge<=10)return;
        var entity=((ServerLevel)level()).getEntity(targetId);
        if(!(entity instanceof LivingEntity target) || !eligible(target))return;
        var delta=target.getEyePosition().subtract(getEyePosition());
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),pitch=(float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance()));
        float dy=Mth.clamp(Mth.wrapDegrees(yaw-aimYaw),-10,10),dp=Mth.clamp(Mth.wrapDegrees(pitch-aimPitch),-10,10);
        aimYaw+=dy;aimPitch+=dp;
        double speed=(.75*(1-Math.min((Math.abs(dy)+Math.abs(dp))/10,.75))+getDeltaMovement().length()*9)/10;
        setDeltaMovement(Vec3.directionFromRotation(aimPitch,aimYaw).scale(speed));orientLegacy();
    }
    private void orientLegacy() {
        var vector=getDeltaMovement();if(vector.lengthSqr()<1e-12)return;
        setYRot((float)Math.toDegrees(Math.atan2(vector.x,vector.z)));setXRot((float)Math.toDegrees(Math.atan2(vector.y,vector.horizontalDistance())));
        yRotO=getYRot()-Mth.wrapDegrees(getYRot()-yRotO);xRotO=getXRot()-Mth.wrapDegrees(getXRot()-xRotO);
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
        if(result.getEntity() instanceof LivingEntity target && eligible(target) && (LegacyRangeAttack.enabled() || clearPath(target))) {
            if(strike(target,Math.max(1,getDamage()))) {
                attachedId=target.getUUID(); attachedAge=0; setHitEntity(target); setDelay(210);
                attachedOffset=position().subtract(target.position());attachedYaw=getYRot()-target.getYRot();attachedPitch=getXRot()-target.getXRot();
                entityData.set(SPIN,(level().getGameTime()%6+random.nextFloat())*60);
                setDeltaMovement(Vec3.ZERO);
            }
        }
    }
    @Override protected void onHitBlock(BlockHitResult hit) {
        if(LegacyRangeAttack.enabled())return;
        if(!level().isClientSide) { setPos(hit.getLocation().subtract(getDeltaMovement().normalize().scale(0.02))); burst(); }
    }
    private boolean strike(LivingEntity target,double damage) {
        return strike(target,damage,false);
    }
    private boolean strike(LivingEntity target,double damage,boolean breaking) {
        if(level().isClientSide || !eligible(target) || !LegacyRangeAttack.enabled() && !clearPath(target)) return false;
        var blade=sourceBlade();
        if(blade.isEmpty() || !(getOwner() instanceof LivingEntity owner)) return false;
        if(LegacyRangeAttack.enabled() && owner instanceof Player player)
            return LegacyProjectileDamage.strike(this,player,blade,target,damage,breaking,.1,false);
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
                net.minecraft.sounds.SoundSource.PLAYERS,LegacyRangeAttack.enabled()?.25f:.5f,LegacyRangeAttack.enabled()?1.6f:1.2f);
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
        tag.putDouble("LegacyHitX",attachedOffset.x);tag.putDouble("LegacyHitY",attachedOffset.y);tag.putDouble("LegacyHitZ",attachedOffset.z);tag.putFloat("LegacyHitYaw",attachedYaw);tag.putFloat("LegacyHitPitch",attachedPitch);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        sourceId=tag.hasUUID("LegacySource")?tag.getUUID("LegacySource"):null;
        ownerId=tag.hasUUID("LegacyOwner")?tag.getUUID("LegacyOwner"):null;
        targetId=tag.hasUUID("LegacyTarget")?tag.getUUID("LegacyTarget"):null;
        attachedId=tag.hasUUID("LegacyAttached")?tag.getUUID("LegacyAttached"):null;
        flightAge=Math.clamp(tag.getInt("LegacyFlightAge"),0,100);attachedAge=Math.clamp(tag.getInt("LegacyAttachedAge"),0,200);
        aimYaw=tag.getFloat("LegacyYaw");aimPitch=tag.getFloat("LegacyPitch");
        if(!Float.isFinite(aimYaw))aimYaw=0;
        aimPitch=Float.isFinite(aimPitch)?Mth.clamp(aimPitch,-90,90):0;
        tickCount=flightAge; setNoGravity(true);
        setRoll(tag.getFloat("LegacyRoll"));entityData.set(SPIN,tag.getFloat("LegacySpin"));entityData.set(AGE,flightAge);
        attachedOffset=new Vec3(finite(tag.getDouble("LegacyHitX")),finite(tag.getDouble("LegacyHitY")),finite(tag.getDouble("LegacyHitZ")));
        attachedYaw=(float)finite(tag.getFloat("LegacyHitYaw"));attachedPitch=(float)finite(tag.getFloat("LegacyHitPitch"));
        if(!Float.isFinite(getRoll()))setRoll(0);if(!Float.isFinite(frozenSpin()))entityData.set(SPIN,-1f);
    }
    private static double finite(double value){return Double.isFinite(value)?value:0;}
}
