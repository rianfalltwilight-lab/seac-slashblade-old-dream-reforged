package org.scex.slashbladelegacy.contracts;

import net.minecraftforge.event.TickEvent.ClientTickEvent;

import net.minecraftforge.event.TickEvent.RenderTickEvent;

import java.nio.file.*;
import java.util.*;
import com.google.gson.GsonBuilder;
import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;
import org.scex.slashbladelegacy.*;

/** Stationary render fixtures, not input/timing acceptance. Production entities and renderers are used. */
@EventBusSubscriber(modid="slashblade_legacy_contracts",value=Dist.CLIENT)
public final class FeedbackVisualProbe {
    private static final List<String> IMAGES=List.of("00-target-reference.png","01-dimension-target-first.png",
            "02-dimension-art-first.png","03-water-horizontal.png","04-gale-red.png","05-terra-green.png");
    private static final List<Entity> FIXTURE=new ArrayList<>(); // integrated-server thread only
    private static final List<Map<String,Object>> rows=new ArrayList<>();
    private static final Set<String> captures=new LinkedHashSet<>();
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static volatile Map<String,Object> serverRow=Map.of();
    private static volatile List<Integer> expected=List.of();
    private static volatile int targetId=-1;
    private static int stage,ticks,test,targetFrames;
    private static long started;
    private static boolean done;
    private static Path out;
    private static String capture;
    private static boolean enabled(){return Boolean.getBoolean("scex.legacy.feedbackVisual");}
    private static boolean driveOnly(){return Boolean.getBoolean("scex.legacy.driveVisual");}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void write(String file,Object value)throws Exception{Files.writeString(out.resolve(file),new GsonBuilder().setPrettyPrinting().create().toJson(value));}
    private static ServerPlayer player(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);}
    private static void server(Minecraft mc,Runnable action){ready=false;mc.getSingleplayerServer().execute(()->{try{action.run();ready=true;}catch(Throwable e){failure=e;}});}

    @SubscribeEvent public static void tick(ClientTickEvent event) {if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!enabled() || done)return;var mc=Minecraft.getInstance();
        try {
            if(started==0){
                check(Files.exists(mc.gameDirectory.toPath().resolve(driveOnly()?"drive-visual.flag":"feedback-visual.flag")),"Disposable visual-run marker missing");
                started=System.nanoTime();out=mc.gameDirectory.toPath().resolve("verification");Files.createDirectories(out);
                mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(30);mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            }
            if(failure!=null)throw new IllegalStateException("Server render fixture",failure);
            check(System.nanoTime()-started<200_000_000_000L,"Visual fixture timeout at "+stage+" / "+test);
            mc.getToasts().clear();
            if(stage==0 && mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen());}
            if(stage==0 && mc.screen instanceof TitleScreen && mc.getOverlay()==null) {
                stage=1;mc.createWorldOpenFlows().createFreshLevel("feedback-visual-"+System.currentTimeMillis(),
                        new LevelSettings("Isolated feedback render fixtures",GameType.CREATIVE,false,net.minecraft.world.Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(20261008,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            } else if(stage==1 && mc.player!=null && mc.level!=null && mc.screen==null) {
                stage=2;ticks=0;server(mc,()->{
                    var s=mc.getSingleplayerServer();var p=player(mc);var level=p.serverLevel();
                    level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,s);
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,s);
                    for(int x=3;x<=29;x++)for(int z=3;z<=34;z++)level.setBlockAndUpdate(new BlockPos(x,70,z),((x+z)%2==0?Blocks.SMOOTH_STONE:Blocks.STONE).defaultBlockState());
                    for(int x=3;x<=29;x++)for(int y=71;y<=78;y++)level.setBlockAndUpdate(new BlockPos(x,y,30),Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
                    p.teleportTo(level,12,71,12,0,0);p.setDeltaMovement(Vec3.ZERO);p.getAbilities().flying=false;p.onUpdateAbilities();
                    var blade=new ItemStack(SlashBladeItems.SLASHBLADE.get());var state=BladeStateAccess.of(blade).orElseThrow();
                    state.setProudSoulCount(10000);state.setBroken(false);state.setSealed(false);state.setSpecialEffects(new net.minecraft.nbt.ListTag());
                    p.setItemInHand(InteractionHand.MAIN_HAND,blade);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);LegacyMode.bindInventory(p);p.inventoryMenu.broadcastChanges();
                });
            } else if(stage==2 && ready && ++ticks>=20) {
                if(!Boolean.getBoolean("scex.legacy.backgroundProbe")) {
                    org.lwjgl.glfw.GLFW.glfwShowWindow(mc.getWindow().getWindow());
                    org.lwjgl.glfw.GLFW.glfwFocusWindow(mc.getWindow().getWindow());
                }
                mc.options.hideGui=true;mc.options.setCameraType(CameraType.FIRST_PERSON);mc.gui.getChat().clearMessages(true);
                stage=3;ticks=0;
            } else if(stage==3) {
                targetFrames=0;stage=4;ticks=0;server(mc,()->fixture(mc,test));
            } else if(stage==4 && ready) {
                if(++ticks==1)camera(mc,test==4?new Vec3(12,74,5):new Vec3(15,75,12),test==4?new Vec3(12,72,13):new Vec3(12,72,22));
                boolean synchronizedEntities=expected.stream().allMatch(id->mc.level.getEntity(id)!=null);
                if(ticks>=8 && synchronizedEntities) {
                    if(targetId>=0){var target=mc.level.getEntity(targetId);check(target instanceof LivingEntity living && living.isAlive() && !target.isInvisible(),"Target absent/invisible on client");}
                    capture=IMAGES.get(test);stage=5;
                }
                check(ticks<100,"Fixture entities did not reach client: "+expected);
            } else if(stage==5 && capture==null) {
                var row=new LinkedHashMap<String,Object>(serverRow);row.put("image",IMAGES.get(test));row.put("target_render_callbacks",targetFrames);
                row.put("client_entities",expected.stream().map(id->{var entity=mc.level.getEntity(id);return entity==null?Map.of("id",id,"present",false):Map.of("id",id,"present",true,"type",entity.getType().toString(),"position",entity.position().toString());}).toList());
                if(targetId>=0)check(targetFrames>0,"Target renderer did not run for case "+test);
                rows.add(row);
                if(++test<(driveOnly()?3:IMAGES.size()))stage=3;
                else {
                    if(driveOnly())captures.addAll(DriveRenderProbe.run(out));
                    write("checks.json",rows);
                    write("result.json",Map.of("passed",true,"status","captured","checks_passed",true,"screenshots",captures.size(),"captures",captures,
                            "renderer",com.mojang.blaze3d.platform.GlUtil.getRenderer(),"scope",driveOnly()
                                    ?"Three dimension target fixtures, eight GPU Drive draw-order fixtures, and five held trail color images; no input, gameplay, or shader-pack acceptance"
                                    :"Server render fixtures with extended lifespan/wait and stationary water; no input, timing, or gameplay acceptance"));
                    done=true;mc.setCameraEntity(mc.player);mc.options.hideGui=false;mc.stop();
                }
            }
        }catch(Throwable e){done=true;try{write("failure.json",Map.of("error",e.toString(),"stage",stage,"test",test,"server",serverRow,"checks",rows));}catch(Exception ignored){}
            com.mojang.logging.LogUtils.getLogger().error("FEEDBACK_VISUAL_FAILED",e);mc.stop();}
    }

    private static void camera(Minecraft mc,Vec3 position,Vec3 target) {
        var camera=EntityType.ARMOR_STAND.create(mc.level);camera.setPos(position);
        Vec3 look=target.subtract(position.add(0,camera.getEyeHeight(),0));
        float yaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z)),pitch=(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance()));
        camera.setYRot(yaw);camera.setYHeadRot(yaw);camera.yHeadRotO=yaw;camera.setXRot(pitch);camera.setOldPosAndRot();mc.setCameraEntity(camera);
    }
    private static net.minecraft.world.entity.monster.Husk target(ServerPlayer p) {
        var target=EntityType.HUSK.create(p.level());target.setPos(12,71,22);target.setYRot(180);target.setYHeadRot(180);
        target.setNoAi(true);target.setNoGravity(true);target.setInvulnerable(true);target.setPersistenceRequired();p.level().addFreshEntity(target);FIXTURE.add(target);return target;
    }
    private static void fixture(Minecraft mc,int scenario) {
        for(var entity:FIXTURE)entity.discard();FIXTURE.clear();targetId=-1;
        var p=player(mc);var blade=p.getMainHandItem();var level=p.serverLevel();
        p.teleportTo(level,12,71,12,0,0);p.setDeltaMovement(Vec3.ZERO);
        var row=new LinkedHashMap<String,Object>();row.put("case",scenario);row.put("entry","explicit server render fixture");
        if(scenario<=2) {
            if(scenario==2)FIXTURE.add(LegacyArtEntity.spawn(p,blade,LegacyArtEntity.Mode.DIMENSION,new Vec3(12,72,22),150,0,false));
            var target=target(p);targetId=target.getId();
            if(scenario==1)FIXTURE.add(LegacyArtEntity.spawn(p,blade,LegacyArtEntity.Mode.DIMENSION,new Vec3(12,72,22),150,0,false));
            row.put("spawn_order",scenario==0?"target only":scenario==1?"target then field":"field then target");
        } else if(scenario==3) {
            var water=LegacyArts.water(p,blade);water.setDeltaMovement(Vec3.ZERO);water.setLifetime(200);water.setPos(12,72,22);FIXTURE.add(water);
            row.put("fixture_changes","water movement stopped and lifetime extended for geometry capture");row.put("water",water.water());row.put("roll",water.getRotationRoll());row.put("color",water.getColor());
        } else if(scenario==4) {
            try {
                Class.forName("cn.mmf.slashblade_addon.specialattacks.GaleSwords").getMethod("doSlash",LivingEntity.class,boolean.class,double.class,float.class).invoke(null,p,false,1d,1f);
                var swords=level.getEntitiesOfClass(EntityAbstractSummonedSword.class,p.getBoundingBox().inflate(8));int n=0;
                for(var sword:swords) {
                    if(sword.getOwner()!=p)continue;
                    sword.setDelay(200);var offset=new Vec3((n%2==0?1:-1)*(1+.5*(n/2)),1.2+.35*(n/2),2);
                    sword.getClass().getMethod("setOffset",Vec3.class).invoke(sword,offset);sword.setPos(p.position().add(offset));FIXTURE.add(sword);n++;
                }
                check(n>0,"Gale renderer fixture missing");row.put("fixture_changes","native Gale SA spawn; wait extended and symmetric display offsets");
            }catch(ReflectiveOperationException e){throw new IllegalStateException("Installed Gale SA",e);}
        } else {
            var target=target(p);targetId=target.getId();
            for(int i=0;i<6;i++) {
                var sword=new LegacyPhantomSword(SummonedBladeMode.SWORD.get(),level);sword.initializeTerra(p,LegacyRangeAttack.sourceId(blade),i,1,target);
                var saved=new CompoundTag();sword.addAdditionalSaveData(saved);saved.putInt("LegacyInterval",200);saved.putInt("LegacyLifetime",240);sword.readAdditionalSaveData(saved);
                level.addFreshEntity(sword);FIXTURE.add(sword);
            }
            row.put("fixture_changes","actual Terra initialization; wait extended via existing save fields");
        }
        expected=FIXTURE.stream().map(Entity::getId).toList();row.put("entity_ids_in_creation_order",expected);
        row.put("projectile_colors",FIXTURE.stream().filter(EntityAbstractSummonedSword.class::isInstance).map(e->((EntityAbstractSummonedSword)e).getColor()).toList());
        serverRow=row;
    }
    @SubscribeEvent public static void targetFrame(RenderLivingEvent.Post<?,?> event){if(enabled() && !done && event.getEntity().getId()==targetId)targetFrames++;}
    @SubscribeEvent public static void frame(RenderTickEvent event) {if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!enabled() || done || capture==null)return;
        try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(out.resolve(capture));captures.add(capture);capture=null;}
        catch(Exception e){failure=e;}
    }
}
