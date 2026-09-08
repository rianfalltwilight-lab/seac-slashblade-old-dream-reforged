package org.scex.slashbladelegacy.contracts;

import java.util.*;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.client.SlashBladeKeyMappings;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import org.scex.slashbladelegacy.*;

/** Client input and actual entity replication for three registered blades, five r87 ranged modes each. */
final class LegacyRangeClientProbe {
    static boolean enabled(){return Boolean.getBoolean("scex.legacy.full17RangeProbe");}
    private static final String[] BLADES={"slashblade:sange","prinegorerouse:aeon_blade","si_slashblade:legacy/fox_faerie"};
    private static final String[] ARTS={"single","spiral","blistering","rain","sb"};
    private static final AABB AREA=new AABB(-20,40,-20,50,110,60);
    private static int test,stage,tick;
    private static volatile boolean prepared,pending,finished;
    private static volatile Throwable failure;
    private static volatile Map<String,Object> snapshot=Map.of();
    private static UUID targetId;
    private static final List<Map<String,Object>> cases=new ArrayList<>();
    private static final List<Map<String,Object>> inputs=new java.util.concurrent.CopyOnWriteArrayList<>();
    private static java.util.function.Consumer<mods.flammpfeil.slashblade.event.handler.InputCommandEvent> observer;
    static Map<String,Object> report(){return Map.of("cases",List.copyOf(cases),"inputs",List.copyOf(inputs));}
    static String imageName(int n){return "06-range-"+(n/5)+"-"+ARTS[n%5]+".png";}
    static void release(Minecraft mc) {
        SlashBladeKeyMappings.KEY_SUMMON_BLADE.setDown(false);mc.options.keyShift.setDown(false);mc.options.keyUp.setDown(false);mc.options.keyDown.setDown(false);
    }
    private static void clear(ServerPlayer player) {
        // The preceding melee probe leaves its pumpkin-headed zombie two blocks in front of the player.
        // Its released SA can also leave non-Mob managers. Clear only this private test arena between cases.
        for(var e:player.level().getEntities(player,AREA,e->!(e instanceof net.minecraft.world.entity.player.Player)))e.discard();
        if(targetId!=null){var target=player.serverLevel().getEntity(targetId);if(target!=null)target.discard();targetId=null;}
        LegacyRangeAttack.clear(player);player.getPersistentData().remove("slashblade_legacy_compat.spiral");player.getPersistentData().remove("slashblade_legacy_compat.blistering_until");
    }
    static boolean tick(Minecraft mc,java.util.function.Consumer<String> capture) {
        if(failure!=null)throw new IllegalStateException("r87 client range "+test+" / "+stage,failure);
        if(test>=15)return finished;
        if(++tick>180)throw new IllegalStateException("r87 range timeout: "+test+" / "+stage+" "+snapshot);
        int art=test%5;
        if(stage==0) {
            release(mc);stage=1;prepared=false;snapshot=Map.of();
            mc.getSingleplayerServer().execute(()->{try {
                var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayers().getFirst();clear(p);
                if(observer==null) {
                    observer=e->{
                        boolean old=e.getOld().contains(mods.flammpfeil.slashblade.util.InputCommand.M_DOWN),now=e.getCurrent().contains(mods.flammpfeil.slashblade.util.InputCommand.M_DOWN);
                        if(old!=now)inputs.add(Map.of("case",test,"down",now,"tick",e.getEntity().level().getGameTime(),"souls",BladeStateAccess.of(e.getEntity().getMainHandItem()).orElseThrow().getProudSoulCount(),
                                "phantoms",e.getEntity().level().getEntitiesOfClass(LegacyPhantomSword.class,AREA).size(),"blades",e.getEntity().level().getEntitiesOfClass(LegacySummonedBlade.class,AREA).size()));
                    };
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,observer);
                }
                for(int x=7;x<=20;x++)for(int z=7;z<=25;z++)p.level().setBlockAndUpdate(new BlockPos(x,70,z),net.minecraft.world.level.block.Blocks.SMOOTH_QUARTZ.defaultBlockState());
                p.teleportTo(p.serverLevel(),12,71,12,0,0);p.setDeltaMovement(0,0,0);p.setOnGround(true);p.fallDistance=0;
                var definition=server.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).getOrThrow(ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY,ResourceLocation.parse(BLADES[test/5])));
                var blade=definition.value().getBlade(server.registryAccess());blade.enchant(p.registryAccess().holderOrThrow(Enchantments.POWER),3);blade.setDamageValue(0);
                var state=BladeStateAccess.of(blade).orElseThrow();state.setBroken(false);state.setSealed(false);state.setProudSoulCount(1000);state.setComboSeq(mods.flammpfeil.slashblade.registry.ComboStateRegistry.NONE.getId());
                var data=blade.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();data.putBoolean(SummonedBladeMode.MODE,art==4);blade.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
                p.setItemInHand(InteractionHand.MAIN_HAND,blade);p.setItemInHand(InteractionHand.OFF_HAND,net.minecraft.world.item.ItemStack.EMPTY);p.experienceLevel=0;
                var rank=p.getData(CapabilityConcentrationRank.RANK_POINT);rank.setRawRankPoint(580*rank.getUnitCapacity()/100);rank.setLastUpdte(p.level().getGameTime());
                var target=EntityType.HUSK.create(p.level());target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.setHealth(1000);
                target.setPos(12,71,22);target.setNoAi(true);target.setPersistenceRequired();p.level().addFreshEntity(target);targetId=target.getUUID();state.setTargetEntityId(target);
                p.inventoryMenu.broadcastChanges();prepared=true;
            }catch(Throwable e){failure=e;}});
        }else if(stage==1 && prepared && tick>=20) {
            stage=2;tick=0;
            mc.options.setCameraType(art==1 || art==2?CameraType.THIRD_PERSON_FRONT:CameraType.FIRST_PERSON);
        }else if(stage==2) {
            if(tick==1) {
                if(art==3){mc.options.keyShift.setDown(true);mc.options.keyDown.setDown(true);}
                else {if(art==2){mc.options.keyShift.setDown(true);mc.options.keyUp.setDown(true);}SlashBladeKeyMappings.KEY_SUMMON_BLADE.setDown(true);}
            }
            if(art==3 && tick==2){mc.options.keyDown.setDown(false);mc.options.keyUp.setDown(true);SlashBladeKeyMappings.KEY_SUMMON_BLADE.setDown(true);}
            if((art==0 || art==4) && tick==3)SlashBladeKeyMappings.KEY_SUMMON_BLADE.setDown(false);
            if(!pending) {
                pending=true;
                mc.getSingleplayerServer().execute(()->{try{
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    var swords=p.level().getEntitiesOfClass(LegacyPhantomSword.class,AREA);var sb=p.level().getEntitiesOfClass(LegacySummonedBlade.class,AREA);
                    var target=p.serverLevel().getEntity(targetId);float health=target instanceof net.minecraft.world.entity.LivingEntity living?living.getHealth():0;
                    var state=BladeStateAccess.of(p.getMainHandItem()).orElseThrow();
                    var observation=new LinkedHashMap<String,Object>(Map.of("case",test,"blade",BLADES[test/5],"art",ARTS[art],"count",swords.size(),"sb_count",sb.size(),"souls",state.getProudSoulCount(),"health",health,
                            "modes",swords.stream().map(s->s.art().name()).toList(),"rank",p.getData(CapabilityConcentrationRank.RANK_POINT).getRank(p.level().getGameTime()).level));
                    observation.put("blade_state",state.serializeNBT().toString());
                    observation.put("swords",swords.stream().limit(3).map(s->{var tag=new net.minecraft.nbt.CompoundTag();s.saveWithoutId(tag);return Map.of("pos",s.position().toString(),"age",s.age(),"attached",tag.hasUUID("LegacyAttached")?tag.getUUID("LegacyAttached").toString():"", "target",String.valueOf(targetId));}).toList());
                    observation.put("attached_to_target",art==4?sb.stream().anyMatch(s->s.getHitEntity()==target):swords.stream().anyMatch(s->{var tag=new net.minecraft.nbt.CompoundTag();s.saveWithoutId(tag);return tag.hasUUID("LegacyAttached") && tag.getUUID("LegacyAttached").equals(targetId);}));
                    snapshot=observation;
                }catch(Throwable e){failure=e;}finally{pending=false;}});
            }
            if(snapshot.isEmpty())return false;
            int count=((Number)snapshot.get("count")).intValue(),sb=((Number)snapshot.get("sb_count")).intValue(),souls=((Number)snapshot.get("souls")).intValue();
            boolean single=art==0 || art==4;
            boolean ready=single?((art==0?count:sb)==1 && souls==999 && ((Number)snapshot.get("health")).floatValue()<1000 && Boolean.TRUE.equals(snapshot.get("attached_to_target")))
                    :count==(art==1?6:art==2?8:30) && souls==990;
            int clientCount=0;for(var entity:mc.level.entitiesForRendering())if(art==4?entity instanceof LegacySummonedBlade:entity instanceof LegacyPhantomSword)clientCount++;
            if(ready && clientCount>0) {
                var result=new LinkedHashMap<String,Object>(snapshot);result.put("client_entities",clientCount);result.put("image",imageName(test));cases.add(result);
                capture.accept(imageName(test));stage=3;tick=0;
            }
        }else if(stage==3 && tick>=4) {
            release(mc);stage=4;tick=0;
        }else if(stage==4 && tick>=6) {
            if(++test<15){stage=0;tick=0;}
            else {
                mc.getSingleplayerServer().execute(()->{try{clear(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst());
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(observer);
                    for(var row:inputs)if(Boolean.TRUE.equals(row.get("down")) && (((Number)row.get("phantoms")).intValue()!=0 || ((Number)row.get("blades")).intValue()!=0))throw new AssertionError("native immediate shot duplicated: "+row);
                }catch(Throwable e){failure=e;}finally{finished=true;}});
            }
        }
        return false;
    }
}
