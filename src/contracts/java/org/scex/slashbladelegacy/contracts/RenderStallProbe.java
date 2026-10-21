package org.scex.slashbladelegacy.contracts;

import com.google.gson.GsonBuilder;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import mods.flammpfeil.slashblade.SlashBladeCreativeGroup;
import mods.flammpfeil.slashblade.capability.slashblade.SlashBladeDataComponents;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.scex.slashbladelegacy.LegacyMode;
import org.scex.slashbladelegacy.contracts.mixin.ProbeCreativeTabInvoker;
import org.scex.slashbladelegacy.contracts.mixin.ProbeUseItemInvoker;

/** Measures the real item renderer. Screenshots run after each measured window. */
@EventBusSubscriber(modid="slashblade_legacy_contracts",value=Dist.CLIENT)
public final class RenderStallProbe {
    private static final String[] BLADES={"slashblade:sange","si_slashblade:legacy/fox_elf","prinegorerouse:chronossy_blade"};
    private static final ThreadMXBean CPU=ManagementFactory.getThreadMXBean();
    private static final com.sun.management.ThreadMXBean ALLOC=CPU instanceof com.sun.management.ThreadMXBean bean?bean:null;
    private static final List<SampleCase> CASES=new ArrayList<>();
    private static final List<String> CAPTURES=new ArrayList<>();
    private static final List<Map<String,Object>> CHECKS=new ArrayList<>();
    private static final Map<String,List<String>> PAGE_CONTENTS=new HashMap<>();
    private static boolean cpuAvailable,allocationAvailable,done,inFrame;
    private static long started,wallStart,cpuStart,allocatedStart;
    private static int stage,ticks,index,mode=-1;
    private static Path out;
    private static SampleCase active;
    private static jdk.jfr.Recording recording;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static volatile Map<String,Object> serverSnapshot=Map.of();
    private static volatile String expectedTranslation="";
    private static Object jeiRuntime,jeiFilter,jeiOverlay,jeiToggles,jeiItemType,jeiContents,jeiPageDelegate;
    private static Method jeiSetFilter,jeiVisible,jeiDisplayed,jeiEnabled,jeiToggle;
    private static Method jeiFirstPage,jeiNextPage,jeiPageCount,jeiPageNumber;
    private static int creativeRow=-1,jeiPageAdvances=-1;

    private static final class SampleCase {
        final int mode,blade,iteration,warmup;
        final String kind;
        final long[][] samples;
        final Map<String,Object> details=new LinkedHashMap<>();
        int frames;
        long timelineCalls,timelineNs;
        SampleCase(int mode,String kind,int blade,int iteration,int warmup){
            this.mode=mode;this.kind=kind;this.blade=blade;this.iteration=iteration;this.warmup=warmup;
            samples=new long[warmup+90][3+RenderStallMetrics.FIELD_NAMES.length];
        }
        String name(){return (mode==0?"legacy":"modern")+"-"+kind+"-"+(blade>=0?blade:iteration);}
        String image(){
            if(kind.equals("held-third"))return name()+".png";
            if(blade==1)return (mode==0?"legacy":"modern")+"-held-1-"+(kind.equals("held-before")?"before":"after")+".png";
            if(iteration==1 && (kind.equals("creative") || kind.equals("jei")))return name()+".png";
            return null;
        }
    }

