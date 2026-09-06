package org.scex.slashbladelegacy.contracts;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;

/** Advance only the authoritative clock between synthetic input calls; not a real world tick. */
final class InputClock {
    static void next(ServerPlayer player) {
        var data=player.server.getWorldData().overworldData();
        data.setGameTime(player.server.overworld().getGameTime()+1);
    }
    static void use(ServerPlayer player,ItemStack blade) {
        next(player);blade.getItem().use(player.level(),player,InteractionHand.MAIN_HAND);
    }
}
