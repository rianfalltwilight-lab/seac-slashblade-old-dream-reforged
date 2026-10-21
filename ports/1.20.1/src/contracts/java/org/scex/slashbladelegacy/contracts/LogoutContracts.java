package org.scex.slashbladelegacy.contracts;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.scex.slashbladelegacy.LegacyInputBudget;
import org.scex.slashbladelegacy.mixin.LegacySchedulerAccessor;

/** Send real logout events before and after Forge invalidates player capabilities. */
final class LogoutContracts {
    private static final List<String> JOBS=List.of("scex_legacy_range","SpiralSwords","StormSwords",
            "BlisteringSwords","HeavyRainSwords","sendPartical","chargeSuperSA");
    private static final List<String> DATA=List.of("slashblade_legacy_compat.spiral",
            "slashblade_legacy_compat.spiral_until","slashblade_legacy_compat.blistering_until",
            "SB.AvoidTimeout","SB.AvoidComboTimeout","SB.AvoidCombo");
    private LogoutContracts() {}

    static void run(MinecraftServer server,Map<String,Object> report) {
        var rows=new ArrayList<Map<String,Object>>();report.put("logout_contracts",rows);
        for(boolean invalid:List.of(false,true)) {
            var player=FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"LogoutContract"));
            var row=new LinkedHashMap<String,Object>();rows.add(row);row.put("capabilities_invalidated",invalid);
            try {
                var input=player.getCapability(CapabilityInputState.INPUT_STATE).orElseThrow(IllegalStateException::new);
                var scheduler=input.getScheduler();var ownedCalls=new AtomicInteger();var foreignCalls=new AtomicInteger();
                long when=server.overworld().getGameTime()+1;
                for(String job:JOBS)scheduler.schedule(job,when,(entity,queue,time)->ownedCalls.incrementAndGet());
                scheduler.schedule("foreign_logout_contract",when,(entity,queue,time)->foreignCalls.incrementAndGet());
                for(String key:DATA)player.getPersistentData().putLong(key,123);
                player.getPersistentData().putString("foreign_logout_contract","keep");
                check(LegacyInputBudget.accept(player) && !LegacyInputBudget.accept(player),"input budget fixture");
                if(invalid)player.invalidateCaps();
                check(player.getCapability(CapabilityInputState.INPUT_STATE).isPresent()!=invalid,"capability fixture");
                for(int pass=0;pass<2;pass++) {
                    MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
                    check(DATA.stream().noneMatch(player.getPersistentData()::contains),"transient NBT cleared on pass "+pass);
                    check(player.getPersistentData().getString("foreign_logout_contract").equals("keep"),"foreign NBT retained");
                    check(LegacyInputBudget.accept(player),"local input budget cleared on pass "+pass);
                    check(player.getCapability(CapabilityInputState.INPUT_STATE).isPresent()!=invalid,"logout does not revive capabilities");
                }
                if(!invalid) {
                    ((LegacySchedulerAccessor)scheduler).legacy$queue().tick(player,when);
                    check(ownedCalls.get()==0 && foreignCalls.get()==1,"only legacy scheduled jobs are removed");
                    row.put("removed_jobs",JOBS);row.put("foreign_job_calls",foreignCalls.get());
                }
                row.put("logout_events",2);row.put("local_data_cleared",true);row.put("passed",true);
            } finally {LegacyInputBudget.clear(player);player.discard();}
        }
    }

    private static void check(boolean pass,String message) {
        if(!pass)throw new AssertionError("logout: "+message);
    }
}
