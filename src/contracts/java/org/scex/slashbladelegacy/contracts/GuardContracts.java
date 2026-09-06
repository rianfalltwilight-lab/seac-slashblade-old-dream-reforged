package org.scex.slashbladelegacy.contracts;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import org.scex.slashbladelegacy.*;

final class GuardContracts {
    static void run(ServerPlayer player,Map<String,Object> report) {
        var level=player.serverLevel();var saved=player.getMainHandItem();var blade=saved.copy();
        blade.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        blade.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS,net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        var state=BladeStateAccess.of(blade).orElseThrow();state.setBroken(false);state.setSealed(false);state.setDefaultBewitched(true);
        state.setComboRoot(ComboStateRegistry.STANDBY.getId());state.setComboSeq(LegacyCombat.id(LegacyMove.SAYA1));state.setLastActionTime(level.getGameTime());
        player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.setPos(24,160,0);player.setYRot(0);player.setXRot(0);player.setOnGround(true);
        level.getChunk(1,0);var enemy=EntityType.SKELETON.create(level);enemy.setPos(24,160,4);level.addFreshEntity(enemy);
        var target=EntityType.ZOMBIE.create(level);target.setPos(24,160,2);level.addFreshEntity(target);
        var entities=new ArrayList<Entity>();
        try {
            var arrow=EntityType.ARROW.create(level);arrow.setPos(24,161,2);arrow.setOwner(enemy);level.addFreshEntity(arrow);entities.add(arrow);
            var own=EntityType.ARROW.create(level);own.setPos(24,161,2);own.setOwner(player);level.addFreshEntity(own);entities.add(own);
            var other=EntityType.SNOWBALL.create(level);other.setPos(24,161,2);other.setOwner(enemy);level.addFreshEntity(other);entities.add(other);
            int wear=blade.getDamageValue();
            blade.getItem().use(level,player,InteractionHand.MAIN_HAND);
            require(arrow.isRemoved() && other.isRemoved() && !own.isRemoved(),"Normal blade destroy / own projectile protection: arrow="+arrow.isRemoved()+", snowball="+other.isRemoved()+", own="+own.isRemoved()+", visible="+player.hasLineOfSight(arrow)+", reach="+mods.flammpfeil.slashblade.util.TargetSelector.getResolvedReach(player)+", types="+mods.flammpfeil.slashblade.item.SwordType.from(blade)+", candidates="+level.getEntitiesOfClass(Projectile.class,player.getBoundingBox().inflate(4)).size());
            require(blade.getDamageValue()==wear+1,"Destruction batch must consume one durability");
            blade.enchant(level.registryAccess().holderOrThrow(Enchantments.POWER),1);
            var guided=EntityType.ARROW.create(level);guided.setPos(24,161,2);guided.setOwner(enemy);level.addFreshEntity(guided);entities.add(guided);
            LegacyProjectileGuard.intercept(player,blade,player.getBoundingBox().inflate(4),true,true);
            require(!guided.isRemoved() && guided.getOwner()==player && guided.isCritArrow() && Math.abs(guided.getDeltaMovement().z-1.5)<.0001,"Legacy induction velocity/owner: removed="+guided.isRemoved()+", owner="+guided.getOwner()+", velocity="+guided.getDeltaMovement()+", types="+mods.flammpfeil.slashblade.item.SwordType.from(blade));
            blade.enchant(level.registryAccess().holderOrThrow(Enchantments.THORNS),1);
            var reflected=EntityType.ARROW.create(level);reflected.setPos(24,161,2);enemy.setPos(24,160,-3);reflected.setOwner(enemy);level.addFreshEntity(reflected);entities.add(reflected);
            LegacyProjectileGuard.intercept(player,blade,player.getBoundingBox().inflate(4),true,true);
            require(reflected.getOwner()==player && reflected.getDeltaMovement().z<0 && Math.abs(reflected.getDeltaMovement().length()-1.5)<.0001,"Thorns returns to shooter");
            var friendly=EntityType.ARROW.create(level);friendly.setOwner(player);friendly.setPos(24,161,2);level.addFreshEntity(friendly);entities.add(friendly);
            var team=player.getScoreboard().addPlayerTeam("legacy_guard");player.getScoreboard().addPlayerToTeam(enemy.getScoreboardName(),team);player.getScoreboard().addPlayerToTeam(player.getScoreboardName(),team);
            var allied=EntityType.ARROW.create(level);allied.setOwner(enemy);allied.setPos(24,161,2);level.addFreshEntity(allied);entities.add(allied);
            LegacyProjectileGuard.intercept(player,blade,player.getBoundingBox().inflate(4),true,true);
            require(allied.getOwner()==enemy,"Allied projectile stolen");player.getScoreboard().removePlayerTeam(team);
            var blocked=EntityType.ARROW.create(level);blocked.setPos(24,161,2);blocked.setOwner(enemy);level.addFreshEntity(blocked);entities.add(blocked);
            var wall=new net.minecraft.core.BlockPos(24,161,1);var oldBlock=level.getBlockState(wall);
            try{level.setBlockAndUpdate(wall,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                LegacyProjectileGuard.intercept(player,blade,player.getBoundingBox().inflate(4),true,true);
                require(blocked.getOwner()==enemy,"Projectile reflected through wall");
            }finally{level.setBlockAndUpdate(wall,oldBlock);}
            blocked.discard();
            // Real hit callback arms markers, and Noutou executes the detonation callback.
            blade.enchant(level.registryAccess().holderOrThrow(Enchantments.PUNCH),1);
            state.setComboSeq(LegacyCombat.id(LegacyMove.BATTOU));state.setLastActionTime(level.getGameTime());
            ComboStateRegistry.REGISTRY.get(LegacyCombat.id(LegacyMove.BATTOU)).hitEffect(target,player);
            ComboStateRegistry.REGISTRY.get(LegacyCombat.id(LegacyMove.BATTOU)).hitEffect(target,player);
            var markers=level.getEntitiesOfClass(LegacyUpthrust.class,player.getBoundingBox().inflate(8));
            require(markers.size()==2,"Punch Battou did not arm two markers");
            var savedMarker=new net.minecraft.nbt.CompoundTag();markers.get(0).addAdditionalSaveData(savedMarker);
            markers.get(0).discard();var restored=new LegacyUpthrust(SummonedBladeMode.UPTHRUST.get(),level);restored.readAdditionalSaveData(savedMarker);restored.setOwner(player);restored.setPos(markers.get(0).position());level.addFreshEntity(restored);restored.tick();
            float health=target.getHealth();state.updateComboSeq(player,LegacyCombat.id(LegacyMove.NOUTOU));
            require(Math.abs(target.getHealth()-(health-4))<.001 && target.getDeltaMovement().y==1,"Sheathing blast count/launch: before="+health+", after="+target.getHealth()+", velocity="+target.getDeltaMovement()+", markers="+level.getEntitiesOfClass(LegacyUpthrust.class,player.getBoundingBox().inflate(8)).size()+", ground="+player.onGround());
            LegacyUpthrust.blast(player,blade);require(Math.abs(target.getHealth()-(health-4))<.001,"Blast repeated after markers consumed");
            state.setComboSeq(ComboStateRegistry.NONE.getId());LegacyUpthrust.attach(player,target);
            var expiring=level.getEntitiesOfClass(LegacyUpthrust.class,player.getBoundingBox().inflate(8)).get(0);health=target.getHealth();
            for(int tick=0;tick<199;tick++)expiring.tick();require(!expiring.isRemoved() && target.getHealth()==health,"Mounted marker expired before200");
            expiring.tick();require(expiring.isRemoved() && target.getHealth()==health-1,"Mounted marker expiry damage");
            report.put("legacy_guard_upthrust",Map.of("batch_wear_one",true,"own_allied_protected",true,"induction_speed",1.5,
                    "thorns_shooter",true,"punch_markers",true,"save_restore_blast",true,"blast_once",true,"mounted_expiry",200));
        }finally {
            entities.forEach(Entity::discard);level.getEntitiesOfClass(LegacyUpthrust.class,player.getBoundingBox().inflate(64)).forEach(Entity::discard);
            var team=player.getScoreboard().getPlayerTeam("legacy_guard");if(team!=null)player.getScoreboard().removePlayerTeam(team);
            target.discard();enemy.discard();player.setItemInHand(InteractionHand.MAIN_HAND,saved);player.setPos(0,160,0);
        }
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
