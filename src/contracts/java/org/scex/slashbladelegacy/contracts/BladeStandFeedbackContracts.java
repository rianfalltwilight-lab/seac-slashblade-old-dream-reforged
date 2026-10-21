package org.scex.slashbladelegacy.contracts;

import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.entity.BladeStandEntity;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.scex.slashbladelegacy.*;

final class BladeStandFeedbackContracts {
    static void run(ServerPlayer player,Map<String,Object> report) {
        var level=player.serverLevel();var held=player.getMainHandItem();boolean shift=player.isShiftKeyDown();
        boolean creative=player.getAbilities().instabuild;var pos=player.blockPosition().offset(3,0,0);var previous=level.getBlockState(pos);
        var stands=new ArrayList<BladeStandEntity>();int checks=0;
        try {
            player.getAbilities().instabuild=false;player.setShiftKeyDown(true);
            var materials=List.of(SlashBladeItems.PROUDSOUL.get(),SlashBladeItems.PROUDSOUL_INGOT.get(),SlashBladeItems.PROUDSOUL_SPHERE.get(),SlashBladeItems.PROUDSOUL_TINY.get());
            var types=List.of(SlashBladeItems.BLADESTAND_2.get(),SlashBladeItems.BLADESTAND_1.get(),SlashBladeItems.BLADESTAND_1_W.get(),SlashBladeItems.BLADESTAND_V.get());
            for(int i=0;i<materials.size();i++) {
                level.setBlockAndUpdate(pos,Blocks.OAK_FENCE.defaultBlockState());
                var soul=new ItemStack(materials.get(i),2);player.setItemInHand(InteractionHand.MAIN_HAND,soul);
                var event=block(player,pos);NeoForge.EVENT_BUS.post(event);
                var created=level.getEntitiesOfClass(BladeStandEntity.class,new AABB(pos).inflate(1));
                check(event.isCanceled() && level.getBlockState(pos).isAir() && created.size()==1,"fence replaced by one stand");checks++;
                var stand=created.getFirst();stands.add(stand);
                check(stand.currentType==types.get(i) && soul.getCount()==2 && stand.getItem().isEmpty(),"old type mapping and no soul consumption");checks++;
                NeoForge.EVENT_BUS.post(block(player,pos));
                check(level.getEntitiesOfClass(BladeStandEntity.class,new AABB(pos).inflate(1)).size()==1,"repeated input cannot duplicate a stand");checks++;
                stand.discard();
            }
            level.setBlockAndUpdate(pos,Blocks.OAK_FENCE.defaultBlockState());
            Consumer<EntityJoinLevelEvent> cancelSpawn=e->{if(e.getEntity() instanceof BladeStandEntity)e.setCanceled(true);};
            NeoForge.EVENT_BUS.addListener(cancelSpawn);
            try {NeoForge.EVENT_BUS.post(block(player,pos));check(level.getBlockState(pos).is(Blocks.OAK_FENCE),"canceled spawn retains fence");checks++;}
            finally {NeoForge.EVENT_BUS.unregister(cancelSpawn);}
            player.setShiftKeyDown(false);var ordinary=block(player,pos);NeoForge.EVENT_BUS.post(ordinary);
            check(!ordinary.isCanceled() && level.getBlockState(pos).is(Blocks.OAK_FENCE),"non-sneaking right click retains fence");checks++;

            var stand=BladeStandEntity.createInstanceFromPos(level,pos.above(),Direction.UP,SlashBladeItems.BLADESTAND_1.get());
            stand.setItem(held.copy());level.addFreshEntity(stand);stands.add(stand);
            int souls=BladeStateAccess.of(stand.getItem()).orElseThrow().getProudSoulCount();
            var flint=new ItemStack(Items.FLINT_AND_STEEL);player.setItemInHand(InteractionHand.MAIN_HAND,flint);
            var ignite=new PlayerInteractEvent.EntityInteract(player,InteractionHand.MAIN_HAND,stand);NeoForge.EVENT_BUS.post(ignite);
            check(ignite.isCanceled() && stand.isOnFire() && flint.getDamageValue()==1,"flint ignites and costs one durability");checks++;
            check(stand.getPersistentData().getLong("Legacy17StandFireUntil")==level.getGameTime()+400
                    && BladeStateAccess.of(stand.getItem()).orElseThrow().getProudSoulCount()==souls,"flint uses tracked fire without tiny-soul reward");checks++;
            NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player,InteractionHand.MAIN_HAND,stand));
            check(flint.getDamageValue()==1,"already burning stand does not charge again");checks++;
            stand.getPersistentData().putLong("Legacy17StandFireUntil",level.getGameTime());
            LegacyBladeSouls.tick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(stand));
            check(!stand.isOnFire() && !stand.getItem().isEmpty(),"fire expiry preserves displayed blade");checks++;

            // Survival placement and empty-hand retrieval must never manufacture an extra blade.
            var blade=held.copy();stand.setItem(ItemStack.EMPTY);player.setItemInHand(InteractionHand.MAIN_HAND,blade);
            stand.interact(player,InteractionHand.MAIN_HAND);
            check(player.getMainHandItem().isEmpty() && stand.getItem().getCount()==1,"stand stores exactly one survival blade");checks++;
            stand.interact(player,InteractionHand.MAIN_HAND);
            check(player.getMainHandItem().getCount()==1 && stand.getItem().isEmpty(),"stand returns exactly one survival blade");checks++;
            report.put("blade_stand_feedback_checks",checks);
        } finally {
            stands.forEach(BladeStandEntity::discard);level.setBlockAndUpdate(pos,previous);
            player.setItemInHand(InteractionHand.MAIN_HAND,held);player.setShiftKeyDown(shift);player.getAbilities().instabuild=creative;
        }
    }
    private static PlayerInteractEvent.RightClickBlock block(ServerPlayer player,BlockPos pos) {
        return new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError("blade stand feedback: "+message);}
}
