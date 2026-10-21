package org.scex.slashbladelegacy.contracts;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;

/** Same test JAR runs against the frozen baseline and candidate with real maid classes loaded. */
final class MaidTimelineContracts {
    private static final String CURSOR="slashblade.lastProcessedTick";
    private static int checks;
    private static final List<Entity> spawned=new ArrayList<>();
    private static void check(boolean result,String message) { checks++; if(!result)throw new AssertionError(message); }

    static void runTimelineOnly(MinecraftServer server,Map<String,Object> report) {
        checks=0;var level=server.overworld();long originalTime=level.getGameTime();
        var user=EntityType.ZOMBIE.create(level);
        try {
            check(user!=null,"timeline fixture exists");
            user.setItemSlot(EquipmentSlot.MAINHAND,blade(level));
            boolean optimized=Arrays.stream(ComboState.TimeLineTickAction.class.getDeclaredFields()).anyMatch(f->f.getName().contains("scex$actionTicks"));
            check(!optimized,"1.9.65 retains its constant-time offset timeline");
            timelineSemantics(user,optimized,report);
            report.put("upstream_timeline_kind","1.9.65 offset lookup; no catch-up loop or NONE guard");
            report.put("upstream_none_guard",false);
        } finally {
            if(user!=null)user.discard();
            level.getServer().getWorldData().overworldData().setGameTime(originalTime);
            report.put("upstream_timeline_checks",checks);
        }
    }

    static void run(MinecraftServer server,Map<String,Object> report) throws Exception {
        checks=0;var level=server.overworld();long originalTime=level.getGameTime();
        try {
            boolean optimized=Arrays.stream(ComboState.TimeLineTickAction.class.getDeclaredFields()).anyMatch(f->f.getName().contains("scex$actionTicks"));
            check(!optimized,"1.9.65 offset timeline requires no bounded-loop replacement");
            report.put("timeline_optimized",optimized);
            report.put("runtime",Map.of("java",System.getProperty("java.version"),"processors",Runtime.getRuntime().availableProcessors(),"max_heap",Runtime.getRuntime().maxMemory()));
            var maid=maid(level);report.put("maid_entity_type",BuiltInRegistries.ENTITY_TYPE.getKey(maid.getType()).toString());
            report.put("maid_task",maid.getClass().getMethod("getTask").invoke(maid).getClass().getName());
            timelineSemantics(maid,optimized,report);
            bench(maid,report);
            combat(maid,report);
            var saved=new CompoundTag();maid.save(saved);
            var restored=(Mob)EntityType.loadEntityRecursive(saved,level,e->e);
            check(restored!=null,"maid NBT reload");spawned.add(restored);
            var before=maid.getMainHandItem();var after=restored.getMainHandItem();
            check(ItemStack.isSameItemSameTags(before,after),"maid held blade components survive save/load");
            check(restored.getClass().getMethod("getTask").invoke(restored).getClass().equals(maid.getClass().getMethod("getTask").invoke(maid).getClass()),"maid task survives save/load");
            reset(restored,24_000,0);restored.tick();
            check(BladeStateAccess.of(restored.getMainHandItem()).orElseThrow().getComboSeq().equals(ComboStateRegistry.NONE.getId()),"loaded idle maid remains idle");
            report.put("maid_timeline_checks",checks);
        } finally {
            for(var e:spawned)e.discard();spawned.clear();level.getServer().getWorldData().overworldData().setGameTime(originalTime);
            report.put("maid_timeline_checks",checks);
        }
    }

