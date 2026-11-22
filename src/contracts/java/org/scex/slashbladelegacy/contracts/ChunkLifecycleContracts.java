package org.scex.slashbladelegacy.contracts;

import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.RegistryEvents;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.scex.slashbladelegacy.*;

/**
 * Real server ticks: owned swords and SA entities in a far chunk that is loaded without entity ticking, fully unloaded,
 * or left by flight. The owner's mode switch must not wait on actions that can no longer advance.
 */
final class ChunkLifecycleContracts {
    private static final TicketType<ChunkPos> TICKET=TicketType.create("scex_legacy_lifecycle",Comparator.comparingLong(ChunkPos::toLong));
    private static final ChunkPos FAR=new ChunkPos(30,30);
    private static final int TIMEOUT=200;
    enum Kind { PHANTOM(true), DIMENSION_FIELD(true), UPSTREAM_SWORD(true), ADDON_SWORD(false);
        final boolean retired; Kind(boolean retired){this.retired=retired;} }
    enum Scenario { CONTROL, NON_TICKING, UNLOADED, FLIGHT, OTHER_LEVEL, FROZEN }
    private record Case(Scenario scenario,Kind kind) {}

    private final MinecraftServer server;
    private final ServerLevel level;
    private ServerLevel target;
    private final Consumer<Map<String,Object>> done;
    private final Map<String,Object> preparation;
    private final Deque<Case> cases=new ArrayDeque<>();
    private final List<Map<String,Object>> rows=new ArrayList<>();
    private final Consumer<ServerTickEvent.Post> ticking=this::tick;
    private Case current;
    private Map<String,Object> row;
    private ServerPlayer owner;
    private Entity entity;
    private UUID entityId;
    private boolean before;
    private int phase,wait,total;
    private final Set<String> tickets=new HashSet<>();

    private ChunkLifecycleContracts(MinecraftServer server,Map<String,Object> preparation,Consumer<Map<String,Object>> done){
        this.server=server;this.level=server.overworld();this.preparation=preparation;this.done=done;
        for(var kind:Kind.values())cases.add(new Case(Scenario.CONTROL,kind));
        for(var scenario:List.of(Scenario.NON_TICKING,Scenario.UNLOADED))for(var kind:Kind.values())cases.add(new Case(scenario,kind));
        for(var scenario:List.of(Scenario.FLIGHT,Scenario.OTHER_LEVEL,Scenario.FROZEN))
            for(var kind:List.of(Kind.UPSTREAM_SWORD,Kind.ADDON_SWORD))cases.add(new Case(scenario,kind));
    }
    static void start(MinecraftServer server,Map<String,Object> preparation,Consumer<Map<String,Object>> done){
        if(!Boolean.TRUE.equals(preparation.get("ready"))){done.accept(preparation);return;}
        var run=new ChunkLifecycleContracts(server,new LinkedHashMap<>(preparation),done);
        NeoForge.EVENT_BUS.addListener(run.ticking);
    }

    private void tick(ServerTickEvent.Post event){
        if(event.getServer()!=server)return;
        try {
            if(++total>cases.size()*TIMEOUT*2+4000)throw new IllegalStateException("Chunk lifecycle contracts exceeded their tick budget");
            if(current==null){
                if(cases.isEmpty()){finish(null);return;}
                begin(cases.poll());
            }
            step();
        } catch(Throwable failure) {finish(failure);}
    }

