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
    private UUID targetId,sourceId;
    private int age;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> TARGET=net.minecraft.network.syncher.SynchedEntityData.defineId(
            LegacyUpthrust.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
    public LegacyUpthrust(EntityType<? extends Projectile> type,Level level){super(type,level);setNoGravity(true);}
    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(TARGET,-1);}
    public static void attach(Player user,LivingEntity target) {
        var blade=user.getMainHandItem();var state=BladeStateAccess.of(blade).orElse(null);
        if(state==null || user.level().isClientSide || !user.onGround() || state.isBroken() || !SwordType.from(blade).contains(SwordType.BEWITCHED)
                || blade.getEnchantmentLevel(user.registryAccess().holderOrThrow(Enchantments.PUNCH))<=0)return;
        var marker=new LegacyUpthrust(SummonedBladeMode.UPTHRUST.get(),user.level());marker.setOwner(user);
        marker.targetId=target.getUUID();marker.sourceId=LegacyDrive.identity(blade);marker.entityData.set(TARGET,target.getId());
        marker.setColor(state.getColorCode());marker.follow(target);user.level().addFreshEntity(marker);
    }
    private LivingEntity target() {
        Entity entity=level() instanceof ServerLevel server && targetId!=null?server.getEntity(targetId):level().getEntity(entityData.get(TARGET));
        return entity instanceof LivingEntity living?living:null;
    }
    private ItemStack source(Player player) {
        if(sourceId==null)return ItemStack.EMPTY;
        ItemStack found=ItemStack.EMPTY;
        for(int i=0;i<player.getInventory().getContainerSize();i++) {
            var blade=player.getInventory().getItem(i);
            if(blade.isEmpty() || BladeStateAccess.of(blade).isEmpty())continue;
            var tag=blade.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
            if(sourceId!=null && tag.hasUUID(SummonedBladeMode.SOURCE) && sourceId.equals(tag.getUUID(SummonedBladeMode.SOURCE)) && BladeStateAccess.of(blade).isPresent()) {
                if(!found.isEmpty())return ItemStack.EMPTY;found=blade;
            }
        }
        return found;
    }
    private void follow(LivingEntity target){setPos(target.getX(),target.getY()+target.getEyeHeight()/2,target.getZ());setYRot(target.getYRot()+30);setXRot(target.getXRot()+30);}
    @Override public void tick() {
        baseTick();age++;var target=target();
        if(!level().isClientSide) {
            if(!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) || target==null || !target.isAlive() || !(getOwner() instanceof Player player)
                    || !player.isAlive() || player.level()!=level() || distanceToSqr(player)>4096 || source(player).isEmpty()){discard();return;}
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
        if(isRemoved())return;var target=target();discard();
        if(blade.isEmpty() || target==null || !target.isAlive() || !player.hasLineOfSight(target) || !TargetSelector.test.test(player,target))return;
        target.invulnerableTime=0;
        var damage=new net.minecraft.world.damagesource.DamageSource(level().registryAccess().holderOrThrow(
                net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC),player,player);
        if(target.hurt(damage,amount)) {
            LegacyCombat.projectileHit(blade,target,player);target.setDeltaMovement(0,launch?1:.1,0);target.hurtTime=1;target.hurtMarked=true;
            mods.flammpfeil.slashblade.ability.StunManager.setStun(target,20);
        }
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);if(targetId!=null)tag.putUUID("LegacyTarget",targetId);if(sourceId!=null)tag.putUUID("LegacySource",sourceId);tag.putInt("LegacyAge",age);}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);targetId=tag.hasUUID("LegacyTarget")?tag.getUUID("LegacyTarget"):null;sourceId=tag.hasUUID("LegacySource")?tag.getUUID("LegacySource"):null;age=Math.clamp(tag.getInt("LegacyAge"),0,200);}
}
