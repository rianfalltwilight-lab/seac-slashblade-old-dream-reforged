package org.scex.slashbladelegacy.contracts;

import java.lang.management.ManagementFactory;
import java.util.*;
import com.mojang.authlib.GameProfile;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.slashblade.SlashBladeDataComponents;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import org.scex.slashbladelegacy.LegacyMode;
import org.scex.slashbladelegacy.LegacyRangeAttack;
import org.scex.slashbladelegacy.SummonedBladeMode;

/** Compare shared public APIs. Record samples without a speed threshold. */
final class PerformanceContracts {
    private static final int WARMUP=400,GROUPS=5,OPERATIONS=200,PAYLOAD_BYTES=1024;
    private static volatile ItemStack observed;

    static void run(MinecraftServer server,Map<String,Object> report) {
        var rows=new LinkedHashMap<String,Object>();report.put("performance_20261008",rows);
        var player=player(server,"PerfLookup");
        try {
            var inventory=player.getInventory();int slots=inventory.getContainerSize();
            check(slots==41,"expected the 41-slot player inventory");
            for(int i=0;i<slots;i++) {
                var blade=new ItemStack(SlashBladeItems.SLASHBLADE.get());
                check(BladeStateAccess.of(blade).isPresent(),"fixture blade state");
                var payload=new byte[PAYLOAD_BYTES];Arrays.fill(payload,(byte)i);
                var tag=new CompoundTag();tag.putUUID(SummonedBladeMode.SOURCE,new UUID(0x53434558L,i+1));
                tag.putByteArray("performance_payload",payload);tag.putInt("performance_slot",i);
                blade.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));inventory.setItem(i,blade);
            }
            LegacyMode.bindInventory(player);
            rows.put("workload",Map.of("slots",slots,"blade_count",slots,"custom_payload_bytes_per_blade",PAYLOAD_BYTES,
                    "custom_data_fields",List.of(SummonedBladeMode.SOURCE,"performance_payload","performance_slot"),
                    "target_slot",40,"warmup_operations_per_api",WARMUP,"groups_per_api",GROUPS,
                    "operations_per_group",OPERATIONS,"mode","legacy; already bound; no world ticks during measurement"));

            var snapshots=snapshot(player);var source=new UUID(0x53434558L,41);
            var original=inventory.getItem(40);var first=inventory.getItem(0);long tick=player.level().getGameTime();
            check(LegacyRangeAttack.sourceBlade(player,source)==original,"unique source identity");
            inventory.setItem(0,original.copy());
            check(LegacyRangeAttack.sourceBlade(player,source).isEmpty(),"same-tick duplicate UUID rejected");
            inventory.setItem(0,first);
            check(LegacyRangeAttack.sourceBlade(player,source)==original,"duplicate removal takes effect immediately");
            inventory.setItem(40,ItemStack.EMPTY);
            check(LegacyRangeAttack.sourceBlade(player,source).isEmpty(),"source removal takes effect immediately");
            var replacement=original.copy();inventory.setItem(40,replacement);
            check(LegacyRangeAttack.sourceBlade(player,source)==replacement,"replacement source takes effect immediately");
            check(player.level().getGameTime()==tick,"source mutations stay in one tick");
            check(snapshot(player).equals(snapshots),"source lookup preserves item NBT");
            restoreMissingState(player);
            rows.put("correctness",Map.of("passed",true,"unique_source",true,"same_tick_duplicate_rejected",true,
                    "immediate_remove_and_replace",true,"item_nbt_unchanged",true,"seeded_missing_state_restored",true));

