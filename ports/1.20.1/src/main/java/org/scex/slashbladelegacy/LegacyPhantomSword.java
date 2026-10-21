package org.scex.slashbladelegacy;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.Projectile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.entity.PartEntity;
import static org.scex.slashbladelegacy.LegacyRangeAttack.Art;

/** Run r87 sword movement and collisions. Use Resharpened for packets and models. */
public final class LegacyPhantomSword extends EntityAbstractSummonedSword {
    private UUID ownerId,sourceId,targetId,holdId,attachedId;
    private Art art=Art.SINGLE;
    private int age,index,interval=7,lifetime=30,remainingHits=2,attachedAge;
    private boolean fired,ended,judgement,blocked,wither,witherBurst,airTrick;
    private boolean collisionDispatch;
    private Vec3 attachedOffset=Vec3.ZERO;
    private float aimYaw,aimPitch,attachedYaw,attachedPitch;
    private org.joml.Matrix4d spiralMatrix;
    private org.joml.Vector3d spiralVector;
    private final Set<UUID> hitTargets=new HashSet<>();
    public LegacyPhantomSword(EntityType<? extends Projectile> type,Level level) { super(type,level);setNoGravity(true); }
    @Override public void setOwner(Entity owner) { super.setOwner(owner);ownerId=owner==null?null:owner.getUUID(); }
    @Override public Entity getOwner() {
        if(level().isClientSide)return super.getOwner();
        return ownerId==null?null:((ServerLevel)level()).getEntity(ownerId);
    }
    public Art art() { return art; }
    public int age() { return age; }
    public boolean hasFired() { return fired; }
    public int interval() { return interval; }
    public int lifetime() { return lifetime; }
    public boolean wither(){return wither;}
    public boolean witherBurst(){return witherBurst;}
    public boolean airTrick(){return airTrick;}
    /** Return the entity that this sword is stuck in, or null. */
    public LivingEntity stuckTarget(){return ended || isRemoved() || level().isClientSide?null:living(attachedId);}
    public void initializeTerra(Player owner,UUID source,int number,double damage,LivingEntity target) {
        initialize(owner,source,Art.STORM,number,damage,0x35C611,target,null,false);
        interval=7+2*number;lifetime=30;
    }
    public void initializeAirTrick(Player owner,UUID source,int color,Entity target) {
        initialize(owner,source,Art.SINGLE,0,1,color,target,null,false);
        airTrick=true;interval=0;setPos(owner.getEyePosition());drive(1.75);
    }
    public void initializeWither(Player owner,UUID source,int number,double damage,LivingEntity target) {
        initialize(owner,source,Art.SINGLE,number,damage,number%2==0?-0x6896cc:-0x1c1c1c,target,null,false);
        wither=true;witherBurst=number%2==0;interval=7+2*number;
    }
    public void initialize(Player owner,UUID source,Art mode,int number,double damage,int color,Entity target,UUID hold,boolean fierce) {
        setOwner(owner);sourceId=source;art=mode;index=number;holdId=hold;judgement=fierce;
        targetId=target==null?null:target.getUUID();setDamage(damage);setColor(color);
        setRoll(mode==Art.SPIRAL || mode==Art.STORM?0:mode==Art.HEAVY_RAIN?owner.getRandom().nextFloat()*360:90);
        aimYaw=owner.getYRot();aimPitch=owner.getXRot();
        interval=switch(mode) {case SINGLE->7;case SPIRAL->200;case STORM->40;case BLISTERING->number;case HEAVY_RAIN->10+number;};
        lifetime=switch(mode) {case SINGLE,BLISTERING->30;case SPIRAL->200;case STORM->70;case HEAVY_RAIN->30+number;};
        fired=mode==Art.SINGLE || mode==Art.HEAVY_RAIN;
        if(mode==Art.SINGLE) {
            double r=(owner.getRandom().nextFloat()-.5)*2,yaw=Math.toRadians(-owner.getYRot()+90);
            setPos(owner.getX()+r*Math.sin(yaw)*2,owner.getY()+owner.getEyeHeight()*.5+(1-Math.abs(r))*2,owner.getZ()+r*Math.cos(yaw)*2);
            drive(1.75);
        }else if(mode==Art.HEAVY_RAIN) {
            Vec3 center=target==null?owner.position().add(owner.getLookAngle().multiply(8,0,8)):target.position();
            double area=number>0?1.5:.1;
            setPos(center.add((random.nextGaussian()-.5)*area,8,(random.nextGaussian()-.5)*area));
            aimYaw=random.nextFloat()*360;aimPitch=(float)(90+(random.nextGaussian()-.5)*8);drive(.8);
        }else standby(owner);
        yRotO=getYRot();xRotO=getXRot();
    }
    private LivingEntity living(UUID id) { return id!=null && ((ServerLevel)level()).getEntity(id) instanceof LivingEntity entity?entity:null; }
    private boolean eligible(Player owner,Entity target) { return LegacyTargets.attackable(owner,target); }
    @Override public void tick() {
        if(ended || isRemoved())return;
        // Run base entity updates only. Server packets position the client model.
        baseTick();
        if(level().isClientSide)return;
        if(!(getOwner() instanceof Player owner) || !owner.isAlive() || LegacyRangeAttack.sourceBlade(owner,sourceId).isEmpty()) { discard();return; }
        yRotO=getYRot();xRotO=getXRot();age++;
        if(attachedId!=null) {
            var target=living(attachedId);
            if(target==null || !eligible(owner,target)) { burst();return; }
            double yaw=Math.toRadians(target.getYRot());
            setPos(target.position().add(attachedOffset.x*Math.cos(yaw)-attachedOffset.z*Math.sin(yaw),attachedOffset.y,
                    attachedOffset.x*Math.sin(yaw)+attachedOffset.z*Math.cos(yaw)));
            setYRot(target.getYRot()+attachedYaw);setXRot(target.getXRot()+attachedPitch);
            if(++attachedAge>=200) { strike(owner,target,getDamage()/2,true,.1);burst(); }
            return;
        }
        if(!fired) {
            if(art==Art.SPIRAL && age>lifetime) { discard();return; }
            if(art==Art.BLISTERING && level().getGameTime()>owner.getPersistentData().getLong(LegacyRangeAttack.BLISTERING_UNTIL)+index) {
                fired=true;age=0;wing();return;
            }
            var target=art==Art.STORM?living(targetId):owner;
            if(art==Art.STORM && (target==null || !target.isAlive())) {
                fired=true;interval=age+7;lifetime=age+30;wing();return;
            }
            standby(owner);
            if(art==Art.BLISTERING) { aim(owner,10,true);return; }
            if(art==Art.SPIRAL && (!owner.getPersistentData().hasUUID(LegacyRangeAttack.SPIRAL)
                    || !holdId.equals(owner.getPersistentData().getUUID(LegacyRangeAttack.SPIRAL)))
                    || age>interval) {
                fired=true;
                if(art==Art.SPIRAL){interval=age+7;lifetime=age+30;}
                wing();return;
            }
            collide(owner,art==Art.SPIRAL,false);return;
        }
        if(age>=lifetime) { burst();return; }
        if(blocked)return;
        if(airTrick) {
            // Sweep the exact segment we will travel, including this tick's homing and acceleration.
            setDeltaMovement(getDeltaMovement().scale(isInWater()?1:1.10f));aim(owner,90,false);orient();
            if(!collide(owner,true,true))setPos(position().add(getDeltaMovement()));
            return;
        }
        boolean canHit=art!=Art.HEAVY_RAIN || age>interval;
        if(collide(owner,canHit,art!=Art.BLISTERING || age>=10))return;
        setDeltaMovement(getDeltaMovement().scale(isInWater()?1:1.10f));
        if(art==Art.SINGLE && age<=interval)aim(owner,age,true);
        else if(art==Art.BLISTERING)aim(owner,10,false);
        orient();
        if(age>interval)setPos(position().add(getDeltaMovement()));
    }
    private void standby(Player owner) {
        if(art==Art.BLISTERING) {
            int side=index%2==0?1:-1,row=index/2;
            double yaw=Math.toRadians(-owner.getYRot()+90*side),width=.8+.15*row;
            setPos(owner.position().add(Math.sin(yaw)*width,owner.getEyeHeight()+(1-row)*.25,Math.cos(yaw)*width).subtract(owner.getLookAngle()));
            drive(1.75);return;
        }
        int ticks=Math.min(age,interval-7);
        if(art==Art.SPIRAL) {
            // Reuse the held spiral's scratch objects. Keep the original rotation order.
            if(spiralMatrix==null){spiralMatrix=new org.joml.Matrix4d();spiralVector=new org.joml.Vector3d();}
            spiralMatrix.identity().rotateY(Math.toRadians(-owner.getYRot()))
                    .rotateY(-Math.toRadians(ticks*5%360)).rotateZ(Math.toRadians(7.5)).rotateY(Math.toRadians(ticks*12+index*60));
            var vector=spiralMatrix.transformDirection(spiralVector.set(0,0,1)).normalize();
            setPos(owner.position().add(vector.x*1.5,owner.getBbHeight()/2+vector.y*1.5,vector.z*1.5));
            setDeltaMovement(vector.x,vector.y,vector.z);
        }else {
            var target=living(targetId);if(target==null)return;
            double yaw=Math.toRadians(ticks*9+index*60);
            setPos(target.position().add(Math.sin(yaw)*2.5,target.getBbHeight()/2+Math.sin(yaw+ticks/10.0)*.25,Math.cos(yaw)*2.5));
            setDeltaMovement(-Math.sin(yaw),0,-Math.cos(yaw));
        }
        orient();
    }
    private void drive(double speed) { setDeltaMovement(Vec3.directionFromRotation(aimPitch,aimYaw).scale(speed));orient(); }
    private void orient() {
        var motion=getDeltaMovement();if(motion.lengthSqr()<1e-12)return;
        setYRot((float)Math.toDegrees(Math.atan2(motion.x,motion.z)));
        setXRot((float)Math.toDegrees(Math.atan2(motion.y,Math.sqrt(motion.horizontalDistanceSqr()))));
        yRotO=getYRot()-Mth.wrapDegrees(getYRot()-yRotO);xRotO=getXRot()-Mth.wrapDegrees(getXRot()-xRotO);
    }
    private void aim(Player owner,float step,boolean allowOwnerLook) {
        var target=living(targetId);
        if(target!=null && !eligible(owner,target))target=null;
        if(target==null && art==Art.SINGLE && !airTrick) {
            target=findTarget(owner);
            targetId=target==null?null:target.getUUID();
        }
        Vec3 vector=target!=null?target.getEyePosition().subtract(position()):allowOwnerLook && art==Art.BLISTERING?owner.getLookAngle():null;
        if(vector==null)return;
        float yaw=(float)Math.toDegrees(Math.atan2(-vector.x,vector.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(vector.y,Math.sqrt(vector.horizontalDistanceSqr())));
        double speed=airTrick?getDeltaMovement().length():1.75;
        aimYaw+=Mth.clamp(Mth.wrapDegrees(yaw-aimYaw),-step,step);aimPitch+=Mth.clamp(Mth.wrapDegrees(pitch-aimPitch),-step,step);drive(speed);
    }
    private LivingEntity findTarget(Player owner) {
        var start=owner.getEyePosition();var end=start.add(owner.getLookAngle().scale(30));
        var block=level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        double limit=start.distanceToSqr(block.getLocation());
        var target=findTarget(owner,start,end,limit,0);
        return target!=null?target:findTarget(owner,start,end,limit,5);
    }
    private LivingEntity findTarget(Player owner,Vec3 start,Vec3 end,double nearest,double width) {
        LivingEntity result=null;
        for(var candidate:level().getEntitiesOfClass(LivingEntity.class,new AABB(start,end).inflate(width+1))) {
            var bounds=candidate.getBoundingBox().inflate(.1+width);var hit=bounds.contains(start)?java.util.Optional.of(start):bounds.clip(start,end);
            if(hit.isEmpty())continue;
            double distance=start.distanceToSqr(hit.get());
            // Test the ray first. The line-of-sight test casts another ray, so run it last.
            if(distance<nearest && eligible(owner,candidate) && owner.hasLineOfSight(candidate)) {nearest=distance;result=candidate;}
        }
        return result;
    }
    private boolean destructible(Player owner,Entity entity) {
        return entity!=this && LegacyProjectileGuard.destructible(owner,entity);
    }
    private boolean collide(Player owner,boolean attack,boolean blocks) {
        var start=position();var end=start.add(getDeltaMovement());
        HitResult block=blocks?level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)):null;
        if(block!=null && block.getType()==HitResult.Type.MISS)block=null;
        // r87 marker, Blistering, Storm and Spiral swords stop at a block only after their box enters it.
        // The base sword and Heavy Rain stop at the ray hit.
        if(block!=null && (airTrick || art==Art.BLISTERING || art==Art.STORM || art==Art.SPIRAL)
                && !level().getBlockCollisions(this,getBoundingBox()).iterator().hasNext())block=null;
        var found=entityHit(owner,start,end,attack);
        if(found!=null && !net.minecraftforge.event.ForgeEventFactory.onProjectileImpact(this,found)) {
            var entity=found.getEntity();
            if(destructible(owner,entity)) {
                LegacyProjectileGuard.destruct(owner,entity,(float)getDamage());
                burst();return true;
            }
            var parent=entity instanceof PartEntity<?> part?part.getParent():entity;
            if(parent instanceof LivingEntity target) {
                collisionDispatch=true;
                try{onHitEntity(new EntityHitResult(target,found.getLocation()));}finally{collisionDispatch=false;}
            }
            if(attachedId!=null || isRemoved())return true;
        }
        // A rejected or continuing entity hit does not let the sword pass this step's block.
        if(block==null || net.minecraftforge.event.ForgeEventFactory.onProjectileImpact(this,block))return false;
        if(airTrick)burst();
        else if(art==Art.SINGLE) { blocked=true;setPos(block.getLocation());setDeltaMovement(Vec3.ZERO); }
        else {setPos(block.getLocation());burst();}
        return true;
    }
    /** r87 getMovingObjectPosition: destructible entities first, then attackable entities.
     * A found entity replaces the block hit at any distance in this step. */
    private EntityHitResult entityHit(Player owner,Vec3 start,Vec3 end,boolean attack) {
        var candidates=level().getEntities(this,getBoundingBox().expandTowards(getDeltaMovement()).inflate(1),
                e->destructible(owner,e) || attack && eligible(owner,e) && (art!=Art.STORM || !hitTargets.contains(e.getUUID())));
        for(boolean destruct:new boolean[]{true,false}) {
            EntityHitResult result=null;double nearest=Double.MAX_VALUE;
            for(var candidate:candidates) {
                if(destructible(owner,candidate)!=destruct)continue;
                if(!destruct && (art==Art.BLISTERING || airTrick) && targetId!=null && !targetId.equals(candidate.getUUID()))continue;
                var bounds=candidate.getBoundingBox().inflate(.3);var hit=bounds.contains(start)?java.util.Optional.of(start):bounds.clip(start,end);
                if(hit.isPresent() && start.distanceToSqr(hit.get())<nearest) {nearest=start.distanceToSqr(hit.get());result=new EntityHitResult(candidate,hit.get());}
            }
            if(result!=null)return result;
        }
        return null;
    }
    /** Apply the collision rules to external force-hit calls. */
    @Override protected void onHitEntity(EntityHitResult result) {
        if(level().isClientSide || ended || isRemoved() || attachedId!=null || LegacySwordImpact.notifying(this)
                || airTrick && !collisionDispatch)return;
        var entity=result.getEntity() instanceof PartEntity<?> part?part.getParent():result.getEntity();
        if(!(getOwner() instanceof Player owner) || !owner.isAlive() || LegacyRangeAttack.sourceBlade(owner,sourceId).isEmpty()
                || !(entity instanceof LivingEntity target) || !eligible(owner,target)
                || art==Art.STORM && hitTargets.contains(target.getUUID())
                || airTrick && targetId!=null && !targetId.equals(target.getUUID()))return;
        hit(owner,target,result.getLocation());
    }
    private void hit(Player owner,LivingEntity target,Vec3 impact) {
        if(wither) {
            if(!strike(owner,target,getDamage(),false,.1))return;
            // Start the radius-one explosion only after the target accepts damage.
            if(witherBurst)level().explode(this,getX(),getY(),getZ(),1,false,Level.ExplosionInteraction.NONE);
            else target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER,100,1));
            if(!target.isAlive())owner.heal(1);
            burst();LegacySwordImpact.post(this,target);return;
        }
        if(art==Art.SPIRAL && age%3!=0)return;
        double damage=getDamage();
        if(art==Art.BLISTERING && judgement)damage/=2;
        if(!LegacyProjectileDamage.strike(this,owner,LegacyRangeAttack.sourceBlade(owner,sourceId),target,damage,false,
                art==Art.SPIRAL?0:art==Art.STORM?.8:.1,art==Art.HEAVY_RAIN,art==Art.BLISTERING && judgement?damage:0))return;
        if(art==Art.BLISTERING && judgement) {
            setDamage(damage);
        }
        if(art==Art.SPIRAL || art==Art.STORM) {
            if(art==Art.STORM)hitTargets.add(target.getUUID());
            if(--remainingHits<=0)burst();LegacySwordImpact.post(this,target);return;
        }
        if(airTrick)setPos(impact);
        attachedId=target.getUUID();attachedAge=art==Art.HEAVY_RAIN?200-(lifetime-age):0;
        attachedOffset=position().subtract(target.position());attachedYaw=getYRot()-target.getYRot();attachedPitch=getXRot()-target.getXRot();
        setDeltaMovement(Vec3.ZERO);
        // r87 records every sword that sticks in an entity as the next AirTrick destination.
        LegacyAirTrick.stuck(owner,this);
        if(airTrick && owner instanceof net.minecraft.server.level.ServerPlayer player) {
            airTrick=false;
            LegacyAirTrick.hit(player,LegacyRangeAttack.sourceBlade(owner,sourceId),target,this);
        }
        // Notify addons after damage and attachment. Reject repeated force-hit calls above.
        LegacySwordImpact.post(this,target);
    }
    private boolean strike(Player owner,LivingEntity target,double amount,boolean breaking,double lift) {
        return LegacyProjectileDamage.strike(this,owner,LegacyRangeAttack.sourceBlade(owner,sourceId),target,amount,breaking,lift,art==Art.HEAVY_RAIN);
    }
    private void wing() { level().playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP,net.minecraft.sounds.SoundSource.PLAYERS,.35f,.2f); }
    @Override public void burst() {
        if(ended || isRemoved() || level().isClientSide)return;
        ended=true;
        if(!airTrick && getOwner() instanceof Player owner) {
            for(var target:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(1),e->eligible(owner,e) && !hitTargets.contains(e.getUUID())))strike(owner,target,1,false,.1);
        }
        level().playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.GLASS_BREAK,net.minecraft.sounds.SoundSource.PLAYERS,.25f,1.6f);
        ((ServerLevel)level()).sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,getX(),getY(),getZ(),8,.2,.2,.2,.05);discard();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if(ownerId!=null)tag.putUUID("LegacyOwner",ownerId);if(sourceId!=null)tag.putUUID("LegacySource",sourceId);
        if(targetId!=null)tag.putUUID("LegacyTarget",targetId);if(holdId!=null)tag.putUUID("LegacyHold",holdId);if(attachedId!=null)tag.putUUID("LegacyAttached",attachedId);
        tag.putString("LegacyArt",art.name());tag.putInt("LegacyAge",age);tag.putInt("LegacyIndex",index);tag.putInt("LegacyInterval",interval);tag.putInt("LegacyLifetime",lifetime);
        tag.putInt("LegacyHits",remainingHits);tag.putInt("LegacyAttachedAge",attachedAge);tag.putBoolean("LegacyFired",fired);tag.putBoolean("LegacyJudgement",judgement);tag.putBoolean("LegacyBlocked",blocked);
        tag.putBoolean("LegacyWither",wither);tag.putBoolean("LegacyWitherBurst",witherBurst);
        tag.putBoolean("LegacyAirTrick",airTrick);
        tag.putFloat("LegacyAimYaw",aimYaw);tag.putFloat("LegacyAimPitch",aimPitch);tag.putFloat("LegacyRoll",getRoll());
        tag.putDouble("LegacyHitX",attachedOffset.x);tag.putDouble("LegacyHitY",attachedOffset.y);tag.putDouble("LegacyHitZ",attachedOffset.z);
        tag.putFloat("LegacyHitYaw",attachedYaw);tag.putFloat("LegacyHitPitch",attachedPitch);
        var hits=new net.minecraft.nbt.ListTag();for(var id:hitTargets)hits.add(net.minecraft.nbt.NbtUtils.createUUID(id));tag.put("LegacyHitTargets",hits);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerId=tag.hasUUID("LegacyOwner")?tag.getUUID("LegacyOwner"):null;sourceId=tag.hasUUID("LegacySource")?tag.getUUID("LegacySource"):null;
        targetId=tag.hasUUID("LegacyTarget")?tag.getUUID("LegacyTarget"):null;holdId=tag.hasUUID("LegacyHold")?tag.getUUID("LegacyHold"):null;attachedId=tag.hasUUID("LegacyAttached")?tag.getUUID("LegacyAttached"):null;
        try {art=Art.valueOf(tag.getString("LegacyArt"));}catch(IllegalArgumentException invalid){art=Art.SINGLE;}
        age=net.minecraft.util.Mth.clamp(tag.getInt("LegacyAge"),0,600);index=net.minecraft.util.Mth.clamp(tag.getInt("LegacyIndex"),0,9);interval=net.minecraft.util.Mth.clamp(tag.getInt("LegacyInterval"),0,600);lifetime=net.minecraft.util.Mth.clamp(tag.getInt("LegacyLifetime"),1,600);
        remainingHits=net.minecraft.util.Mth.clamp(tag.getInt("LegacyHits"),0,2);attachedAge=net.minecraft.util.Mth.clamp(tag.getInt("LegacyAttachedAge"),0,200);
        fired=tag.getBoolean("LegacyFired");judgement=tag.getBoolean("LegacyJudgement");blocked=tag.getBoolean("LegacyBlocked");
        wither=tag.getBoolean("LegacyWither");witherBurst=tag.getBoolean("LegacyWitherBurst");
        airTrick=tag.getBoolean("LegacyAirTrick");
        aimYaw=finite(tag.getFloat("LegacyAimYaw"));aimPitch=finite(tag.getFloat("LegacyAimPitch"));setRoll(finite(tag.getFloat("LegacyRoll")));
        attachedOffset=new Vec3(finite(tag.getDouble("LegacyHitX")),finite(tag.getDouble("LegacyHitY")),finite(tag.getDouble("LegacyHitZ")));
        attachedYaw=finite(tag.getFloat("LegacyHitYaw"));attachedPitch=finite(tag.getFloat("LegacyHitPitch"));
        hitTargets.clear();for(var hit:tag.getList("LegacyHitTargets",net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            if(hitTargets.size()>=2)break;
            if(hit instanceof net.minecraft.nbt.IntArrayTag array && array.getAsIntArray().length==4)hitTargets.add(net.minecraft.nbt.NbtUtils.loadUUID(hit));
        }
        if(art==Art.SPIRAL && holdId==null)fired=true;
        setNoGravity(true);
    }
    private static float finite(float value) {return Float.isFinite(value)?value:0;}
    private static double finite(double value) {return Double.isFinite(value)?value:0;}
}