    private static ItemStack blade(ServerLevel level) {
        return level.registryAccess().registryOrThrow(SlashBladeDefinition.REGISTRY_KEY).holders()
                .filter(h->h.key().location().toString().equals("slashblade:sange")).findFirst().orElseThrow().value().getBlade();
    }
    private static Mob maid(ServerLevel level) throws Exception {
        var type=BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation("touhou_little_maid:maid"));
        var maid=(Mob)type.create(level);check(maid!=null && maid.getClass().getName().endsWith(".EntityMaid"),"real TLM entity factory");
        var task=Class.forName("net.jfrx.slashblade.maidnativepower.task.TaskSlashBlade").getConstructor().newInstance();
        maid.getClass().getMethod("setTask",Class.forName("com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask")).invoke(maid,task);
        maid.setPos(0,160,0);maid.setNoAi(true);maid.setNoGravity(true);maid.setOnGround(true);
        maid.getRandom().setSeed(0x5ce871L);
        maid.setItemSlot(EquipmentSlot.MAINHAND,blade(level));
        check(level.addFreshEntity(maid),"maid added to isolated level");spawned.add(maid);
        var state=BladeStateAccess.of(maid.getMainHandItem()).orElseThrow();state.setDamage(0);state.setBroken(false);state.setKillCount(1234);state.setProudSoulCount(5000);state.setRefine(17);
        return maid;
    }
    private static void reset(LivingEntity user,long elapsed,int cursor) {
        var level=(ServerLevel)user.level();level.getServer().getWorldData().overworldData().setGameTime(elapsed+100);
        var state=BladeStateAccess.of(user.getMainHandItem()).orElseThrow();state.setLastActionTime(100);state.setComboSeq(ComboStateRegistry.NONE.getId());
        user.getPersistentData().putInt(CURSOR,cursor);
    }
    private static void resetActive(LivingEntity user,long elapsed,int cursor) {
        reset(user,elapsed,cursor);
        BladeStateAccess.of(user.getMainHandItem()).orElseThrow().setComboSeq(ComboStateRegistry.STANDBY.getId());
    }
    private static void timelineSemantics(LivingEntity user,boolean optimized,Map<String,Object> report) {
        var trace=new ArrayList<Integer>();
        var action=ComboState.TimeLineTickAction.getBuilder().put(0,e->trace.add(0)).put(3,e->trace.add(3)).build();
        reset(user,480_000,91);action.accept(user);
        check(trace.equals(List.of(0)),"first call uses offset zero at an old world age");
        reset(user,480_003,91);action.accept(user);action.accept(user);
        check(trace.equals(List.of(0,3,3)),"same elapsed tick retains native repeated callback");
        reset(user,480_006,91);action.accept(user);
        check(trace.equals(List.of(0,3,3)),"offset lookup does not replay skipped ticks");
        check(user.getPersistentData().getInt(CURSOR)==91,"offset lookup does not write a 2.0.x cursor");
        var rng=new Random(0x5ce871L);var rows=new ArrayList<String>();
        for(int test=0;test<256;test++) {
            var expected=new ArrayList<Integer>();var observed=new ArrayList<Integer>();
            var ticks=new HashSet<Integer>();var builder=ComboState.TimeLineTickAction.getBuilder();
            for(int i=0;i<12;i++){int key=rng.nextInt(80)-10;ticks.add(key);builder.put(key,e->observed.add(key));}
            var timeline=builder.build();long offset=-1;
            for(int call=0;call<8;call++) {
                long elapsed=rng.nextInt(100),adjusted=elapsed;
                if(offset<0)offset=elapsed;
                adjusted-=offset;
                if(adjusted<0){offset=adjusted;adjusted=0;}
                if(ticks.contains((int)adjusted))expected.add((int)adjusted);
                reset(user,elapsed,91);timeline.accept(user);
            }
            check(observed.equals(expected) && user.getPersistentData().getInt(CURSOR)==91,"native offset oracle "+test);
            rows.add(observed.toString());
        }
        var empty=ComboState.TimeLineTickAction.getBuilder().build();
        reset(user,Integer.MAX_VALUE,91);empty.accept(user);
        check(user.getPersistentData().getInt(CURSOR)==91,"empty timeline remains constant-time at the maximum int age");
        report.put("timeline_reference_trace",rows);
        report.put("timeline_port_boundary","1.9.65 uses a per-action offset lookup. The 2.0.x shared-cursor and catch-up assertions do not apply.");
    }
    private static void bench(Mob maid,Map<String,Object> report) {
        var level=(ServerLevel)maid.level();var rows=new ArrayList<Object>();report.put("maid_tick_benchmarks",rows);
        int[] updates={0};var stack=new ArrayList<String>();
        Consumer<SlashBladeEvent.UpdateEvent> listener=e->{if(e.getEntity()==maid){updates[0]++;if(stack.isEmpty())stack.addAll(StackWalker.getInstance().walk(s->s.map(Object::toString).toList()));}};
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            var bean=ManagementFactory.getThreadMXBean();if(bean.isThreadCpuTimeSupported() && !bean.isThreadCpuTimeEnabled())bean.setThreadCpuTimeEnabled(true);
            for(long age:new long[]{24_000,2_000_000,20_000_000}) {
                reset(maid,age,0);var state=BladeStateAccess.of(maid.getMainHandItem()).orElseThrow();
                int kills=state.getKillCount(),souls=state.getProudSoulCount(),refine=state.getRefine(),wear=maid.getMainHandItem().getDamageValue();
                int initial=updates[0];
                for(int i=0;i<64;i++){level.getServer().getWorldData().overworldData().setGameTime(age+100+i);maid.tick();}
                long[] ns=new long[160];long cpuStart=bean.getCurrentThreadCpuTime();
                for(int i=0;i<ns.length;i++){level.getServer().getWorldData().overworldData().setGameTime(age+164+i);long t=System.nanoTime();maid.tick();ns[i]=System.nanoTime()-t;}
                long cpu=bean.getCurrentThreadCpuTime()-cpuStart;
                check(updates[0]-initial==224,"one real inventory tick per maid tick at "+age+": "+(updates[0]-initial));
                check(state.getKillCount()==kills && state.getProudSoulCount()==souls && state.getRefine()==refine && maid.getMainHandItem().getDamageValue()==wear,"idle tick preserves permanent progression");
                check(maid.getPersistentData().getInt(CURSOR)==0 && state.getLastActionTime()==100,"idle timeline preserves native cursor and action epoch");
                long[] sorted=ns.clone();Arrays.sort(sorted);
                rows.add(Map.of("elapsed_ticks",age,"samples",ns,"mean_ns",Arrays.stream(ns).average().orElseThrow(),"p50_ns",sorted[80],"p95_ns",sorted[151],"p99_ns",sorted[158],"thread_cpu_ns_per_tick",cpu/(double)ns.length,"update_events",updates[0]-initial));
            }
            check(stack.stream().anyMatch(s->s.contains("MaidTickHandler.handleMaidTick")) && stack.stream().anyMatch(s->s.contains("EntityMaid.tick")),"bench traverses actual maid event -> held inventory entry");
            report.put("maid_entry_stack",stack);
            var held=maid.getMainHandItem();maid.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);int initial=updates[0];
            for(int i=0;i<4;i++)maid.tick();check(updates[0]==initial,"unequipped maid does not tick a blade");
            maid.setItemSlot(EquipmentSlot.MAINHAND,held);maid.tick();check(updates[0]==initial+1,"re-equipped maid resumes exactly one blade tick");
        } finally {MinecraftForge.EVENT_BUS.unregister(listener);}
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static void combat(Mob maid,Map<String,Object> report) throws Exception {
        var level=(ServerLevel)maid.level();reset(maid,24000,0);
        var target=EntityType.HUSK.create(level);target.setPos(0,160,2);target.setNoAi(true);target.setNoGravity(true);target.tickCount=100;
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);
        check(level.addFreshEntity(target),"combat target loaded");spawned.add(target);
        check(maid.canAttack(target),"target accepted by actual maid attack policy");
        BehaviorControl attack=(BehaviorControl)Class.forName("net.jfrx.slashblade.maidnativepower.entity.ai.MaidSlashBladeAttack").getMethod("create").invoke(null);
        var state=BladeStateAccess.of(maid.getMainHandItem()).orElseThrow();state.setLastActionTime(level.getGameTime());
        var combos=new ArrayList<String>();var damages=new ArrayList<Float>();int triggers=0;
        for(int tick=0;tick<120;tick++) {
            level.getServer().getWorldData().overworldData().setGameTime(24100+tick);maid.setPos(0,160,0);maid.setYRot(0);maid.setXRot(0);maid.setDeltaMovement(Vec3.ZERO);maid.setOnGround(true);
            target.setPos(0,160,2);target.setDeltaMovement(Vec3.ZERO);target.invulnerableTime=0;
            maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET,target);
            maid.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,new NearestVisibleLivingEntities(maid,List.of(target)));
            maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            if(attack.tryStart(level,maid,level.getGameTime()))triggers++;
            maid.tick();combos.add(state.getComboSeq().toString());damages.add(1000-target.getHealth());
            // Tick actual spawned slash entities so deferred attacks also execute.
            var effects=level.getEntities(maid,maid.getBoundingBox().inflate(12),e->e instanceof mods.flammpfeil.slashblade.entity.IShootable);
            for(var e:effects){if(!spawned.contains(e))spawned.add(e);if(!e.isRemoved())e.tick();}
            target.invulnerableTime=0;
        }
        report.put("maid_combat",Map.of("accepted_behavior_calls",triggers,"combo_trace",combos,"cumulative_damage_trace",damages,"total_damage",1000-target.getHealth(),"kills",state.getKillCount(),"souls",state.getProudSoulCount(),"refine",state.getRefine(),"wear",maid.getMainHandItem().getDamageValue()));
        check(triggers>0,"actual maid attack behavior accepted its memories");
        check(combos.stream().anyMatch(c->!c.equals("slashblade:none")),"maid entered a registered attack combo");
        check(target.getHealth()<1000,"maid behavior and combo timeline damage real target");
    }
}
