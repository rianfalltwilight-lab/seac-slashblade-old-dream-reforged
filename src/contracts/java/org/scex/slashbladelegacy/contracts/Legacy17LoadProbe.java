package org.scex.slashbladelegacy.contracts;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.scex.slashbladelegacy.LegacyCompat;

/** Bounded synthetic load, same world/JARs with only the full r87 engine switch changed. */
public final class Legacy17LoadProbe {
    private static final int WARM=100,MEASURE=300,PHASES=18;
    private static Legacy17LoadProbe active;
    private final MinecraftServer server;
    private final List<ServerPlayer> players=new ArrayList<>();
    private final List<Mob> targets=new ArrayList<>();
    private final List<Map<String,Object>> rows=new ArrayList<>();
    private final List<Double> samples=new ArrayList<>();
    private final Consumer<ServerTickEvent.Post> tick=this::tick;
    private final boolean original;
    private final Map<String,Object> report=new LinkedHashMap<>();
    private int phase,age,clicks;private double healthRemoved;private long start,cpuStart,gcStart;private boolean recording;
    private Legacy17LoadProbe(MinecraftServer server,Map<String,Object> preparation){
        this.server=server;original=LegacyCompat.LEGACY_COMBAT.get();report.put("preparation",preparation);
        report.put("scope","Synthetic FakePlayer item.use at 2.5 clicks/s each; real entity ticks and complete tickServer timing. Same process/world/dependencies, engine toggle only. No real network clients or long-duration production claim.");
        report.put("design",Map.of("pairs_per_scale",3,"scales",List.of("idle","1 attacker / 8 targets","8 attackers / 32 targets"),"warm_ticks_per_phase",WARM,"measured_ticks_per_phase",MEASURE,"order","BC, CB, BC at each scale","p95_budget_ms",25,"over_50ms_max_fraction",.01,"relative_tolerance","candidate p95 <= baseline p95 * 1.25 + 2 ms","quantile","nearest rank"));
    }
    static void start(MinecraftServer server,Map<String,Object> preparation){
        if(active!=null)throw new IllegalStateException("Already running");
        var probe=new Legacy17LoadProbe(server,preparation);active=probe;
        try{if(!Boolean.TRUE.equals(preparation.get("ready")))throw new IllegalStateException("Fixture not ready");
            NeoForge.EVENT_BUS.addListener(probe.tick);probe.setup();
        }catch(Throwable failure){probe.finish(failure);}
    }
    public static void begin(){var p=active;if(p!=null){p.start=System.nanoTime();p.recording=true;}}
    public static void end(){var p=active;if(p!=null && p.recording){p.recording=false;p.sample((System.nanoTime()-p.start)/1e6);}}
    private boolean legacy(){int pair=(phase%6)/2;boolean first=phase%2==0;return pair==1?first:!first;}
    private void setup(){
        clear();age=0;clicks=0;healthRemoved=0;samples.clear();LegacyCompat.LEGACY_COMBAT.set(legacy());
        int scale=phase/6,attackers=scale==0?0:scale==1?1:8,count=scale==0?0:scale==1?8:32;
        var level=server.overworld();
        var definition=server.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements()
            .filter(h->h.key().location().equals(ResourceLocation.parse("slashblade:sange"))).findFirst().orElseThrow();
        for(int i=0;i<attackers;i++){
            var player=FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes(("r87load"+phase+":"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8)),"Load"+i));
            player.setPos(24,160,0);player.setYRot(0);player.setXRot(0);player.setOnGround(true);player.setNoGravity(true);player.getAbilities().instabuild=false;
            var blade=definition.value().getBlade(server.registryAccess());blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);
            var state=BladeStateAccess.of(blade).orElseThrow();state.setSpecialEffects(new net.minecraft.nbt.ListTag());state.setMaxDamage(1_000_000);state.setDamage(0);state.setBroken(false);state.setSealed(false);state.setProudSoulCount(10000);
            player.setItemInHand(InteractionHand.MAIN_HAND,blade);level.addNewPlayer(player);players.add(player);
        }
        for(int i=0;i<count;i++){
            var mob=EntityType.ZOMBIE.create(level);mob.setPos(24+(i%4-.5)*.2,160,1.5+(i/4)*.1);mob.setNoAi(true);mob.setNoGravity(true);
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1_000_000);mob.setHealth(1_000_000);mob.setPersistenceRequired();
            if(!level.addFreshEntity(mob))throw new IllegalStateException("Target spawn canceled");targets.add(mob);
        }
        cpuStart=ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();gcStart=gcMillis();
    }
    private void tick(ServerTickEvent.Post event){
        if(event.getServer()!=server)return;
        try{
            for(var p:players){p.setPos(24,160,0);p.setOnGround(true);p.setDeltaMovement(0,0,0);
                if(age%8==0){p.getMainHandItem().getItem().use(p.level(),p,InteractionHand.MAIN_HAND);p.stopUsingItem();clicks++;}}
            for(int i=0;i<targets.size();i++){var m=targets.get(i);if(!m.isAlive())throw new IllegalStateException("Load target died");healthRemoved+=m.getMaxHealth()-m.getHealth();m.setHealth(m.getMaxHealth());m.setRemainingFireTicks(0);m.setPos(24+(i%4-.5)*.2,160,1.5+(i/4)*.1);m.setDeltaMovement(0,0,0);}
        }catch(Throwable failure){finish(failure);}
    }
    private void sample(double ms){
        try{
            if(age++>=WARM)samples.add(ms);
            if(age<WARM+MEASURE)return;
            var sorted=samples.stream().sorted().toList();long slow=samples.stream().filter(v->v>50).count();
            var row=new LinkedHashMap<String,Object>();row.put("phase",phase);row.put("legacy",legacy());row.put("scale",phase/6);row.put("pair",(phase%6)/2);row.put("attackers",players.size());row.put("targets",targets.size());row.put("clicks",clicks);
            row.put("sample_count",samples.size());row.put("p50_ms",q(sorted,.5));row.put("p95_ms",q(sorted,.95));row.put("p99_ms",q(sorted,.99));row.put("max_ms",sorted.getLast());row.put("over_50ms",slow);
            row.put("cpu_ms_including_warm",(ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime()-cpuStart)/1e6);row.put("gc_pause_ms_including_warm",gcMillis()-gcStart);
            row.put("health_removed",healthRemoved);row.put("tick_ms",List.copyOf(samples));rows.add(row);
            if(q(sorted,.95)>25 || slow>MEASURE*.01)throw new IllegalStateException("Predeclared absolute tick budget exceeded in phase "+phase);
            if(++phase==PHASES){finish(null);return;}setup();
        }catch(Throwable failure){finish(failure);}
    }
    private void finish(Throwable failure){
        if(active!=this)return;active=null;NeoForge.EVENT_BUS.unregister(tick);clear();LegacyCompat.LEGACY_COMBAT.set(original);ContractWorldReady.release();
        boolean relative=true;
        if(failure==null)for(int scale=0;scale<3;scale++)for(int pair=0;pair<3;pair++){
            double b=0,c=0;for(var row:rows)if((int)row.get("scale")==scale && (int)row.get("pair")==pair){if((boolean)row.get("legacy"))c=(double)row.get("p95_ms");else b=(double)row.get("p95_ms");}relative&=c<=b*1.25+2;
        }
        report.put("phases",rows);report.put("relative_budget_passed",relative);report.put("success",failure==null && relative);report.put("status",failure==null && relative?"BOUNDED_SYNTHETIC_LOAD_PASS":"FAIL");
        report.put("unmeasured",List.of("production configured pack","real client network traffic","long-duration allocation/live-heap growth","player-built worlds","high-latency multiplayer"));
        if(failure!=null){report.put("error",failure.toString());failure.printStackTrace();}
        try{Files.writeString(Path.of("contracts.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new RuntimeException(e);}finally{server.halt(false);}
    }
    private void clear(){targets.forEach(Entity::discard);targets.clear();for(var p:players){p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.discard();}players.clear();}
    private static double q(List<Double> sorted,double fraction){return sorted.get(Math.max(0,(int)Math.ceil(sorted.size()*fraction)-1));}
    private static long gcMillis(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionTime())).sum();}
}
