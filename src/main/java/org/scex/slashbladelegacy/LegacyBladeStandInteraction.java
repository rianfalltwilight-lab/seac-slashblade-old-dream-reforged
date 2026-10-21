package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.entity.BladeStandEntity;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Legacy soul/fence stand creation and direct ignition of the retained native stand entity. */
public final class LegacyBladeStandInteraction {
    private LegacyBladeStandInteraction() {}

    private static Item standType(ItemStack soul) {
        if(soul.is(SlashBladeItems.PROUDSOUL.get()))return SlashBladeItems.BLADESTAND_2.get();
        if(soul.is(SlashBladeItems.PROUDSOUL_INGOT.get()))return SlashBladeItems.BLADESTAND_1.get();
        if(soul.is(SlashBladeItems.PROUDSOUL_SPHERE.get()))return SlashBladeItems.BLADESTAND_1_W.get();
        if(soul.is(SlashBladeItems.PROUDSOUL_TINY.get()))return SlashBladeItems.BLADESTAND_V.get();
        return null;
    }

    public static void block(PlayerInteractEvent.RightClickBlock event) {
        var player=event.getEntity();var level=event.getLevel();var pos=event.getPos();
        if(event.isCanceled() || event.getUseBlock()==TriState.FALSE || event.getUseItem()==TriState.FALSE
                || !LegacyMode.legacy(player) || !player.isShiftKeyDown()
                || !player.mayBuild() || !level.mayInteract(player,pos))return;
        var soul=event.getItemStack();var type=standType(soul);var fence=level.getBlockState(pos);
        if(type==null || !fence.is(BlockTags.WOODEN_FENCES)
                || !player.mayUseItemAt(pos,event.getFace(),soul))return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if(level.isClientSide)return;
        var direction=type==SlashBladeItems.BLADESTAND_1_W.get()?player.getDirection().getOpposite():Direction.UP;
        var stand=BladeStandEntity.createInstanceFromPos(level,pos,direction,type);
        if(direction==Direction.UP)stand.setRotation(Math.floorMod(Math.round(player.getYRot()/45f)+4,8));
        // r87 replaces the fence without consuming the soul. A canceled spawn must retain the fence.
        if(!level.addFreshEntity(stand)) {event.setCancellationResult(InteractionResult.FAIL);return;}
        if(level.getBlockState(pos)!=fence || !level.removeBlock(pos,false)) {
            stand.discard();event.setCancellationResult(InteractionResult.FAIL);return;
        }
        stand.playPlacementSound();
    }

    public static void entity(PlayerInteractEvent.EntityInteract event) {
        var player=event.getEntity();var level=event.getLevel();var flint=event.getItemStack();
        if(event.isCanceled() || !LegacyMode.legacy(player) || !player.mayBuild()
                || !(event.getTarget() instanceof BladeStandEntity stand) || stand.isRemoved()
                || !flint.is(Items.FLINT_AND_STEEL))return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if(level.isClientSide || stand.isOnFire())return;
        LegacyBladeSouls.ignite(stand);
        level.playSound(null,stand.blockPosition(),SoundEvents.FLINTANDSTEEL_USE,SoundSource.BLOCKS,1,1);
        flint.hurtAndBreak(1,player,event.getHand()==InteractionHand.MAIN_HAND?EquipmentSlot.MAINHAND:EquipmentSlot.OFFHAND);
    }
}
