package org.scex.slashbladelegacy.contracts;

import net.minecraftforge.event.TickEvent.RenderTickEvent;

import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import com.google.gson.GsonBuilder;
import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/** Read-only trace around the unchanged old client probe; each side reads only its own world. */
@EventBusSubscriber(modid="slashblade_legacy_contracts",value=Dist.CLIENT)
public final class LegacyTimingTrace {
    private static final Queue<Map<String,Object>> rows=new ConcurrentLinkedQueue<>();
    private static final List<String> missedAssertions=new ArrayList<>();
    private static final long origin=System.nanoTime();
    private static final Map<String,Field> fields=new HashMap<>();
    private static volatile Map<String,Integer> mark=Map.of();
    private static volatile Map<String,Object> serverSnapshot=Map.of(),serverLanding=Map.of();
    private static boolean hitched;
    static {
        try {
            Class.forName("mods.flammpfeil.slashblade.event.SlashBladeEvent$NextComboEvent",false,LegacyTimingTrace.class.getClassLoader());
            NextTrace.register();
        } catch(ClassNotFoundException absent) {
            // Older builds do not publish this event.
        }
    }
    private static boolean enabled(){return Boolean.getBoolean("scex.legacy.clientProbe") && !Boolean.getBoolean("scex.legacy.viewProbe") && !Boolean.getBoolean("scex.legacy.lifecycleProbe");}
    public static boolean observeFailure(String message){
        if(!Boolean.getBoolean("scex.legacy.traceContinue"))return false;
        missedAssertions.add(message);record("assertion_failed",Minecraft.getInstance().player,message);return true;
    }
    public static List<String> failures(){return List.copyOf(missedAssertions);}
    public static Map<String,Object> serverSnapshot(){return serverSnapshot;}
    public static Map<String,Object> serverLanding(){return serverLanding;}
    public static void resetLanding(){serverLanding=Map.of();}
    @SubscribeEvent public static void render(mods.flammpfeil.slashblade.event.client.RenderOverrideEvent event){
        if(!enabled() || !Boolean.getBoolean("scex.legacy.full17Probe") || rows.size()>20000)return;
        var p=Minecraft.getInstance().player;
        if(p==null || event.getStack()!=p.getMainHandItem() || !Set.of("blade","sheath").contains(event.getTarget()))return;
        var row=new LinkedHashMap<String,Object>();row.put("kind","blade_render");row.put("client",true);row.put("mark",mark);
        row.put("world_tick",p.level().getGameTime());row.put("swing",p.attackAnim);row.put("part",event.getTarget());
        row.put("combo",BladeStateAccess.of(p.getMainHandItem()).orElseThrow().getComboSeq().toString());
        row.put("pose",event.getPoseStack().last().pose().get(new float[16]));
        row.put("modelView",com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix().get(new float[16]));rows.add(row);
    }
    @SubscribeEvent public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!enabled() || !Boolean.getBoolean("scex.legacy.frameHitch") || hitched || mark.getOrDefault("phase",0)!=8 || mark.getOrDefault("ticks",0)<2)return;
        hitched=true;record("controlled_frame_hitch_begin",Minecraft.getInstance().player,"400 ms render-thread hitch; server remains running");
        try{Thread.sleep(400);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        record("controlled_frame_hitch_end",Minecraft.getInstance().player,"");
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void client(ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!enabled())return;
        try{
            var m=new LinkedHashMap<String,Integer>();
            for(var name:List.of("phase","ticks","click","bladeIndex")){
                var f=fields.get(name);if(f==null){f=LegacyClientProbe.class.getDeclaredField(name);f.setAccessible(true);fields.put(name,f);}m.put(name,f.getInt(null));
            }
            mark=Map.copyOf(m);record("client_tick",Minecraft.getInstance().player,"");
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void server(ServerTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!enabled())return;
        for(var p:net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers())record("server_tick",p,"");
    }
    private static final class NextTrace {
        static void register() {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,(SlashBladeEvent.NextComboEvent event)->{
                if(enabled())record("next_combo",event.getUser(),event.getNextCombo().toString());
            });
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void use(PlayerInteractEvent.RightClickItem event){
        if(enabled())record("right_click_item",event.getEntity(),event.getHand().toString());
    }
    private static void record(String kind,LivingEntity p,String detail){
        if(p==null || rows.size()>20000)return;
        var m=new LinkedHashMap<String,Object>();m.put("ns",System.nanoTime()-origin);m.put("kind",kind);m.put("client",p.level().isClientSide);m.put("mark",mark);
        m.put("world_tick",p.level().getGameTime());m.put("entity_tick",p.tickCount);m.put("y",p.getY());m.put("vy",p.getDeltaMovement().y);m.put("ground",p.onGround());m.put("detail",detail);
        BladeStateAccess.of(p.getMainHandItem()).ifPresent(s->{m.put("combo",s.getComboSeq().toString());m.put("action_tick",s.getLastActionTime());m.put("root",s.getComboRoot().toString());});
        m.put("commands",p.getCapability(CapabilityInputState.INPUT_STATE).orElseThrow(IllegalStateException::new).getCommands().toString());rows.add(m);
        if(kind.equals("server_tick")){
            serverSnapshot=Map.copyOf(m);
            if("slashblade_legacy_compat:helm_landing".equals(m.get("combo")) && Boolean.TRUE.equals(m.get("ground")))serverLanding=serverSnapshot;
        }
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event){
        if(!enabled())return;
        try{var out=Minecraft.getInstance().gameDirectory.toPath().resolve("verification");Files.createDirectories(out);
            Files.writeString(out.resolve("timing-trace.json"),new GsonBuilder().setPrettyPrinting().create().toJson(rows));
        }catch(Exception e){throw new IllegalStateException(e);}
    }
}