    private void begin(Case next){
        current=next;phase=0;wait=0;entity=null;entityId=null;
        row=new LinkedHashMap<>();row.put("scenario",next.scenario().name());row.put("kind",next.kind().name());rows.add(row);
        owner=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"Lifecycle"+rows.size()));
        owner.setPos(8,160,8);owner.setOnGround(true);level.addNewPlayer(owner);
        owner.setItemInHand(InteractionHand.MAIN_HAND,blade());LegacyMode.bindInventory(owner);
        before=LegacyMode.modern(owner);
        target=next.scenario()==Scenario.OTHER_LEVEL?server.getLevel(net.minecraft.world.level.Level.NETHER):level;
        if(target==null)throw new IllegalStateException("No nether level");
        // CONTROL, FLIGHT, OTHER_LEVEL and FROZEN start in an entity-ticking chunk; the others loaded without entity ticking.
        ticket(ticking(next.scenario())?2:0,true);
    }

    private void step(){
        var center=new BlockPos(FAR.getMiddleBlockX(),180,FAR.getMiddleBlockZ());
        wait++;
        switch(phase) {
            case 0 -> { // chunk reaches the requested state
                boolean loaded=target.hasChunk(FAR.x,FAR.z) && target.areEntitiesLoaded(FAR.toLong());
                boolean ticking=target.isPositionEntityTicking(center);
                if(loaded && ticking==ticking(current.scenario())){spawn(Vec3.atCenterOf(center));next();}
                else timeout("chunk state");
            }
            case 1 -> { // entity joined and is tracked
                if(entity.isRemoved()){fail("entity removed before the scenario started");return;}
                row.put("tracked_blocking_before",!LegacyMode.ready(owner));
                switch(current.scenario()) {
                    case CONTROL -> {
                        expect("ticking action blocks the switch",!LegacyMode.ready(owner));
                        LegacyMode.request(owner);
                        expect("switch waits for the ticking action",LegacyMode.pending(owner) && LegacyMode.modern(owner)==before);
                        LegacyMode.request(owner);entity.discard();endCase();
                    }
                    case NON_TICKING -> {request();phase=3;wait=0;}
                    case UNLOADED -> {release(0);next();}
                    case FLIGHT -> {
                        var sword=(EntityAbstractSummonedSword)entity;sword.shoot(1,0,0,3,0);next();
                    }
                    case OTHER_LEVEL -> {
                        expect("owner resolves across levels (cached owner)",((mods.flammpfeil.slashblade.entity.IShootable)entity).getShooter()==owner);
                        request();phase=3;wait=0;
                    }
                    case FROZEN -> {server.tickRateManager().setFrozen(true);request();phase=3;wait=0;}
                }
            }
            case 2 -> { // UNLOADED: entity leaves with its chunk; FLIGHT: entity leaves entity-ticking range
                if(current.scenario()==Scenario.UNLOADED) {
                    if(!target.hasChunk(FAR.x,FAR.z) && target.getEntity(entityId)==null){row.put("unload_ticks",wait);request();phase=3;wait=0;}
                    else timeout("chunk unload");
                } else {
                    if(entity.isRemoved()){fail("flying sword expired before leaving the ticking chunk");return;}
                    if(!target.isPositionEntityTicking(entity.blockPosition())){row.put("flight_ticks",wait);row.put("flight_x",entity.getX());request();phase=3;wait=0;}
                    else timeout("flight");
                }
            }
            case 3 -> { // switch completes
                owner.setOnGround(true);owner.swinging=false;
                if(LegacyMode.pending(owner))LegacyMode.finish(owner);
                if(!LegacyMode.pending(owner)) {
                    boolean switched=LegacyMode.modern(owner)!=before;
                    row.put("switch_ticks",wait);
                    expect("switch completes",switched);
                    if(current.scenario()==Scenario.UNLOADED){ticket(0,false);next();return;}
                    boolean retired=entity.isRemoved();
                    row.put("entity_retired",retired);
                    expect(current.kind().retired?"own or SlashBlade action retired":"addon action left to its own lifecycle",retired==current.kind().retired);
                    endCase();
                } else if(wait>40){expect("switch completes",false);endCase();}
            }
            case 4 -> { // UNLOADED: reload after the switch
                if(target.hasChunk(FAR.x,FAR.z) && target.areEntitiesLoaded(FAR.toLong())) {
                    if(wait<5 && target.getEntity(entityId)==null)return; // allow the join event to run
                    var reloaded=target.getEntity(entityId);
                    row.put("reloaded_present",reloaded!=null);
                    expect(current.kind().retired?"previous-mode action cannot resume after reload":"addon action reloads unchanged",(reloaded!=null)!=current.kind().retired);
                    if(reloaded!=null)reloaded.discard();
                    endCase();
                } else timeout("chunk reload");
            }
            default -> throw new IllegalStateException("phase "+phase);
        }
    }

    private static boolean ticking(Scenario scenario){return scenario!=Scenario.NON_TICKING && scenario!=Scenario.UNLOADED;}
    private void spawn(Vec3 at){
        var source=LegacyRangeAttack.sourceId(owner.getMainHandItem());
        switch(current.kind()) {
            case PHANTOM -> {
                var sword=new LegacyPhantomSword(SummonedBladeMode.SWORD.get(),target);
                sword.initialize(owner,source,LegacyRangeAttack.Art.SINGLE,0,1,0xFFFFFF,null,null,false);
                sword.setPos(at);entity=sword;
                if(!target.addFreshEntity(sword))throw new IllegalStateException("phantom sword join canceled");
            }
            case DIMENSION_FIELD -> entity=LegacyArtEntity.spawn(owner,owner.getMainHandItem(),LegacyArtEntity.Mode.DIMENSION,at,200,1,false);
            case UPSTREAM_SWORD,ADDON_SWORD -> {
                var type=current.kind()==Kind.UPSTREAM_SWORD?RegistryEvents.SummonedSword:Contracts.ADDON_SWORD.get();
                var sword=new EntityAbstractSummonedSword(type,target);
                sword.setOwner(owner);sword.setNoGravity(true);sword.setPos(at);entity=sword;
                if(!target.addFreshEntity(sword))throw new IllegalStateException("summoned sword join canceled");
            }
        }
        if(entity==null || entity.isRemoved())throw new IllegalStateException(current.kind()+" was not spawned");
        entityId=entity.getUUID();
        row.put("type",net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }

    private void request(){
        LegacyMode.request(owner);
        row.put("immediate",!LegacyMode.pending(owner));
    }
    private void next(){phase++;wait=0;}
    private void timeout(String what){if(wait>TIMEOUT)fail("timed out waiting for "+what);}
    private void fail(String message){expect(message,false);endCase();}
    private void expect(String check,boolean passed){
        @SuppressWarnings("unchecked") var checks=(Map<String,Boolean>)row.computeIfAbsent("checks",k->new LinkedHashMap<String,Boolean>());
        checks.put(check,passed);
        if(!passed)row.put("passed",false);
    }
    private void endCase(){
        row.putIfAbsent("passed",true);
        if(entity!=null && !entity.isRemoved())entity.discard();
        server.tickRateManager().setFrozen(false);
        var leftover=entityId==null || target==null?null:target.getEntity(entityId);if(leftover!=null)leftover.discard();
        releaseAll();
        if(owner!=null){owner.discard();owner=null;}
        current=null;
    }
    private void ticket(int distance,boolean forceTicks){
        String key=distance+":"+forceTicks;
        if(tickets.add(key))target.getChunkSource().addRegionTicket(TICKET,FAR,distance,FAR,forceTicks);
    }
    private void release(int distance){
        for(boolean force:List.of(true,false))if(tickets.remove(distance+":"+force))target.getChunkSource().removeRegionTicket(TICKET,FAR,distance,FAR,force);
    }
    private void releaseAll(){
        for(var key:List.copyOf(tickets)){var p=key.split(":");target.getChunkSource().removeRegionTicket(TICKET,FAR,Integer.parseInt(p[0]),FAR,Boolean.parseBoolean(p[1]));}
        tickets.clear();
    }
    private net.minecraft.world.item.ItemStack blade(){
        return server.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY)
                .getOrThrow(ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY,ResourceLocation.parse("slashblade:sange"))).value().getBlade(server.registryAccess());
    }
    private void finish(Throwable failure){
        NeoForge.EVENT_BUS.unregister(ticking);server.tickRateManager().setFrozen(false);
        if(current!=null){if(failure!=null)row.put("error",failure.toString());endCase();}
        releaseAll();
        var result=new LinkedHashMap<String,Object>();
        boolean passed=failure==null && rows.stream().allMatch(r->Boolean.TRUE.equals(r.get("passed")));
        result.put("passed",passed);result.put("cases",rows.size());result.put("ticks",total);
        if(failure!=null)result.put("error",failure.toString());
        result.put("rows",rows);
        preparation.put("chunk_lifecycle",result);
        done.accept(preparation);
    }
}
