package org.scex.slashbladelegacy.contracts;

import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.client.SlashBladeKeyMappings;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.client.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import org.scex.slashbladelegacy.*;

/** Real GUI player and actual Botania hurt method. Only the fixture boss's AI/tick is frozen. */
final class LegacyGaiaClientProbe {
    static boolean enabled(){return Boolean.getBoolean("scex.legacy.gaiaProbe");}
    static final int COUNT=19;
    private static final String[] BLADES={"slashblade:sange","prinegorerouse:aeon_blade","si_slashblade:legacy/fox_faerie"};
    private static final AABB AREA=new AABB(-20,40,-20,60,110,70);
    private static int test,stage,ticks;
    private static volatile boolean prepared,pending,finished;
    private static volatile Throwable failure;
    private static volatile UUID targetId;
    private static volatile Map<String,Object> snapshot=Map.of();
    private static final List<Map<String,Object>> rows=new ArrayList<>();
    private static Consumer<net.neoforged.neoforge.event.tick.EntityTickEvent.Pre> freeze;
    private static Consumer<net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent> protection;
    private static int acceptedEvents;
    private static float before;
    static Map<String,Object> report(){return Map.of("cases",List.copyOf(rows),"last_snapshot",snapshot,"case",test,"stage",stage,"scope","Actual Botania entity and real ServerPlayer damage path; test-only boss AI freeze; not ritual or full fight acceptance");}
    private static String mode(){return test<4?"melee":test<7?"phantom":test<10?"sb":test<13?"native_invulnerability":test<16?"protection":"sa";}
    private static String blade(){return test<4?test==3?"slashblade_addon:kamuy_lightning":BLADES[test]:BLADES[(test-4)%3];}
    static void release(Minecraft mc){mc.options.keyUse.setDown(false);SlashBladeKeyMappings.KEY_SUMMON_BLADE.setDown(false);mc.options.keyShift.setDown(false);mc.options.keyUp.setDown(false);mc.options.keyDown.setDown(false);}
    private static void clear(ServerPlayer p){for(var e:p.level().getEntities(p,AREA,e->!(e instanceof net.minecraft.world.entity.player.Player)))e.discard();LegacyRangeAttack.clear(p);}
    static boolean tick(Minecraft mc,Consumer<String> capture) {
        if(failure!=null)throw new IllegalStateException("Gaia "+test+" / "+stage,failure);if(test>=COUNT)return finished;
        if(++ticks>180)throw new IllegalStateException("Gaia timeout "+test+" / "+stage+" "+snapshot);
        if(stage==0) {
            release(mc);prepared=false;snapshot=Map.of();stage=1;ticks=0;acceptedEvents=0;
            mc.getSingleplayerServer().execute(()->{try{
                var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayers().getFirst();clear(p);
                if(freeze==null){freeze=e->{if(e.getEntity().getUUID().equals(targetId))e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(freeze);
                    protection=e->{if(e.getEntity().getUUID().equals(targetId)){acceptedEvents++;if(mode().equals("protection"))e.setCanceled(true);}};NeoForge.EVENT_BUS.addListener(protection);}
                require((boolean)Class.forName("vazkii.botania.common.helper.PlayerHelper").getMethod("isTruePlayer",Entity.class).invoke(null,p),"Botania true player gate");
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.teleportTo(p.serverLevel(),12,71,12,0,0);p.setDeltaMovement(0,0,0);p.setOnGround(true);p.removeAllEffects();p.setHealth(p.getMaxHealth());p.experienceLevel=300;
                var definition=p.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).getOrThrow(ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY,ResourceLocation.parse(blade())));
                var blade=definition.value().getBlade(p.registryAccess());blade.enchant(p.registryAccess().holderOrThrow(Enchantments.POWER),3);blade.setDamageValue(1);
                var state=BladeStateAccess.of(blade).orElseThrow();state.setProudSoulCount(1000);state.setBroken(false);state.setSealed(false);state.setComboSeq(ComboStateRegistry.NONE.getId());
                var tag=blade.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();tag.putBoolean(SummonedBladeMode.MODE,mode().equals("sb"));blade.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
                p.setItemInHand(InteractionHand.MAIN_HAND,blade);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                var rank=p.getData(CapabilityConcentrationRank.RANK_POINT);rank.setRawRankPoint(0);rank.setLastUpdte(p.level().getGameTime());
                var type=(EntityType<?>)Class.forName("vazkii.botania.common.entity.BotaniaEntities").getField("GAIA_GUARDIAN").get(null);var target=(Mob)type.create(p.level());
                var data=new CompoundTag();target.saveWithoutId(data);data.putInt("sourceX",12);data.putInt("sourceY",70);data.putInt("sourceZ",12);data.putInt("playerCount",1);data.putInt("invulTime",mode().equals("native_invulnerability")?20:0);target.load(data);
                target.setPos(12,71,mode().equals("melee") || mode().equals("native_invulnerability") || mode().equals("protection")?15:22);target.setNoAi(true);target.setPersistenceRequired();targetId=target.getUUID();before=target.getHealth();
                var attacked=target.getClass().getDeclaredField("playersWhoAttacked");attacked.setAccessible(true);require(((List<?>)attacked.get(target)).isEmpty() && target.getLastHurtByMob()==null,"fresh Gaia has no prior hit");
                require(p.level().addFreshEntity(target),"actual Gaia spawned");state.setTargetEntityId(target);p.inventoryMenu.broadcastChanges();prepared=true;
            }catch(Throwable e){failure=e;}});
        }else if(stage==1 && prepared && ticks>=20){stage=2;ticks=0;mc.options.setCameraType(CameraType.FIRST_PERSON);}
        else if(stage==2) {
            boolean ranged=mode().equals("phantom") || mode().equals("sb"),sa=mode().equals("sa"),denied=mode().equals("native_invulnerability") || mode().equals("protection");
            if(ticks==1){if(ranged)SlashBladeKeyMappings.KEY_SUMMON_BLADE.setDown(true);else{mc.options.keyUse.setDown(true);KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(1));}}
            if(ticks==(ranged?3:sa?26:2))release(mc);
            if(!pending){pending=true;mc.getSingleplayerServer().execute(()->{try{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var target=(LivingEntity)p.serverLevel().getEntity(targetId);require(target!=null,"Gaia remains present");
                var state=BladeStateAccess.of(p.getMainHandItem()).orElseThrow();var row=new LinkedHashMap<String,Object>();row.put("blade",blade());row.put("mode",mode());row.put("before",before);row.put("after",target.getHealth());row.put("incoming_events",acceptedEvents);row.put("combo",state.getComboSeq().toString());row.put("souls",state.getProudSoulCount());row.put("target_uuid",targetId.toString());row.put("gaia_invul_time",target.getClass().getMethod("getInvulTime").invoke(target));row.put("fresh_without_prior_weapon_hit",true);row.put("target_class",target.getClass().getName());snapshot=row;
            }catch(Throwable e){failure=e;}finally{pending=false;}});}
            if(snapshot.isEmpty())return false;
            float after=((Number)snapshot.get("after")).floatValue();
            if(denied && ticks>=14){require(after==before,"native invulnerability/protection honored");if(mode().equals("protection"))require(((Number)snapshot.get("incoming_events")).intValue()>0,"protection observed actual attempted damage");}
            else if(denied || after>=before || sa && ticks<27)return false;
            var client=java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).filter(e->e.getUUID().equals(targetId)).findFirst().orElse(null);
            if(!(client instanceof LivingEntity visible) || visible.getHealth()!=after)return false;
            var row=new LinkedHashMap<>(snapshot);row.put("client_health",visible.getHealth());row.put("image","08-gaia-"+test+".png");rows.add(row);capture.accept("08-gaia-"+test+".png");release(mc);stage=3;ticks=0;
        }else if(stage==3 && ticks>=6){if(++test<COUNT){stage=0;ticks=0;}else mc.getSingleplayerServer().execute(()->{try{clear(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst());NeoForge.EVENT_BUS.unregister(freeze);NeoForge.EVENT_BUS.unregister(protection);}finally{finished=true;}});}
        return false;
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