            try(var allocations=new Allocations()) {
                rows.put("allocation_counter",allocations.status);
                rows.put("source_blade",measure(()->observed=LegacyRangeAttack.sourceBlade(player,source),allocations));
                check(observed==replacement,"measured lookup returns the replacement source");
                check(snapshot(player).equals(snapshots),"measured source lookup preserves item NBT");
                rows.put("bind_inventory",measure(()->LegacyMode.bindInventory(player),allocations));
                check(snapshot(player).equals(snapshots),"already-bound inventory preserves item NBT");
            }
            rows.put("scope","Current-thread allocation and batched API latency; not MSPT, TPS or a long-running load test");
        } finally {observed=null;player.getInventory().clearContent();player.discard();}
    }

    static void regressions(MinecraftServer server,Map<String,Object> report) {
        var player=player(server,"PerfProjectile");
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SlashBladeItems.SLASHBLADE.get()));
            Range17Contracts.run(player,report);BaseArts17Contracts.run(player,report);
            player.getInventory().clearContent();player.setPos(0,160,0);player.setYRot(0);player.setXRot(0);
            player.setOnGround(true);player.removeAllEffects();
            var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);rank.setRawRankPoint(0);rank.setLastUpdte(player.level().getGameTime());
            SbContracts.run(player,new ItemStack(SlashBladeItems.SLASHBLADE.get()),report);
        } finally {player.getInventory().clearContent();player.discard();}
        Super17Contracts.run(server,report);
    }

    private static ServerPlayer player(MinecraftServer server,String name) {
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),name));
        player.setGameMode(GameType.SURVIVAL);player.setPos(0,160,0);player.getPersistentData().remove(LegacyMode.MODERN);
        server.overworld().addNewPlayer(player);return player;
    }

    private static void restoreMissingState(ServerPlayer player) {
        var blade=new ItemStack(SlashBladeItems.SLASHBLADE.get());
        BladeStateAccess.of(blade).orElseThrow().setTranslationKey("item.slashblade_addon.terra_blade");
        LegacyMode.bind(blade,player);
        check(blade.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).contains("slashblade_legacy_compat.terra_effects_added"),"fixture has the existing seed marker");
        blade.remove(SlashBladeDataComponents.BLADE_STATE_DATA.get());
        check(!blade.has(SlashBladeDataComponents.BLADE_STATE_DATA.get()),"fixture removes blade state");
        LegacyMode.bind(blade,player);
        check(blade.has(SlashBladeDataComponents.BLADE_STATE_DATA.get()),"same-mode bind restores missing blade state after seeding");
    }

    private static List<Tag> snapshot(ServerPlayer player) {
        var result=new ArrayList<Tag>();
        for(int i=0;i<player.getInventory().getContainerSize();i++)result.add(player.getInventory().getItem(i).save(player.registryAccess()));
        return result;
    }

    private static Map<String,Object> measure(Runnable operation,Allocations allocations) {
        for(int i=0;i<WARMUP;i++)operation.run();
        var samples=new ArrayList<Map<String,Object>>();
        for(int group=0;group<GROUPS;group++) {
            long beforeBytes=allocations.read(),start=System.nanoTime();
            for(int i=0;i<OPERATIONS;i++)operation.run();
            long nanos=System.nanoTime()-start,afterBytes=allocations.read();
            var sample=new LinkedHashMap<String,Object>();sample.put("group",group+1);sample.put("operations",OPERATIONS);
            sample.put("elapsed_ns",nanos);sample.put("ns_per_operation",(double)nanos/OPERATIONS);
            boolean available=beforeBytes>=0 && afterBytes>=beforeBytes;sample.put("allocation_available",available);
            if(available){sample.put("allocated_bytes",afterBytes-beforeBytes);sample.put("bytes_per_operation",(double)(afterBytes-beforeBytes)/OPERATIONS);}
            samples.add(sample);
        }
        return Map.of("warmup_operations",WARMUP,"samples",samples);
    }

    private static final class Allocations implements AutoCloseable {
        private com.sun.management.ThreadMXBean bean;
        private boolean enabledHere;
        private final long thread=Thread.currentThread().threadId();
        private String status="unavailable: ThreadMXBean does not expose allocation bytes";
        Allocations() {
            if(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean candidate && candidate.isThreadAllocatedMemorySupported()) {
                try {
                    if(!candidate.isThreadAllocatedMemoryEnabled()){candidate.setThreadAllocatedMemoryEnabled(true);enabledHere=true;}
                    bean=candidate;status="available: current-thread allocated bytes";
                } catch(UnsupportedOperationException | SecurityException failure){status="unavailable: "+failure.getClass().getSimpleName();}
            }
        }
        long read(){return bean==null?-1:bean.getThreadAllocatedBytes(thread);}
        @Override public void close(){if(enabledHere && bean!=null)bean.setThreadAllocatedMemoryEnabled(false);}
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError("performance contract: "+message);}
}
