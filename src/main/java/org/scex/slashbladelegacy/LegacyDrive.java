package org.scex.slashbladelegacy;

import java.util.*;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.entity.Projectile;
import mods.flammpfeil.slashblade.util.TargetSelector;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;

/** Original 1.12.2 Drive timing and fixed magic damage, with the existing Resharpened visual. */
public final class LegacyDrive extends EntityDrive {
    private UUID source;
    private boolean multi;
    private int age;
    private final Set<UUID> hit=new HashSet<>();
    public LegacyDrive(EntityType<? extends Projectile> type,Level level){super(type,level);}
    public void initialize(Player player,ItemStack blade,float damage,float speed,float roll,boolean multiHit) {
        setOwner(player);source=identity(blade);multi=multiHit;setDamage(Math.max(1,damage));
        setSpeed(speed);setLifetime(20);setRotationRoll(roll);
        setColor(BladeStateAccess.of(blade).orElseThrow().getColorCode());
        Vec3 look=player.getLookAngle();setPos(player.position().add(0,player.getEyeHeight()/2,0).add(look));
        setYRot(player.getYRot());setXRot(player.getXRot());setDeltaMovement(look.scale(speed));
    }
    public static UUID identity(ItemStack blade) {
        var tag=blade.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(!tag.hasUUID(SummonedBladeMode.SOURCE)) {
            tag.putUUID(SummonedBladeMode.SOURCE,UUID.randomUUID());blade.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        }
        return tag.getUUID(SummonedBladeMode.SOURCE);
    }
    private ItemStack blade(Player player) {
        if(source==null)return ItemStack.EMPTY;
        ItemStack found=ItemStack.EMPTY;
        for(int i=0;i<player.getInventory().getContainerSize();i++) {
            var item=player.getInventory().getItem(i);
            if(item.isEmpty() || BladeStateAccess.of(item).isEmpty())continue;
            var data=item.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
            if(source!=null && data.hasUUID(SummonedBladeMode.SOURCE) && source.equals(data.getUUID(SummonedBladeMode.SOURCE)) && BladeStateAccess.of(item).isPresent()) {
                if(!found.isEmpty())return ItemStack.EMPTY;
                found=item;
            }
        }
        return found;
    }
    @Override public void tick() {
        // Do not call EntityDrive.tick: its inherited projectile path multiplies damage by current attack attributes.
        baseTick();age++;
        if(!level().isClientSide) {
            if(!LegacyCompat.LEGACY_COMBAT.get() || !(getOwner() instanceof Player player) || !player.isAlive()
                    || player.level()!=level() || distanceToSqr(player)>4096){discard();return;}
            ItemStack blade=blade(player);if(blade.isEmpty()){discard();return;}
            if(!level().noCollision(this,getBoundingBox())){discard();return;}
            LegacyProjectileGuard.intercept(player,blade,new AABB(position(),position()).inflate(1.5),false,false);
            if(!multi || age%2==0) {
                for(var target:level().getEntitiesOfClass(LivingEntity.class,new AABB(position(),position()).inflate(1.5))) {
                    if(blade.isEmpty())break;
                    if(target==player || hit.contains(target.getUUID()) || !TargetSelector.test.test(player,target)
                            || !player.hasLineOfSight(target) || !clearPath(position(),target.getBoundingBox().getCenter()))continue;
                    if(!multi)hit.add(target.getUUID());
                    target.invulnerableTime=0;
                    // The old directMagic source attributes both attacker and direct entity to the player.
                    var damage=new net.minecraft.world.damagesource.DamageSource(level().registryAccess().holderOrThrow(
                            net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC),player,player);
                    if(target.hurt(damage,(float)getDamage()))LegacyCombat.projectileHit(blade,target,player);
                }
            }
        }
        Vec3 velocity=getDeltaMovement().scale(1.05);Vec3 next=position().add(velocity);
        if(!clearPath(position(),next)){discard();return;}
        setDeltaMovement(velocity);setPos(next);
        if(age>=20)discard();
    }
    private boolean clearPath(Vec3 from,Vec3 to) {
        return level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);if(source!=null)tag.putUUID("LegacySource",source);
        tag.putBoolean("LegacyMulti",multi);tag.putInt("LegacyAge",age);
        var hits=new net.minecraft.nbt.ListTag();for(var id:hit)hits.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));tag.put("LegacyHits",hits);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);source=tag.hasUUID("LegacySource")?tag.getUUID("LegacySource"):null;
        multi=tag.getBoolean("LegacyMulti");age=Math.clamp(tag.getInt("LegacyAge"),0,20);hit.clear();
        var hits=tag.getList("LegacyHits",net.minecraft.nbt.Tag.TAG_STRING);
        for(int i=0;i<hits.size();i++)try{hit.add(UUID.fromString(hits.getString(i)));}catch(IllegalArgumentException ignored){}
    }
}
