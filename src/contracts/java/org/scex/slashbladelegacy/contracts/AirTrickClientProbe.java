package org.scex.slashbladelegacy.contracts;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.ability.SlayerStyleArts;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.client.SlashBladeKeyMappings;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.lwjgl.glfw.GLFW;
import org.scex.slashbladelegacy.LegacyMode;
import org.scex.slashbladelegacy.LegacyPhantomSword;
import org.scex.slashbladelegacy.client.LegacyKeyMappings;
import org.scex.slashbladelegacy.contracts.mixin.ProbeKeyboardInvoker;

/** 通过实际键盘入口验证隔空瞬步。服务端观察器只读状态。 */
final class AirTrickClientProbe {
    static final int SCREENSHOTS=5;
    private static final List<String> NAMES=List.of("empty-default","empty-rebound","locked-impact","no-power");
    private static final List<Map<String,Object>> rows=new ArrayList<>();
    private static int test,stage,tick,clientEntities;
    private static boolean observing,restored;
    private static InputConstants.Key oldLock,oldForward,oldSpecial,oldSneak;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static volatile Map<String,Object> snapshot=Map.of();
    private static Map<String,Object> clientState=Map.of(),inputStart=Map.of();
    // 下列观察状态只由集成服务器线程更新。
    private static ServerPlayer owner;
    private static Husk target;
    private static LegacyPhantomSword marker;
    private static int joins,freshInputs,lockAtPress;
    private static boolean active,flight,attached,teleported,trickUp,sneakAtPress,commandMatched;
    private static double maxY;
    private static Vec3 launchPosition,launchSwordPosition;
    private static final Consumer<EntityJoinLevelEvent> JOIN=AirTrickClientProbe::joined;
    private static final Consumer<InputCommandEvent> INPUT=AirTrickClientProbe::input;
    private static final Consumer<ServerTickEvent.Post> TICK=AirTrickClientProbe::observe;

    static boolean enabled(){return Boolean.getBoolean("scex.legacy.airTrickProbe");}
    static Map<String,Object> report() {
        return Map.of("cases",List.copyOf(rows),"case_count",rows.size(),"test",test,"stage",stage,
                "tick",tick,"snapshot",snapshot,"client_state",clientState,"mappings_restored",restored);
    }
    private static String image(){return "airtrick-"+NAMES.get(test)+".png";}
    private static void key(Minecraft mc,int code,int action) {
        ((ProbeKeyboardInvoker)mc.keyboardHandler).legacyProbe$key(mc.getWindow().getWindow(),code,0,action,0);
    }
    private static void releaseKeys(Minecraft mc) {
        for(int code:new int[]{GLFW.GLFW_KEY_V,GLFW.GLFW_KEY_W,GLFW.GLFW_KEY_F7,GLFW.GLFW_KEY_LEFT_SHIFT})key(mc,code,GLFW.GLFW_RELEASE);
    }
    static void release(Minecraft mc) {
        releaseKeys(mc);
        if(oldLock!=null && !restored) {
            LegacyKeyMappings.LOCK_ON.setKey(oldLock);mc.options.keyUp.setKey(oldForward);
            SlashBladeKeyMappings.KEY_SPECIAL_MOVE.setKey(oldSpecial);mc.options.keyShift.setKey(oldSneak);
            KeyMapping.resetMapping();restored=true;
        }
        if(observing) {
            NeoForge.EVENT_BUS.unregister(JOIN);NeoForge.EVENT_BUS.unregister(INPUT);NeoForge.EVENT_BUS.unregister(TICK);observing=false;
        }
        var server=mc.getSingleplayerServer();
        if(server!=null)server.execute(()->{active=false;if(marker!=null)marker.discard();if(target!=null)target.discard();});
    }

