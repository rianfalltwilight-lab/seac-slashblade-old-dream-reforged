package org.scex.slashbladelegacy.contracts;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import java.util.*;
import java.util.function.Consumer;

/** A bounded real-tick preparation phase for fixed fixture coordinates in a cold production-world copy. */
final class ContractWorldReady {
    private static final TicketType<ChunkPos> TICKET=TicketType.create("scex_legacy_contracts_fixture",Comparator.comparingLong(ChunkPos::toLong));
    private static ContractWorldReady active;
    private final MinecraftServer server;
    private final Consumer<Map<String,Object>> callback;
    private final List<ChunkPos> chunks=new ArrayList<>();
    private final List<Entity> markers=new ArrayList<>();
    private final Consumer<ServerTickEvent.Post> ticking=this::tick;
    private final Consumer<ServerStoppingEvent> stopping=this::stop;
    private int ticks;
    private boolean finished;

    private ContractWorldReady(MinecraftServer server,Consumer<Map<String,Object>> callback){
        this.server=server;this.callback=callback;
        // Existing contracts use x=-2..76, z=-3..16, plus an intentionally out-of-range target at (24,160,100).
        for(int x=-1;x<=4;x++)for(int z=-1;z<=1;z++)chunks.add(new ChunkPos(x,z));
        chunks.add(new ChunkPos(1,6));
    }
    static void prepare(MinecraftServer server,Consumer<Map<String,Object>> callback){
        if(active!=null)throw new IllegalStateException("Contract preparation already active");
        active=new ContractWorldReady(server,callback);
        NeoForge.EVENT_BUS.addListener(active.ticking);NeoForge.EVENT_BUS.addListener(active.stopping);
        for(var pos:active.chunks)server.overworld().getChunkSource().addRegionTicket(TICKET,pos,2,pos,true);
    }
    private void tick(ServerTickEvent.Post event){
        if(event.getServer()!=server || finished)return;
        ticks++;
        try{
            var level=server.overworld();
            var pending=new ArrayList<String>();
            for(var pos:chunks)if(!level.isPositionEntityTicking(new BlockPos(pos.getMinBlockX()+8,190,pos.getMinBlockZ()+8)))pending.add(pos.toString());
            if(!pending.isEmpty()){
                if(ticks>=200)finish(false,Map.of("pending_chunks",pending,"error","Entity-ticking preparation timed out"));
                return;
            }
            if(markers.isEmpty()){
                for(var pos:chunks){
                    var marker=EntityType.MARKER.create(level);
                    marker.setPos(pos.getMinBlockX()+8,190,pos.getMinBlockZ()+8);markers.add(marker);
                    if(!level.addFreshEntity(marker))throw new IllegalStateException("Fixture marker join canceled at "+pos);
                }
            }
            int visible=0;
            for(var marker:markers)if(level.getEntitiesOfClass(Entity.class,marker.getBoundingBox().inflate(.5),e->e==marker).size()==1)visible++;
            if(visible!=markers.size()){
                if(ticks>=200)finish(false,Map.of("visible_markers",visible,"error","Fixture entities are not queryable"));
                return;
            }
            finish(true,Map.of("visible_markers",visible));
        }catch(Throwable failure){
            if(finished)throw new IllegalStateException("Contract callback failed",failure);
            finish(false,Map.of("error",failure.toString()));
        }
    }
    private void finish(boolean ready,Map<String,Object> details){
        if(finished)return;finished=true;
        NeoForge.EVENT_BUS.unregister(ticking);
        markers.forEach(Entity::discard);markers.clear();
        var report=new LinkedHashMap<String,Object>();report.put("ready",ready);report.put("real_wait_ticks",ticks);
        report.put("ticket_chunks",chunks.stream().map(ChunkPos::toString).toList());report.putAll(details);
        // Tickets stay active throughout the synchronous contract assertions and are released by their finally block.
        callback.accept(report);
    }
    private void stop(ServerStoppingEvent event){if(event.getServer()==server)release();}
    static void release(){
        var current=active;if(current==null)return;active=null;
        NeoForge.EVENT_BUS.unregister(current.ticking);NeoForge.EVENT_BUS.unregister(current.stopping);
        current.markers.forEach(Entity::discard);current.markers.clear();
        for(var pos:current.chunks)current.server.overworld().getChunkSource().removeRegionTicket(TICKET,pos,2,pos,true);
    }
}