    public static boolean enabled(){return Boolean.getBoolean("scex.legacy.renderStallProbe");}
    public static boolean measuring(){return enabled() && !done && inFrame;}
    public static boolean stageActive(){return enabled() && !done && active!=null && stage>=5 && stage<=8;}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void write(String name,Object value)throws Exception{Files.writeString(out.resolve(name),new GsonBuilder().setPrettyPrinting().create().toJson(value));}
    private static ServerPlayer player(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();}
    private static void server(Minecraft mc,Runnable task){ready=false;mc.getSingleplayerServer().execute(()->{try{task.run();ready=true;}catch(Throwable e){failure=e;}});}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!enabled() || done)return;
        var mc=Minecraft.getInstance();
        try{
            if(started==0)initialize(mc);
            if(failure!=null)throw new IllegalStateException("Render-stall fixture",failure);
            check(System.nanoTime()-started<240_000_000_000L,"Probe timeout at stage "+stage+", case "+index);
            mc.getToasts().clear();
            if(stage==0 && mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.options.onboardingAccessibilityFinished();mc.setScreen(new TitleScreen());}
            if(stage==0 && mc.screen instanceof TitleScreen && mc.getOverlay()==null){
                stage=1;
                mc.createWorldOpenFlows().createFreshLevel("render-stall-"+System.currentTimeMillis(),
                        new LevelSettings("Isolated blade render diagnosis",GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(20261008L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());
            }else if(stage==1 && mc.player!=null && mc.level!=null && mc.screen==null){
                stage=2;ticks=0;server(mc,()->{
                    var s=mc.getSingleplayerServer();var p=player(mc);var level=p.serverLevel();
                    level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,s);
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,s);
                    long dayTime=level.getDayTime();s.getWorldData().overworldData().setGameTime(480000L);
                    p.connection.send(new ClientboundSetTimePacket(level.getGameTime(),dayTime,false));
                    p.teleportTo(level,12,-60,12,0,0);p.getInventory().clearContent();p.getInventory().selected=0;p.inventoryMenu.broadcastChanges();
                    serverSnapshot=Map.of("game_time",level.getGameTime(),"day_time_before",dayTime,"day_time_after",level.getDayTime());
                });
            }else if(stage==2 && ready && ++ticks>=20 && mc.level.getGameTime()>=480000){
                CHECKS.add(serverSnapshot);mc.options.setCameraType(CameraType.FIRST_PERSON);
                org.lwjgl.glfw.GLFW.glfwShowWindow(mc.getWindow().getWindow());org.lwjgl.glfw.GLFW.glfwFocusWindow(mc.getWindow().getWindow());
                mc.gui.getChat().clearMessages(true);initializeJei();stage=3;
            }else if(stage==3){
                if(index==CASES.size()){finish(mc);return;}
                active=CASES.get(index);
                if(mode!=active.mode){
                    closeScreen(mc);mode=active.mode;stage=4;ticks=0;
                    server(mc,()->{var p=player(mc);p.getInventory().clearContent();p.getPersistentData().putBoolean(LegacyMode.MODERN,mode==1);LegacyMode.bindInventory(p);p.inventoryMenu.broadcastChanges();});
                }else prepareCase(mc);
            }else if(stage==4 && ready && ++ticks>=3){
                LegacyMode.receive(mc.player,mc.player.getUUID(),mode==1);
                check(LegacyMode.modern(mc.player)==(mode==1),"Client mode fixture mismatch");prepareCase(mc);
            }else if(stage==5 && ready){
                if(active.kind.startsWith("held")){
                    var data=mc.player.getMainHandItem().get(SlashBladeDataComponents.BLADE_STATE_DATA.get());
                    if(data==null || !data.translationKey().equals(expectedTranslation)){check(++ticks<100,"Held stack did not synchronize");return;}
                    active.details.put("client_before",stackSnapshot(mc.player.getMainHandItem()));
                    if(active.kind.equals("held-after")){
                        ((ProbeUseItemInvoker)mc).legacyProbe$useItem();stage=6;ticks=0;return;
                    }
                }else if(active.kind.equals("creative") || active.kind.equals("jei")){
                    setJeiEnabled(active.kind.equals("jei"));
                    var screen=new CreativeModeInventoryScreen(mc.player,mc.level.enabledFeatures(),true);mc.setScreen(screen);
                    var tab=active.kind.equals("creative")?SlashBladeCreativeGroup.SLASHBLADE_GROUP.get():BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(CreativeModeTabs.INVENTORY);
                    ((ProbeCreativeTabInvoker)screen).legacyProbe$selectTab(tab);
                    active.details.put("screen",screen.getClass().getName());
                    active.details.put("tab",BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab).toString());
                    if(active.kind.equals("creative")){
                        selectCreativeBladePage(screen);recordPage("visible_slots",visibleCreative(screen));
                    }
                }
                active.details.put("server",serverSnapshot);stage=active.kind.equals("jei")?7:8;ticks=0;
            }else if(stage==6){
                if(++ticks==1){mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);}
                if(ticks>=30){active.details.put("client_after_use",stackSnapshot(mc.player.getMainHandItem()));stage=8;ticks=0;}
            }else if(stage==7){
                // JEI needs the new screen bounds before its page layout is valid.
                if(++ticks>=2 && (boolean)jeiDisplayed.invoke(jeiOverlay) && !visibleJei().isEmpty()){
                    selectJeiBladePage();recordPage("jei_visible",visibleJei());stage=8;
                }else check(ticks<100,"JEI layout did not become ready");
            }else if(stage==8 && active.kind.equals("held-third")){
                var runtime=mc.player.getMainHandItem().get(SlashBladeDataComponents.BLADE_RUNTIME_STATE.get());
                if(runtime!=null && !runtime.comboSeq().getPath().equals("none") && !runtime.comboSeq().getPath().equals("standby"))
                    active.details.put("attack_combo",runtime.comboSeq().toString());
                if(ticks%12==0){
                    ((ProbeUseItemInvoker)mc).legacyProbe$useItem();
                    active.details.put("use_calls",ticks/12+1);
                }else if(ticks%12==1){mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);}
                ticks++;
            }else if(stage==9){
                if(active.image()!=null && !CAPTURES.contains(active.image()))return;
                if(active.kind.equals("jei")){
                    check((boolean)jeiDisplayed.invoke(jeiOverlay),"JEI blade overlay was not visible");recordPage("jei_visible_end",visibleJei());
                }else if(active.kind.equals("creative")){
                    check(mc.screen instanceof CreativeModeInventoryScreen,"Creative blade screen closed during sampling");
                    recordPage("visible_slots_end",visibleCreative((CreativeModeInventoryScreen)mc.screen));
                }
                active.details.put("client_end",stackSnapshot(mc.player.getMainHandItem()));
                active.details.put("timeline_calls",RenderStallMetrics.timelineCalls()-active.timelineCalls);
                active.details.put("timeline_ns",RenderStallMetrics.timelineNanos()-active.timelineNs);
                if(active.kind.equals("held-third")){
                    long layers=Arrays.stream(active.samples).mapToLong(row->row[3+RenderStallMetrics.LAYER_CALLS]).sum();
                    check(layers>0,"Third-person blade layer did not render");
                    check(((Number)active.details.getOrDefault("use_calls",0)).intValue()>1,"Third-person attack input did not run");
                    check(active.details.containsKey("attack_combo"),"Third-person attack did not enter a combo");
                    active.details.put("third_person_layer_calls",layers);
                    mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);
                }
                CHECKS.add(Map.of("case",active.name(),"frames",active.frames,"passed",active.frames==active.samples.length));
                closeScreen(mc);active=null;index++;stage=3;
            }
        }catch(Throwable e){fail(mc,e);}
    }

    private static void initialize(Minecraft mc)throws Exception{
        check(Files.exists(mc.gameDirectory.toPath().resolve("render-stall.flag")),"Disposable render-stall marker missing");
        started=System.nanoTime();out=mc.gameDirectory.toPath().resolve("verification");Files.createDirectories(out);
        mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(30);mc.options.renderDistance().set(3);mc.options.simulationDistance().set(3);mc.options.fov().set(70);
        mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        cpuAvailable=CPU.isCurrentThreadCpuTimeSupported();if(cpuAvailable && !CPU.isThreadCpuTimeEnabled())CPU.setThreadCpuTimeEnabled(true);
        allocationAvailable=ALLOC!=null && ALLOC.isThreadAllocatedMemorySupported();if(allocationAvailable && !ALLOC.isThreadAllocatedMemoryEnabled())ALLOC.setThreadAllocatedMemoryEnabled(true);
        RenderStallMetrics.configureCpuTimer();
        for(int m=0;m<2;m++){
            CASES.add(new SampleCase(m,"empty",-1,0,30));
            for(int b=0;b<BLADES.length;b++){CASES.add(new SampleCase(m,"held-before",b,0,0));CASES.add(new SampleCase(m,"held-after",b,0,0));}
            CASES.add(new SampleCase(m,"held-third",1,0,0));
            for(int n=1;n<=3;n++)CASES.add(new SampleCase(m,"creative",-1,n,30));
            for(int n=1;n<=3;n++)CASES.add(new SampleCase(m,"jei",-1,n,30));
        }
    }

    private static void prepareCase(Minecraft mc)throws Exception{
        if(recording==null){
            recording=new jdk.jfr.Recording();recording.setName("Blade render diagnosis");recording.setMaxSize(32L*1024*1024);
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(5));
            recording.enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(5));recording.start();
        }
        closeScreen(mc);setJeiEnabled(false);mc.options.setCameraType(active.kind.equals("held-third")?CameraType.THIRD_PERSON_BACK:CameraType.FIRST_PERSON);
        active.details.put("camera",mc.options.getCameraType().name());
        active.timelineCalls=RenderStallMetrics.timelineCalls();active.timelineNs=RenderStallMetrics.timelineNanos();
        stage=5;ticks=0;
        server(mc,()->{
            var p=player(mc);var row=new LinkedHashMap<String,Object>();row.put("modern",LegacyMode.modern(p));row.put("game_time",p.level().getGameTime());
            if(active.kind.equals("held-before") || active.kind.equals("held-third")){
                var definition=p.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements().filter(h->h.key().location().toString().equals(BLADES[active.blade])).findFirst().orElseThrow();
                var blade=definition.value().getBlade(p.registryAccess());
                row.put("definition_stack",stackSnapshot(blade));LegacyMode.bind(blade,p);row.put("after_mode_bind",stackSnapshot(blade));
                expectedTranslation=blade.get(SlashBladeDataComponents.BLADE_STATE_DATA.get()).translationKey();
                p.setItemInHand(InteractionHand.MAIN_HAND,blade);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            }else if(!active.kind.equals("held-after")){p.getInventory().clearContent();}
            p.inventoryMenu.broadcastChanges();row.put("held",stackSnapshot(p.getMainHandItem()));serverSnapshot=row;
        });
    }

    /** Direct component reads preserve missing runtime data on a new stack. */
    private static Map<String,Object> stackSnapshot(ItemStack stack){
        var row=new LinkedHashMap<String,Object>();row.put("item",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());row.put("count",stack.getCount());
        var data=stack.get(SlashBladeDataComponents.BLADE_STATE_DATA.get());var runtime=stack.get(SlashBladeDataComponents.BLADE_RUNTIME_STATE.get());
        row.put("persistent_present",data!=null);row.put("runtime_present",runtime!=null);
        if(data!=null){row.put("translation_key",data.translationKey());row.put("model",data.model().map(Object::toString).orElse("default"));row.put("texture",data.texture().map(Object::toString).orElse("default"));}
        if(runtime!=null){row.put("combo",runtime.comboSeq().toString());row.put("last_action_time",runtime.lastActionTime());row.put("last_processed_tick",runtime.lastProcessedComboTick());}
        return row;
    }

    private static void initializeJei()throws Exception{
        Class<?> internal=Class.forName("mezz.jei.common.Internal"),runtime=Class.forName("mezz.jei.api.runtime.IJeiRuntime");
        jeiRuntime=internal.getMethod("getJeiRuntime").invoke(null);check(jeiRuntime!=null,"JEI runtime unavailable");
        jeiFilter=runtime.getMethod("getIngredientFilter").invoke(jeiRuntime);jeiOverlay=runtime.getMethod("getIngredientListOverlay").invoke(jeiRuntime);
        jeiSetFilter=Class.forName("mezz.jei.api.runtime.IIngredientFilter").getMethod("setFilterText",String.class);jeiSetFilter.invoke(jeiFilter,"@slashblade");
        Class<?> overlay=Class.forName("mezz.jei.api.runtime.IIngredientListOverlay");jeiDisplayed=overlay.getMethod("isListDisplayed");
        jeiVisible=overlay.getMethod("getVisibleIngredients",Class.forName("mezz.jei.api.ingredients.IIngredientType"));
        jeiItemType=Class.forName("mezz.jei.api.constants.VanillaTypes").getField("ITEM_STACK").get(null);
        jeiToggles=internal.getMethod("getClientToggleState").invoke(null);
        Class<?> toggle=Class.forName("mezz.jei.common.config.IClientToggleState");jeiEnabled=toggle.getMethod("isOverlayEnabled");jeiToggle=toggle.getMethod("toggleOverlayEnabled");
        var contentsField=Class.forName("mezz.jei.gui.overlay.IngredientListOverlay").getDeclaredField("contents");contentsField.setAccessible(true);jeiContents=contentsField.get(jeiOverlay);
        jeiFirstPage=jeiContents.getClass().getMethod("updateLayoutToFirstPage");
        jeiPageDelegate=jeiContents.getClass().getMethod("getPageDelegate").invoke(jeiContents);
        Class<?> paged=Class.forName("mezz.jei.gui.input.IPaged");
        jeiNextPage=paged.getMethod("nextPage");jeiPageCount=paged.getMethod("getPageCount");jeiPageNumber=paged.getMethod("getPageNumber");
        setJeiEnabled(false);
    }

    private static List<ItemStack> visibleCreative(CreativeModeInventoryScreen screen){
        return screen.getMenu().slots.stream().limit(45).filter(s->s.hasItem()).map(s->s.getItem()).toList();
    }
    private static int pageScore(List<ItemStack> items){
        int blades=0;boolean fox=false,chronossy=false;
        for(var stack:items){
            if(!(stack.getItem() instanceof ItemSlashBlade))continue;
            blades++;var data=stack.get(SlashBladeDataComponents.BLADE_STATE_DATA.get());if(data==null)continue;
            fox|=data.translationKey().contains("fox_elf");chronossy|=data.translationKey().contains("chronossy");
        }
        return blades<15?-1:blades+(fox?1000:0)+(chronossy?1000:0);
    }
    private static void selectCreativeBladePage(CreativeModeInventoryScreen screen){
        var items=screen.getMenu().items;int rows=Math.max(0,(items.size()+8)/9-5);
        if(creativeRow<0){
            int best=-1;
            for(int row=0;row<=rows;row++){
                int from=row*9;int score=pageScore(items.subList(from,Math.min(items.size(),from+45)));
                if(score>best){best=score;creativeRow=row;}
            }
            check(creativeRow>=0,"Creative tab has no natural page with at least 15 blades");
        }
        // The normal wheel handler updates both scrollOffs and ItemPickerMenu.scrollTo.
        if(creativeRow>0)check(screen.mouseScrolled(0,0,0,-creativeRow),"Creative scrolling was rejected");
        active.details.put("creative_row",creativeRow);active.details.put("creative_total_items",items.size());
        active.details.put("page_entry","CreativeModeInventoryScreen.mouseScrolled -> ItemPickerMenu.scrollTo");
    }
    private static void selectJeiBladePage()throws Exception{
        jeiFirstPage.invoke(jeiContents);int pages=(int)jeiPageCount.invoke(jeiPageDelegate);
        check(pages>0,"JEI has no pages");
        if(jeiPageAdvances<0){
            int best=-1;
            for(int page=0;page<pages;page++){
                int score=pageScore(visibleJei());if(score>best){best=score;jeiPageAdvances=page;}
                if(page+1<pages)check((boolean)jeiNextPage.invoke(jeiPageDelegate),"JEI page scan stopped early");
            }
            check(jeiPageAdvances>=0,"JEI has no natural page with at least 15 blades");
        }
        jeiFirstPage.invoke(jeiContents);
        for(int page=0;page<jeiPageAdvances;page++)check((boolean)jeiNextPage.invoke(jeiPageDelegate),"JEI selected page is unavailable");
        active.details.put("jei_page_number",jeiPageNumber.invoke(jeiPageDelegate));active.details.put("jei_page_count",pages);
        active.details.put("jei_filter","@slashblade");active.details.put("page_entry","IngredientGridWithNavigation.getPageDelegate -> IPaged.nextPage");
    }
    private static void recordPage(String field,List<ItemStack> items){
        int blades=0;var names=new ArrayList<String>();var signature=new ArrayList<String>();var snapshots=new ArrayList<Map<String,Object>>();
        for(var stack:items){
            if(stack.getItem() instanceof ItemSlashBlade)blades++;
            var snapshot=stackSnapshot(stack);String name=stack.getHoverName().getString();snapshot.put("display_name",name);names.add(name);snapshots.add(snapshot);
            signature.add(snapshot.get("item")+"|"+snapshot.getOrDefault("translation_key","")+"|"+name);
        }
        check(blades>=15,active.kind+" page contains only "+blades+" blades");
        var expected=PAGE_CONTENTS.putIfAbsent(active.kind,List.copyOf(signature));
        check(expected==null || expected.equals(signature),active.kind+" visible page changed between samples");
        active.details.put(field,snapshots);active.details.put("visible_blade_count",blades);active.details.put("visible_names",names);
        active.details.put("page_content_matches",true);
    }
    private static void setJeiEnabled(boolean value)throws Exception{if(jeiToggles!=null && (boolean)jeiEnabled.invoke(jeiToggles)!=value)jeiToggle.invoke(jeiToggles);}
    @SuppressWarnings("unchecked") private static List<ItemStack> visibleJei()throws Exception{return (List<ItemStack>)jeiVisible.invoke(jeiOverlay,jeiItemType);}
    private static void closeScreen(Minecraft mc){if(mc.screen!=null)mc.player.closeContainer();}

    @SubscribeEvent public static void pre(RenderFrameEvent.Pre event){
        if(!enabled() || done || stage!=8)return;
        if(active.frames==0)active.details.put("render_start_epoch_ms",System.currentTimeMillis());
        RenderStallMetrics.resetFrame();allocatedStart=allocationAvailable?ALLOC.getThreadAllocatedBytes(Thread.currentThread().threadId()):-1;
        cpuStart=cpuAvailable?CPU.getCurrentThreadCpuTime():-1;wallStart=System.nanoTime();inFrame=true;
    }
    @SubscribeEvent public static void post(RenderFrameEvent.Post event){
        if(!enabled() || done)return;
        if(inFrame){
            long wall=System.nanoTime()-wallStart,cpu=cpuAvailable?CPU.getCurrentThreadCpuTime()-cpuStart:-1;
            long bytes=allocationAvailable?ALLOC.getThreadAllocatedBytes(Thread.currentThread().threadId())-allocatedStart:-1;inFrame=false;
            var row=active.samples[active.frames++];row[0]=wall;row[1]=cpu;row[2]=bytes;RenderStallMetrics.copyFrame(row,3);
            if(active.frames==active.samples.length){active.details.put("render_end_epoch_ms",System.currentTimeMillis());stage=9;}
            return;
        }
        if(stage==9 && active.image()!=null && !CAPTURES.contains(active.image())){
            try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(out.resolve(active.image()));CAPTURES.add(active.image());}
            catch(Exception e){fail(Minecraft.getInstance(),e);}
        }
    }

    private static void finish(Minecraft mc)throws Exception{
        var fields=new ArrayList<>(List.of("render_wall_ns","render_thread_cpu_ns","render_thread_allocated_bytes"));fields.addAll(Arrays.asList(RenderStallMetrics.FIELD_NAMES));
        var rows=new ArrayList<Map<String,Object>>();
        for(var sample:CASES){
            check(sample.frames==sample.samples.length,"Missing frames: "+sample.name());
            var row=new LinkedHashMap<String,Object>();row.put("case",sample.name());row.put("modern",sample.mode==1);row.put("kind",sample.kind);
            row.put("blade",sample.blade<0?"none":BLADES[sample.blade]);row.put("warmup_frames",sample.warmup);row.put("measured_frames",90);
            row.put("samples",sample.samples);row.put("details",sample.details);rows.add(row);
        }
        write("metrics.json",Map.of("fields",fields,"cases",rows,"cpu_available",cpuAvailable,"allocation_available",allocationAvailable,
                "boundary","RenderFrameEvent Pre/Post: gameRenderer.render only; excludes swap and FPS limiter",
                "order","Legacy first: empty, sange, high-face fox_elf, high-texture chronossy, third-person attacks, creative blade page x3, JEI blade page x3; modern repeats with warm resource caches. Natural pages require at least 15 blades and identical visible names across samples.",
                "instrumentation","Both inputs use the same counters and method wrappers. The first OBJ sample includes one topology count. Filter timing measures CPU submission, not GPU time. Screenshots and JSON writes are outside measured windows."));
        write("checks.json",CHECKS);
        stopRecording();
        ObjVertexProbe.run(out.resolve("vertex-equivalence.json"));
        write("result.json",Map.of("passed",true,"checks_passed",true,"status","captured","screenshots",CAPTURES.size(),"captures",CAPTURES,
                "renderer",com.mojang.blaze3d.platform.GlUtil.getRenderer(),"case_count",CASES.size(),"jei_verified",true,
                "scope","Real creative blade tab, JEI inventory overlay, new held blade before/after use; render CPU and allocation diagnosis, no GPU time or FPS claim"));
        done=true;mc.stop();
    }
    private static void fail(Minecraft mc,Throwable error){
        done=true;inFrame=false;
        try{write("failure.json",Map.of("error",error.toString(),"stage",stage,"case_index",index,"case",active==null?"none":active.name(),"checks",CHECKS));}catch(Exception ignored){}
        try{stopRecording();}catch(Exception ignored){}
        com.mojang.logging.LogUtils.getLogger().error("RENDER_STALL_PROBE_FAILED",error);mc.stop();
    }
    private static void stopRecording()throws Exception{if(recording!=null){try{recording.stop();recording.dump(out.resolve("render-stall.jfr"));}finally{recording.close();recording=null;}}}
}
