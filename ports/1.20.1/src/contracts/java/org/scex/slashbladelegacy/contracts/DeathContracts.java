package org.scex.slashbladelegacy.contracts;

import com.mojang.authlib.GameProfile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.scex.slashbladelegacy.LegacyCapabilities;
import org.scex.slashbladelegacy.LegacyMode;

/** Exercise dead-player ticks without replacing missing capabilities with default values. */
final class DeathContracts {
    private DeathContracts() {}

    static void run(MinecraftServer server,Map<String,Object> report) {
        var rows=new LinkedHashMap<String,Object>();report.put("death_contracts",rows);
        var original=player(server,"DeathOriginal");var replacement=player(server,"DeathClone");
        var live=player(server,"LiveMissingRank");
        try {
            var blade=ContractItems.blade();original.setItemInHand(InteractionHand.MAIN_HAND,blade);
            ticks(original);check(original.isAlive(),"baseline player is alive");
            int[] scheduledCalls={0};
            LegacyCapabilities.input(original).getScheduler().schedule("death_contract_sentinel",server.overworld().getGameTime(),
                    (entity,queue,time)->scheduledCalls[0]++);
            original.setHealth(0);original.invalidateCaps();
            check(original.isDeadOrDying() && !original.isRemoved(),"dead player remains in the tick lifecycle");
            check(!original.getCapability(CapabilityInputState.INPUT_STATE).isPresent()
                    && !original.getCapability(CapabilityConcentrationRank.RANK_POINT).isPresent(),"dead capabilities are invalid");
            for(int tick=0;tick<25;tick++)ticks(original);
            check(!original.getCapability(CapabilityConcentrationRank.RANK_POINT).isPresent(),"dead ticks do not revive rank");
            check(scheduledCalls[0]==0,"dead inventory ticks do not run the retained scheduler");
            rows.put("dead_invalid_capability_ticks",Map.of("start_events",25,"end_events",25,"inventory_ticks",25,"scheduled_calls",scheduledCalls[0],"passed",true));

            original.getPersistentData().putBoolean(LegacyMode.MODERN,true);
            replacement.setItemInHand(InteractionHand.MAIN_HAND,blade.copy());
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement,original,true));
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(replacement,false));
            ticks(replacement);
            check(replacement.isAlive() && LegacyMode.modern(replacement),"clone restores the player mode");
            check(replacement.getCapability(CapabilityInputState.INPUT_STATE).isPresent(),"respawn restores input");
            check(LegacyCapabilities.rank(replacement).getMaxCapacity()==1799,"respawn binds the new rank mode");
            check(!original.getCapability(CapabilityConcentrationRank.RANK_POINT).isPresent(),"clone leaves the old capabilities invalid");
            rows.put("clone_and_respawn",Map.of("mode_preserved",true,"new_input_present",true,"new_rank_capacity",1799,"passed",true));

            live.invalidateCaps();check(live.isAlive(),"missing-rank control remains alive");
            boolean rejected=false;
            try{LegacyCapabilities.rank(live);}catch(IllegalStateException expected){rejected=true;}
            check(rejected,"an alive player without rank is still rejected");
            rows.put("live_missing_rank_stays_strict",true);
        } finally {original.discard();replacement.discard();live.discard();}
    }

    private static ServerPlayer player(MinecraftServer server,String name) {
        var player=FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),name));
        player.connection=new ContractItems.PacketSink(server,player);player.setPos(8,160,8);player.setHealth(player.getMaxHealth());
        return player;
    }

    private static void ticks(ServerPlayer player) {
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START,player));
        player.getMainHandItem().inventoryTick(player.level(),player,0,true);
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));
    }

    private static void check(boolean passed,String message) {
        if(!passed)throw new AssertionError("death: "+message);
    }
}
