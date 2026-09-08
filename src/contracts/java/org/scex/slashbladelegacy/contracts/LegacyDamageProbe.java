package org.scex.slashbladelegacy.contracts;

import java.util.*;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.util.TargetSelector;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.scex.slashbladelegacy.*;

/** Observe real survival-player use packets and target health. Never invokes damage itself. */
@EventBusSubscriber(modid="slashblade_legacy_contracts",value=Dist.CLIENT)
public final class LegacyDamageProbe {
    private static Zombie target;
    private static boolean requested;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static volatile Map<String,Object> snapshot=Map.of();
    private static final List<Map<String,Object>> rows=new ArrayList<>();
    private static final List<Map<String,Object>> events=new ArrayList<>();
    private static String bladeId;
    private static int click;
    private static int equipped=-1,bladesPerCase=1,rangeCase;
    private static double distance=2;
    private static boolean expectedDamage=true;
    public static void expandCases(List<String> blades){
        bladesPerCase=blades.size();
        if(Boolean.getBoolean("scex.legacy.rangeProbe")){var original=List.copyOf(blades);for(int i=1;i<5;i++)blades.addAll(original);}
    }
    public static boolean enabled(){return Boolean.getBoolean("scex.legacy.damageProbe");}
    public static void equip(ServerPlayer player){
        if(!enabled())return;
        player.setGameMode(GameType.SURVIVAL);
        equipped++;rangeCase=Boolean.getBoolean("scex.legacy.rangeProbe")?equipped/bladesPerCase:-1;
        distance=switch(rangeCase){case 0,2->4.6;case 1->5.2;case 3->3.6;case 4->2.6;default->2;};
        expectedDamage=rangeCase==-1 || rangeCase==0 || rangeCase==4;
        player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).setBaseValue(rangeCase>=3?1:3);
        player.getData(mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank.RANK_POINT).setRawRankPoint(0);
        for(int x=11;x<=13;x++)for(int y=71;y<=74;y++)player.level().setBlockAndUpdate(new net.minecraft.core.BlockPos(x,y,14),
            (rangeCase==2?net.minecraft.world.level.block.Blocks.STONE:net.minecraft.world.level.block.Blocks.AIR).defaultBlockState());
        if(target!=null)target.discard();
        target=EntityType.ZOMBIE.create(player.serverLevel());
        target.setNoAi(true);target.setNoGravity(true);target.setSilent(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        target.getAttribute(Attributes.ARMOR).setBaseValue(0);
        target.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CARVED_PUMPKIN));
        target.setHealth(200);target.setPos(12,71,12+distance);
        if(!player.serverLevel().addFreshEntity(target))throw new IllegalStateException("Damage target spawn failed");
        requested=false;ready=false;
    }
    public static boolean beforeClick(Minecraft mc,String id,int index){
        if(!enabled())return true;
        if(failure!=null)throw new IllegalStateException("Damage fixture failed",failure);
        if(!requested){
            requested=true;ready=false;bladeId=id;click=index;
            mc.getSingleplayerServer().execute(()->{try{
                var player=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                target.setPos(12,71,12+distance);target.setDeltaMovement(Vec3.ZERO);target.setHealth(200);target.invulnerableTime=0;
                target.setLastHurtByMob(null);target.setRemainingFireTicks(0);
                var state=BladeStateAccess.of(player.getMainHandItem()).orElseThrow();
                rows.add(new LinkedHashMap<>(Map.of("blade",id,"click",index,"before_health",target.getHealth(),
                    "config_loaded",LegacyCompat.SPEC.isLoaded(),"combat_enabled",LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT),
                    "broken",state.isBroken(),"sealed",state.isSealed(),"combo_root",state.getComboRoot().toString(),
                    "target_eligible",TargetSelector.getTargettableEntitiesWithinAABB(player.level(),player,LegacyCombat.box(player,LegacyMove.SAYA1)).contains(target))));
                rows.getLast().putAll(Map.of("range_case",rangeCase,"center_distance",distance,"expected_damage",expectedDamage,
                    "box_gap",TargetSelector.distanceBetweenEntity(player,target),"resolved_reach",TargetSelector.getResolvedReach(player)));
                ready=true;
            }catch(Throwable e){failure=e;}});
            return false;
        }
        return ready;
    }
    public static void finishClick(){
        if(!enabled())return;
        var row=rows.getLast();row.put("server_after",snapshot);
        requested=false;ready=false;
        if(!snapshot.containsKey("health") || (((Number)snapshot.get("health")).floatValue()<200)!=expectedDamage)
            throw new IllegalStateException("Right click damage/range mismatch: "+row);
    }
    public static Map<String,Object> report(){return Map.of("hits",rows,"events",events);}
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event){
        if(!enabled() || target==null || target.isRemoved() || event.getServer().getPlayerList().getPlayers().isEmpty())return;
        var p=event.getServer().getPlayerList().getPlayers().getFirst();var s=BladeStateAccess.of(p.getMainHandItem()).orElse(null);
        snapshot=Map.of("tick",p.level().getGameTime(),"health",target.getHealth(),"target_position",target.position().toString(),
            "player_position",p.position().toString(),"combo",s==null?"none":s.getComboSeq().toString(),
            "reach",TargetSelector.getResolvedReach(p),"los",p.hasLineOfSight(target));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void slash(SlashBladeEvent.DoSlashEvent event){
        if(enabled() && target!=null && !event.getUser().level().isClientSide)
            events.add(Map.of("event","do_slash","canceled",event.isCanceled(),"blade",bladeId,"click",click,"tick",event.getUser().level().getGameTime()));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void attack(AttackEntityEvent event){
        if(enabled() && event.getTarget()==target && !event.getEntity().level().isClientSide)
            events.add(Map.of("event","attack_entity","canceled",event.isCanceled(),"blade",bladeId,"click",click,"tick",event.getEntity().level().getGameTime()));
    }
}
