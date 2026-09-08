package org.scex.slashbladelegacy.contracts;

import java.util.*;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.*;
import org.scex.slashbladelegacy.*;

/** Nine core arts via actual right-button release, plus two addon SA and a labeled Super render fixture. */
final class LegacyArtsClientProbe {
    static boolean enabled(){return Boolean.getBoolean("scex.legacy.full17ArtsProbe");}
    static final int COUNT=12;
    static final int SCREENSHOTS=COUNT+3;
    private static final AABB AREA=new AABB(-20,40,-20,80,120,100);
    private static int test,stage,tick;
    private static volatile boolean prepared,pending,finished;
    private static volatile Throwable failure;
    private static volatile Map<String,Object> snapshot=Map.of();
    private static final List<Map<String,Object>> cases=new ArrayList<>();
    private static UUID targetId;
    private static int elapsed;
    private static long energyBefore;
    private static java.util.function.Consumer<mods.flammpfeil.slashblade.event.SlashBladeEvent.PerformSlashArtEvent> observer;
    static Map<String,Object> report(){return Map.of("cases",List.copyOf(cases),"last_snapshot",snapshot,"case",test,"stage",stage);}
    static String imageName(int n){return "07-arts-"+n+".png";}
    static void release(Minecraft mc){mc.options.keyUse.setDown(false);mc.options.keyShift.setDown(false);mc.options.keyUp.setDown(false);mc.options.keyDown.setDown(false);mc.setCameraEntity(mc.player);mc.options.hideGui=false;}
    private static String bladeId(){return test==9?"prinegorerouse:aeon_blade":test==10?"si_slashblade:legacy/fox_faerie":"slashblade:sange";}
    private static void clear(ServerPlayer player){for(var e:player.level().getEntities(player,AREA,e->!(e instanceof net.minecraft.world.entity.player.Player)))e.discard();}
    static boolean tick(Minecraft mc,java.util.function.Consumer<String> capture) {
        if(failure!=null)throw new IllegalStateException("r87 client arts "+test+" / "+stage,failure);
        if(test>=COUNT)return finished;
        if(++tick>160)throw new IllegalStateException("r87 arts timeout: "+test+" / "+stage+" "+snapshot);
        if(stage==0) {
            release(mc);stage=1;prepared=false;snapshot=Map.of();elapsed=0;energyBefore=0;
            mc.getSingleplayerServer().execute(()->{try {
                var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayers().getFirst();clear(p);
                if(observer==null){observer=e->{if(e.getEntityLiving()==p)elapsed=e.getElapsed();};net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(observer);}
                for(int x=4;x<=30;x++)for(int z=5;z<=45;z++)p.level().setBlockAndUpdate(new BlockPos(x,70,z),net.minecraft.world.level.block.Blocks.SMOOTH_QUARTZ.defaultBlockState());
                p.teleportTo(p.serverLevel(),12,71,12,0,0);p.setDeltaMovement(Vec3.ZERO);p.setOnGround(true);p.fallDistance=0;p.removeAllEffects();p.setHealth(p.getMaxHealth());p.experienceLevel=300;
                var definition=server.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).getOrThrow(ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY,ResourceLocation.parse(bladeId())));
                var blade=definition.value().getBlade(server.registryAccess());blade.enchant(p.registryAccess().holderOrThrow(Enchantments.POWER),3);blade.setDamageValue(1);
                var state=BladeStateAccess.of(blade).orElseThrow();state.setBroken(false);state.setSealed(false);state.setProudSoulCount(1000);state.setComboSeq(ComboStateRegistry.NONE.getId());
                if(test<9)state.setSlashArtsKey(LegacyArts.key(LegacyArts.Art.values()[test]));else if(test==11)state.setSlashArtsKey(LegacyArts.key(LegacyArts.Art.DIMENSION));
                p.setItemInHand(InteractionHand.MAIN_HAND,blade);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                if(test==10){var type=blade.getItem().getClass();energyBefore=(long)type.getMethod("getMaxEnergy").invoke(blade.getItem());type.getMethod("setEnergy",ItemStack.class,long.class).invoke(blade.getItem(),blade,energyBefore);}
                var rank=p.getData(CapabilityConcentrationRank.RANK_POINT);rank.setRawRankPoint(0);rank.setLastUpdte(p.level().getGameTime());
                var target=EntityType.HUSK.create(p.level());target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.setHealth(1000);
                target.setPos(12,71,test==4?24:22);target.setNoAi(true);target.setPersistenceRequired();p.level().addFreshEntity(target);targetId=target.getUUID();state.setTargetEntityId(target);p.inventoryMenu.broadcastChanges();prepared=true;
            }catch(Throwable e){failure=e;}});
        }else if(stage==1 && prepared && tick>=20) {
            mc.options.setCameraType(test==4 || test==5 || test==7 || test==8?CameraType.THIRD_PERSON_BACK:CameraType.FIRST_PERSON);stage=2;tick=0;
        }else if(stage==2) {
            if(tick==1) {
                if(test==11)mc.getSingleplayerServer().execute(()->{try{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var state=BladeStateAccess.of(p.getMainHandItem()).orElseThrow();state.updateComboSeq(p,state.getSlashArts().doArts(mods.flammpfeil.slashblade.slasharts.SlashArts.ArtsType.Super,p));}catch(Throwable e){failure=e;}});
                else{mc.options.keyUse.setDown(true);KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(1));}
            }
            if(test!=11 && tick==26)mc.options.keyUse.setDown(false);
            if((test==11 || tick>=26) && !pending) {
                pending=true;mc.getSingleplayerServer().execute(()->{try{
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var state=BladeStateAccess.of(p.getMainHandItem()).orElseThrow();var entities=p.level().getEntities(p,AREA,e->!(e instanceof LivingEntity));
                    var types=entities.stream().map(e->net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString()).toList();
                    var row=new LinkedHashMap<String,Object>();row.put("blade",bladeId());row.put("sa",state.getSlashArtsKey().toString());row.put("combo",state.getComboSeq().toString());row.put("pose",LegacyCombat.visualMove(state.getComboSeq()).name());row.put("elapsed",elapsed);row.put("souls",state.getProudSoulCount());row.put("damage",p.getMainHandItem().getDamageValue());row.put("types",types);row.put("uuids",entities.stream().map(e->e.getUUID().toString()).toList());
                    var target=p.serverLevel().getEntity(targetId);row.put("health",target instanceof LivingEntity living?living.getHealth():0);row.put("player_pos",p.position().toString());
                    row.put("fields",entities.stream().filter(e->e instanceof LegacyArtEntity a && a.mode()==LegacyArtEntity.Mode.DIMENSION).count());
                    row.put("modes",entities.stream().filter(e->e instanceof LegacyArtEntity).map(e->((LegacyArtEntity)e).mode().name()).toList());
                    if(test==10){row.put("energy_before",energyBefore);row.put("energy_after",p.getMainHandItem().getItem().getClass().getMethod("getEnergy",ItemStack.class).invoke(p.getMainHandItem().getItem(),p.getMainHandItem()));}
                    snapshot=row;
                }catch(Throwable e){failure=e;}finally{pending=false;}});
            }
            if(snapshot.isEmpty())return false;
            var ids=(List<?>)snapshot.get("uuids");int visible=0;
            for(var e:mc.level.entitiesForRendering())if(ids.contains(e.getUUID().toString()))visible++;
            boolean paid=test==10?snapshot.containsKey("energy_after") && ((Number)snapshot.get("energy_after")).longValue()<((Number)snapshot.get("energy_before")).longValue():((Number)snapshot.get("souls")).intValue()<1000;
            boolean started=test==11?((Number)snapshot.get("fields")).intValue()>=2:((Number)snapshot.get("elapsed")).intValue()>15 && paid;
            boolean synchronizedPose=BladeStateAccess.of(mc.player.getMainHandItem()).orElseThrow().getComboSeq().toString().equals(snapshot.get("combo"));
            if(started && visible>0 && synchronizedPose && (test!=11 || ((Number)snapshot.get("health")).floatValue()<1000)) {
                var row=new LinkedHashMap<String,Object>(snapshot);row.put("client_entities",visible);row.put("client_combo",BladeStateAccess.of(mc.player.getMainHandItem()).orElseThrow().getComboSeq().toString());row.put("image",imageName(test));row.put("entry",test==11?"registered Super combo render fixture":"actual right-button press and release");cases.add(row);capture.accept(imageName(test));stage=3;tick=0;
            }
        }else if(stage==3) {
            if(!LegacyClientProbe.captured(imageName(test))){tick=0;return false;}
            if(test>=1 && test<=3) {
                if(tick==1){var camera=EntityType.ARMOR_STAND.create(mc.level);camera.setPos(16,71,8);camera.setYRot(35);camera.setYHeadRot(35);camera.yHeadRotO=35;camera.setXRot(10);camera.setOldPosAndRot();mc.setCameraEntity(camera);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.hideGui=true;}
                if(tick==2){capture.accept("07-arts-"+test+"-flight.png");var row=cases.getLast();row.put("flight_entry","client-only side camera; projectile and player simulation unchanged");row.put("flight_entities",java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).filter(e->e instanceof LegacyDrive).map(e->Map.of("uuid",e.getUUID().toString(),"position",e.position().toString(),"age",((LegacyDrive)e).age(),"roll",((LegacyDrive)e).getRotationRoll())).toList());}
            }
            if(test>=1 && test<=3 && tick>=8 && !LegacyClientProbe.captured("07-arts-"+test+"-flight.png"))return false;
            if(tick>=8){release(mc);stage=4;tick=0;}
        }
        else if(stage==4 && tick>=6) {
            if(++test<COUNT){stage=0;tick=0;}
            else mc.getSingleplayerServer().execute(()->{try{clear(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst());net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(observer);}catch(Throwable e){failure=e;}finally{finished=true;}});
        }
        return false;
    }
}

