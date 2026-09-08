package org.scex.slashbladelegacy.contracts;

import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.scex.slashbladelegacy.*;

final class ProjectileTail17Contracts {
    static void run(ServerPlayer player,Map<String,Object> report) {
        var original=player.getMainHandItem();var position=player.position();var blade=new ItemStack(SlashBladeItems.SLASHBLADE.get());
        blade.set(DataComponents.CUSTOM_NAME,Component.literal("r87 projectile tail"));blade.enchant(player.registryAccess().holderOrThrow(Enchantments.PUNCH),1);blade.setDamageValue(1);
        player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.setPos(-8,160,-8);player.setYRot(0);player.setXRot(0);player.setOnGround(true);player.removeAllEffects();
        var state=BladeStateAccess.of(blade).orElseThrow();state.setProudSoulCount(1000);
        var target=EntityType.ALLAY.create(player.level());target.setNoAi(true);target.setNoGravity(true);target.setPos(-8,160,0);player.serverLevel().addFreshEntity(target);
        var shots=new ArrayList<Entity>();var wall=new BlockPos(-8,160,-4);var oldWall=player.level().getBlockState(wall);var oldBelow=player.level().getBlockState(wall.below());
        try {
            player.setPos(-8,160,80);var locked=sb(player,blade,target,shots);check(Math.abs(locked.getY()-player.getY())<=.5,"SB origin is old feet offset, no half-eye addition");
            var save=new CompoundTag();locked.save(save);check(save.hasUUID("LegacyTarget") && save.getUUID("LegacyTarget").equals(target.getUUID()),"locked target beyond fifteen remains locked");
            locked.setPos(-8,160,20);for(int i=0;i<11;i++)locked.tick();check(!locked.isRemoved() && Math.abs(locked.getDeltaMovement().x)>.05,"SB tracks locked distant target after ten-tick delay");locked.discard();
            var hit=sb(player,blade,target,shots);hit.setPos(-8,160.4,-.2);hit.setDeltaMovement(0,0,1);hit.tick();check(target.getHealth()==17 && hit.getHitEntity()==target,"SB first collision with owner beyond sixty-four");
            target.setPos(-6,161,-4);target.setYRot(90);hit.tick();check(hit.position().distanceTo(new Vec3(-5.8,161.4,-4))<.00001,"SB keeps rotated impact offset rather than moving to target center");
            save=new CompoundTag();hit.save(save);var restored=(LegacySummonedBlade)EntityType.loadEntityRecursive(save,player.level(),e->e);shots.add(restored);hit.discard();restored.tick();check(restored.getOwner()==player && restored.getHitEntity()==target && restored.position().distanceTo(new Vec3(-5.8,161.4,-4))<.00001,"SB saved attachment and owner roundtrip");restored.discard();
            player.setPos(-8,160,-8);target.setPos(-8,160,-2.5);target.setYRot(0);target.setHealth(20);target.invulnerableTime=0;
            player.level().setBlock(wall,Blocks.STONE.defaultBlockState(),3);player.level().setBlock(wall.below(),Blocks.STONE.defaultBlockState(),3);
            var through=sb(player,blade,target,shots);through.setPos(-8,160.4,-4.8);through.setDeltaMovement(0,0,1);
            for(int i=0;i<15 && through.getHitEntity()==null;i++)through.tick();check(through.getHitEntity()==target && target.getHealth()==17,"SB ignores block impact then hits through old noClip movement");through.discard();
            player.level().setBlock(wall,oldWall,3);player.level().setBlock(wall.below(),oldBelow,3);
            target.setHealth(20);target.setPos(-8,160,0);target.setDeltaMovement(Vec3.ZERO);target.invulnerableTime=0;
            LegacyUpthrust.attach(player,target);LegacyUpthrust.attach(player,target);check(markers(player).size()==2,"two attached Upthrust markers");int wear=blade.getDamageValue();LegacyUpthrust.blast(player,blade);
            check(target.getHealth()==14 && target.getDeltaMovement().y==1 && blade.getDamageValue()==wear+4 && markers(player).isEmpty(),"Upthrust two absolute count hits plus inherited shatter callbacks");
            target.setHealth(20);target.getAttribute(Attributes.ARMOR).setBaseValue(30);target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,100,4));
            LegacyUpthrust.attach(player,target);LegacyUpthrust.blast(player,blade);check(target.getHealth()==19,"absolute Upthrust bypasses armor and Resistance V while shatter remains resisted");target.removeAllEffects();target.getAttribute(Attributes.ARMOR).setBaseValue(0);
            target.setHealth(20);target.invulnerableTime=17;LegacyUpthrust.attach(player,target);wear=blade.getDamageValue();
            Consumer<LivingIncomingDamageEvent> cancel=e->{if(e.getEntity()==target)e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(cancel);try{LegacyUpthrust.blast(player,blade);}finally{NeoForge.EVENT_BUS.unregister(cancel);}
            check(target.getHealth()==20 && target.invulnerableTime==17 && blade.getDamageValue()==wear && markers(player).isEmpty(),"absolute protection preserves timer wear and health without premature shatter");
            LegacyUpthrust.attach(player,target);var marker=markers(player).getFirst();save=new CompoundTag();marker.save(save);marker.discard();var reloaded=(LegacyUpthrust)EntityType.loadEntityRecursive(save,player.level(),e->e);shots.add(reloaded);player.serverLevel().addFreshEntity(reloaded);
            player.setPos(-8,160,80);for(int i=0;i<199;i++)reloaded.tick();check(!reloaded.isRemoved() && reloaded.getOwner()==player && target.getHealth()==20,"Upthrust saved owner and no artificial range expiry");reloaded.tick();check(reloaded.isRemoved() && target.getHealth()==18,"Upthrust old two-hundred tick end hit plus shatter");
            player.setPos(-8,160,-8);target.setHealth(20);var fireball=EntityType.FIREBALL.create(player.level());fireball.setOwner(target);shots.add(fireball);fireball.setPos(-8,165,-8);
            boolean destroyed=LegacyProjectileGuard.destruct(player,fireball,1);
            check(destroyed && !fireball.isRemoved() && fireball.getOwner()==player,"hostile fireball receives its real deflection callback: accepted="+destroyed+", removed="+fireball.isRemoved()+", owner="+fireball.getOwner()+", player="+player+", target="+target+", eligible="+LegacyProjectileGuard.destructible(player,fireball));
            check(!LegacyProjectileGuard.destruct(player,fireball,1) && !fireball.isRemoved(),"reflected own projectile is protected");
            var arrow=EntityType.ARROW.create(player.level());arrow.setOwner(target);shots.add(arrow);check(LegacyProjectileGuard.destruct(player,arrow,1) && arrow.isRemoved(),"hostile arrow destroyed");
            target.setPos(-8,160,0);target.setHealth(2);target.invulnerableTime=0;
            var blister=new LegacyPhantomSword(SummonedBladeMode.SWORD.get(),player.level());shots.add(blister);
            blister.initialize(player,LegacyRangeAttack.sourceId(blade),LegacyRangeAttack.Art.BLISTERING,0,3,0xffffff,target,null,true);
            var released=new CompoundTag();blister.save(released);released.putBoolean("LegacyFired",true);blister.readAdditionalSaveData(released);blister.setPos(target.position().add(0,.25,0));blister.setDeltaMovement(Vec3.ZERO);
            int[] deaths={0};Consumer<net.neoforged.neoforge.event.entity.living.LivingDeathEvent> death=e->{if(e.getEntity()==target)deaths[0]++;};NeoForge.EVENT_BUS.addListener(death);try{blister.tick();}finally{NeoForge.EVENT_BUS.unregister(death);}
            check(target.getHealth()==0 && deaths[0]==1,"fiercer Blistering old cut then hit can kill, without restoring one HP");
            report.put("legacy17_projectile_tail",Map.of("sb_origin_lock_range",true,"sb_no_clip",true,"sb_rotated_attachment_save",true,"upthrust_absolute",true,"upthrust_protection",true,"upthrust_saved_owner_expiry",true,"real_fireball_deflection",true,"blistering_lethal_cut",true));
        } finally {shots.stream().filter(Objects::nonNull).forEach(Entity::discard);markers(player).forEach(Entity::discard);target.discard();player.level().setBlock(wall,oldWall,3);player.level().setBlock(wall.below(),oldBelow,3);player.setItemInHand(InteractionHand.MAIN_HAND,original);player.setPos(position);}
    }
    private static LegacySummonedBlade sb(ServerPlayer player,ItemStack blade,LivingEntity target,List<Entity> out){var shot=new LegacySummonedBlade(SummonedBladeMode.BLADE.get(),player.level());shot.initialize(player,3,0xffffff,LegacyRangeAttack.sourceId(blade),target);out.add(shot);return shot;}
    private static List<LegacyUpthrust> markers(ServerPlayer player){return player.level().getEntitiesOfClass(LegacyUpthrust.class,new AABB(-20,140,-20,20,180,100),e->e.getOwner()==player && !e.isRemoved());}
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError("r87 projectile tail: "+text);}
}
