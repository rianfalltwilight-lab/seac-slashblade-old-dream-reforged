package org.scex.slashbladelegacy;

import java.util.*;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.Projectile;
import mods.flammpfeil.slashblade.item.SwordType;
import mods.flammpfeil.slashblade.util.TargetSelector;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

/** Punch-enchanted Battou marker. The old mounted sword uses 200 ticks, despite its nominal 30-tick flight lifetime. */
public final class LegacyUpthrust extends EntityAbstractSummonedSword {
    private UUID targetId,sourceId,ownerId;
    private int age;
    private float offsetY,offsetYaw,offsetPitch;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> TARGET=net.minecraft.network.syncher.SynchedEntityData.defineId(
            LegacyUpthrust.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
    public LegacyUpthrust(EntityType<? extends Projectile> type,Level level){super(type,level);setNoGravity(true);}
    @Override public void setOwner(Entity owner){super.setOwner(owner);ownerId=owner==null?null:owner.getUUID();}
    @Override public Entity getOwner(){return level() instanceof ServerLevel server?ownerId==null?null:server.getEntity(ownerId):super.getOwner();}
    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(TARGET,-1);}
    public static void attach(Player user,LivingEntity target) {
        var blade=user.getMainHandItem();var state=BladeStateAccess.of(blade).orElse(null);
        if(state==null || user.level().isClientSide || !user.onGround() || state.isBroken() || !SwordType.from(blade).contains(SwordType.BEWITCHED)
                || blade.getEnchantmentLevel(user.registryAccess().holderOrThrow(Enchantments.PUNCH))<=0)return;
        var marker=new LegacyUpthrust(SummonedBladeMode.UPTHRUST.get(),user.level());marker.setOwner(user);
        marker.targetId=target.getUUID();marker.sourceId=LegacyDrive.identity(blade);marker.entityData.set(TARGET,target.getId());
        marker.setColor(LegacyRangeAttack.color(state));marker.setRoll(0);marker.setDamage(1);
        // r87 setDriveVector uses the constructor's shooter angles, replacing setLocationAndAngles' +30.
        var direction=user.getLookAngle();marker.offsetYaw=(float)Math.toDegrees(Math.atan2(direction.x,direction.z))-target.getYRot();
        marker.offsetPitch=(float)Math.toDegrees(Math.atan2(direction.y,direction.horizontalDistance()))-target.getXRot();marker.offsetY=target.getEyeHeight()/2;
        marker.follow(target);user.level().addFreshEntity(marker);
    }
    private LivingEntity target() {
        Entity entity=level() instanceof ServerLevel server && targetId!=null?server.getEntity(targetId):level().getEntity(entityData.get(TARGET));
        return entity instanceof LivingEntity living?living:null;
    }
    private ItemStack source(Player player) {
        return LegacyRangeAttack.sourceBlade(player,sourceId);
    }
    private void follow(LivingEntity target){yRotO=getYRot();xRotO=getXRot();setPos(target.getX(),target.getY()+offsetY,target.getZ());setYRot(target.getYRot()+offsetYaw);setXRot(target.getXRot()+offsetPitch);}
    @Override public void tick() {
        baseTick();age++;var target=target();
        if(!level().isClientSide) {
            if(!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) || target==null || !target.isAlive() || !(getOwner() instanceof Player player)
                    || !player.isAlive() || player.level()!=level() || !LegacyTargets.attackable(player,target) || source(player).isEmpty()){discard();return;}
            entityData.set(TARGET,target.getId());
            if(age>=200){detonate(player,source(player),1,false);return;}
        }
        if(target!=null)follow(target);
    }
    public static void blast(Player player,ItemStack blade) {
        if(player.level().isClientSide || !player.onGround())return;
        var tag=blade.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(!tag.hasUUID(SummonedBladeMode.SOURCE))return;
        UUID source=tag.getUUID(SummonedBladeMode.SOURCE);
        var markers=player.level().getEntitiesOfClass(LegacyUpthrust.class,player.getBoundingBox().inflate(20,5,20),
                marker->!marker.isRemoved() && marker.getOwner()==player && source.equals(marker.sourceId) && !marker.source(player).isEmpty());
        // Count only this player's matching markers; old list.size() also counted unrelated swords.
        int damage=markers.size();for(var marker:markers)marker.detonate(player,blade,damage,true);
    }
    private void detonate(Player player,ItemStack blade,int amount,boolean launch) {
        if(isRemoved())return;var target=target();
        if(blade.isEmpty() || target==null || !LegacyTargets.attackable(player,target)){discard();return;}
        boolean accepted=launch?LegacyProjectileDamage.absolute(this,player,blade,target,amount):LegacyProjectileDamage.strike(this,player,blade,target,1,true,.1,false);
        // Accept protection before the inherited shatter area; every collateral target has its own hurt event.
        if(accepted){burst();target.setDeltaMovement(0,launch?1:.1,0);target.hurtTime=1;target.hurtMarked=true;mods.flammpfeil.slashblade.ability.StunManager.setStun(target,20);}else discard();
    }
    @Override public void burst() {
        if(isRemoved() || level().isClientSide)return;
        if(getOwner() instanceof Player player)for(var target:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(1),e->LegacyTargets.attackable(player,e)))
            LegacyProjectileDamage.strike(this,player,source(player),target,1,false,.1,false);
        level().playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.GLASS_BREAK,net.minecraft.sounds.SoundSource.PLAYERS,.25f,1.6f);discard();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);if(targetId!=null)tag.putUUID("LegacyTarget",targetId);if(sourceId!=null)tag.putUUID("LegacySource",sourceId);if(ownerId!=null)tag.putUUID("LegacyOwner",ownerId);tag.putInt("LegacyAge",age);tag.putFloat("LegacyY",offsetY);tag.putFloat("LegacyYaw",offsetYaw);tag.putFloat("LegacyPitch",offsetPitch);}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);targetId=tag.hasUUID("LegacyTarget")?tag.getUUID("LegacyTarget"):null;sourceId=tag.hasUUID("LegacySource")?tag.getUUID("LegacySource"):null;ownerId=tag.hasUUID("LegacyOwner")?tag.getUUID("LegacyOwner"):null;age=Math.clamp(tag.getInt("LegacyAge"),0,200);offsetY=finite(tag.getFloat("LegacyY"));offsetYaw=finite(tag.getFloat("LegacyYaw"));offsetPitch=finite(tag.getFloat("LegacyPitch"));}
    private static float finite(float value){return Float.isFinite(value)?value:0;}
}