    static boolean tick(Minecraft mc,Consumer<String> capture) {
        clientState=clientState(mc);
        if(failure!=null)throw new IllegalStateException("AirTrick client fixture",failure);
        if(test==NAMES.size())return true;
        if(stage>=2 && mc.screen!=null)throw new IllegalStateException("AirTrick unexpected screen: "+clientState);
        if(++tick>180)throw new IllegalStateException("AirTrick client timeout "+report());
        if(stage==0) {
            if(oldLock==null) {
                oldLock=LegacyKeyMappings.LOCK_ON.getKey();oldForward=mc.options.keyUp.getKey();
                oldSpecial=SlashBladeKeyMappings.KEY_SPECIAL_MOVE.getKey();oldSneak=mc.options.keyShift.getKey();
                NeoForge.EVENT_BUS.addListener(JOIN);NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,INPUT);
                NeoForge.EVENT_BUS.addListener(TICK);observing=true;
            }
            releaseKeys(mc);
            if(test==1) {
                var conflicts=java.util.Arrays.stream(mc.options.keyMappings).filter(mapping->mapping!=LegacyKeyMappings.LOCK_ON
                        && mapping.getKey().equals(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F7))).map(KeyMapping::getName).toList();
                if(!conflicts.isEmpty())throw new IllegalStateException("AirTrick fixture F7 already bound: "+conflicts);
            }
            LegacyKeyMappings.LOCK_ON.setKey(InputConstants.Type.KEYSYM.getOrCreate(test==1?GLFW.GLFW_KEY_F7:GLFW.GLFW_KEY_LEFT_SHIFT));
            mc.options.keyUp.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_W));
            mc.options.keyShift.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_LEFT_SHIFT));
            SlashBladeKeyMappings.KEY_SPECIAL_MOVE.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_V));
            KeyMapping.resetMapping();mc.options.setCameraType(CameraType.FIRST_PERSON);
            ready=false;snapshot=Map.of();stage=1;tick=0;
            int nextCase=test;
            mc.getSingleplayerServer().execute(()->prepare(mc,nextCase));
        } else if(stage==1 && ready && tick>=20) {
            if(Math.abs(mc.player.getY()-71)>.05 || mc.screen!=null)return false;
            key(mc,test==1?GLFW.GLFW_KEY_F7:GLFW.GLFW_KEY_LEFT_SHIFT,GLFW.GLFW_PRESS);
            key(mc,GLFW.GLFW_KEY_W,GLFW.GLFW_PRESS);inputStart=clientState(mc);stage=2;tick=0;
        } else if(stage==2 && tick>=4 && Boolean.TRUE.equals(snapshot.get("lock_forward"))) {
            int serverTarget=((Number)snapshot.get("server_target")).intValue();
            if(test==2 && serverTarget!=((Number)snapshot.get("fixture_target")).intValue())return false;
            if(test!=2 && serverTarget!=0)throw new IllegalStateException("Empty fixture unexpectedly locked an entity");
            if(test==1 && (mc.options.keyShift.isDown() || mc.player.isCrouching()))throw new IllegalStateException("Rebound lock used physical sneak");
            key(mc,GLFW.GLFW_KEY_V,GLFW.GLFW_PRESS);stage=3;tick=0;
        } else if(stage==3) {
            if(tick==2) {key(mc,GLFW.GLFW_KEY_V,GLFW.GLFW_RELEASE);key(mc,GLFW.GLFW_KEY_W,GLFW.GLFW_RELEASE);}
            clientEntities=0;
            for(var entity:mc.level.entitiesForRendering())if(entity instanceof LegacyPhantomSword)clientEntities++;
            boolean inputSeen=((Number)snapshot.getOrDefault("fresh_server_inputs",0)).intValue()==1;
            var clientTarget=mc.level.getEntity(((Number)snapshot.getOrDefault("fixture_target",0)).intValue());
            boolean settled=test==3?tick>=10:test==2?Boolean.TRUE.equals(snapshot.get("teleported")) && clientTarget!=null
                    && mc.player.position().distanceTo(clientTarget.position())<2 && clientEntities>0
                    :Boolean.TRUE.equals(snapshot.get("observed_flight")) && clientEntities>0;
            if(tick>=2 && inputSeen && settled) {
                validate(snapshot);
                capture.accept(image());stage=4;tick=0;
            }
        } else if(stage==4 && LegacyClientProbe.captured(image())) {
            releaseKeys(mc);stage=5;tick=0;
        } else if(stage==5 && tick>=8 && Boolean.TRUE.equals(snapshot.get("keys_released"))) {
            validate(snapshot);
            var row=new LinkedHashMap<String,Object>(snapshot);
            row.put("name",NAMES.get(test));row.put("passed",true);row.put("raw_keyboard_packets",true);
            row.put("lock_key",test==1?"F7":"LEFT_SHIFT");row.put("image",image());row.put("client_entities_at_capture",clientEntities);
            row.put("client_input_start",inputStart);row.put("client_end",clientState);
            rows.add(row);test++;stage=0;tick=0;
            if(test==NAMES.size()){release(mc);return true;}
        }
        return false;
    }

    private static Map<String,Object> clientState(Minecraft mc) {
        return Map.of("screen",mc.screen==null?"none":mc.screen.getClass().getName(),"window_active",mc.isWindowActive(),
                "lock_key",LegacyKeyMappings.LOCK_ON.getKey().getName(),"lock_down",LegacyKeyMappings.LOCK_ON.isDown(),
                "forward_down",mc.options.keyUp.isDown(),"special_down",SlashBladeKeyMappings.KEY_SPECIAL_MOVE.isDown(),
                "physical_sneak_down",mc.options.keyShift.isDown(),"forward_input",mc.player!=null && mc.player.input.up,
                "client_commands",mc.player==null?List.of():mc.player.getData(CapabilityInputState.INPUT_STATE.get()).getCommands().stream().map(Enum::name).toList());
    }

    private static void prepare(Minecraft mc,int nextCase) {
        try {
            active=false;if(marker!=null)marker.discard();if(target!=null)target.discard();
            marker=null;target=null;launchPosition=null;launchSwordPosition=null;
            var server=mc.getSingleplayerServer();owner=server.getPlayerList().getPlayers().getFirst();var level=owner.serverLevel();
            for(int x=7;x<=17;x++)for(int z=7;z<=30;z++)level.setBlockAndUpdate(new BlockPos(x,70,z),Blocks.SMOOTH_QUARTZ.defaultBlockState());
            owner.stopUsingItem();owner.removeAllEffects();owner.setGameMode(GameType.SURVIVAL);owner.setNoGravity(false);
            owner.setDeltaMovement(Vec3.ZERO);owner.fallDistance=0;owner.setHealth(owner.getMaxHealth());owner.teleportTo(level,12,71,12,0,0);
            owner.getPersistentData().remove(LegacyMode.MODERN);
            for(String name:List.of(SlayerStyleArts.AVOID_TRICKUP_PATH,SlayerStyleArts.AVOID_COUNTER_PATH,
                    SlayerStyleArts.AVOID_VEC_PATH,"sb.airtrick.counter","SB.AvoidCombo","SB.AvoidTimeout","SB.AvoidComboTimeout"))owner.getPersistentData().remove(name);
            var blade=server.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements()
                    .filter(entry->entry.key().location().toString().equals("slashblade:sange")).findFirst().orElseThrow().value().getBlade(server.registryAccess());
            blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);
            if(nextCase!=3)blade.enchant(server.registryAccess().holderOrThrow(Enchantments.POWER),1);
            var state=BladeStateAccess.of(blade).orElseThrow();state.setDefaultBewitched(true);state.setBroken(false);state.setSealed(false);
            state.setProudSoulCount(10);state.setKillCount(0);state.setTargetEntityId(0);state.setComboSeq(ComboStateRegistry.NONE.getId());
            state.setSpecialEffects(new net.minecraft.nbt.ListTag());blade.setDamageValue(0);
            owner.setItemInHand(InteractionHand.MAIN_HAND,blade);owner.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);LegacyMode.bindInventory(owner);
            if(nextCase==2) {
                target=EntityType.HUSK.create(level);target.setPos(12,71,22);target.setNoAi(true);target.setPersistenceRequired();
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);target.setHealth(200);level.addFreshEntity(target);
            }
            joins=0;freshInputs=0;lockAtPress=0;flight=false;attached=false;teleported=false;trickUp=false;
            sneakAtPress=false;commandMatched=false;maxY=71;active=true;
            owner.inventoryMenu.broadcastChanges();ready=true;
        } catch(Throwable error){failure=error;}
    }

    private static void joined(EntityJoinLevelEvent event) {
        if(active && !event.getLevel().isClientSide && event.getEntity() instanceof LegacyPhantomSword sword && sword.getOwner()==owner) {
            marker=sword;joins++;launchPosition=owner.position();launchSwordPosition=sword.position();
        }
    }
    private static void input(InputCommandEvent event) {
        if(!active || event.getEntity()!=owner || event.getOld().contains(InputCommand.SPRINT) || !event.getCurrent().contains(InputCommand.SPRINT))return;
        freshInputs++;commandMatched=event.getCurrent().containsAll(java.util.EnumSet.of(InputCommand.SNEAK,InputCommand.FORWARD,InputCommand.SPRINT));
        sneakAtPress=owner.isCrouching();
        var locked=BladeStateAccess.of(owner.getMainHandItem()).orElseThrow().getTargetEntity(owner.level());lockAtPress=locked==null?0:locked.getId();
    }
    private static void observe(ServerTickEvent.Post event) {
        if(!active)return;
        try {
            maxY=Math.max(maxY,owner.getY());trickUp|=owner.getPersistentData().contains(SlayerStyleArts.AVOID_TRICKUP_PATH);
            if(marker!=null) {
                flight|=marker.age()>0 && marker.airTrick() && marker.position().distanceTo(launchSwordPosition)>.5;
                attached|=!marker.airTrick() && !marker.isRemoved();
                teleported|=target!=null && attached && target.getHealth()<200 && owner.position().distanceTo(target.position())<2
                        && owner.position().distanceTo(launchPosition)>3;
            }
            var state=BladeStateAccess.of(owner.getMainHandItem()).orElseThrow();var locked=state.getTargetEntity(owner.level());
            var commands=owner.getData(CapabilityInputState.INPUT_STATE.get()).getCommands();
            var row=new LinkedHashMap<String,Object>();
            row.put("fresh_server_inputs",freshInputs);row.put("command_matched",commandMatched);row.put("projectile_joins",joins);
            row.put("soul_start",10);row.put("soul_end",state.getProudSoulCount());row.put("trick_up_seen",trickUp);row.put("max_vertical_rise",maxY-71);
            row.put("observed_flight",flight);row.put("accepted_impact",attached);row.put("teleported",teleported);
            row.put("physical_sneak_at_press",sneakAtPress);row.put("lock_at_press",lockAtPress);
            row.put("server_target",locked==null?0:locked.getId());row.put("fixture_target",target==null?0:target.getId());
            row.put("lock_forward",commands.contains(InputCommand.SNEAK) && commands.contains(InputCommand.FORWARD));
            row.put("keys_released",!commands.contains(InputCommand.SNEAK) && !commands.contains(InputCommand.FORWARD) && !commands.contains(InputCommand.SPRINT));
            row.put("server_position",List.of(owner.getX(),owner.getY(),owner.getZ()));row.put("target_health",target==null?0:target.getHealth());
            snapshot=Map.copyOf(row);
        } catch(Throwable error){failure=error;}
    }
    private static void validate(Map<String,Object> row) {
        int expected=test==3?0:1;
        boolean passed=((Number)row.get("fresh_server_inputs")).intValue()==1 && Boolean.TRUE.equals(row.get("command_matched"))
                && ((Number)row.get("projectile_joins")).intValue()==expected && ((Number)row.get("soul_end")).intValue()==10-expected
                && !Boolean.TRUE.equals(row.get("trick_up_seen"));
        if(test!=2)passed&=((Number)row.get("max_vertical_rise")).doubleValue()<.15 && !Boolean.TRUE.equals(row.get("teleported"));
        if(test==1)passed&=!Boolean.TRUE.equals(row.get("physical_sneak_at_press"));
        if(test==2)passed&=Boolean.TRUE.equals(row.get("observed_flight")) && Boolean.TRUE.equals(row.get("accepted_impact"))
                && Boolean.TRUE.equals(row.get("teleported")) && ((Number)row.get("lock_at_press")).intValue()==((Number)row.get("fixture_target")).intValue();
        if(!passed)throw new IllegalStateException("AirTrick keyboard case "+NAMES.get(test)+" failed: "+row);
    }
}
