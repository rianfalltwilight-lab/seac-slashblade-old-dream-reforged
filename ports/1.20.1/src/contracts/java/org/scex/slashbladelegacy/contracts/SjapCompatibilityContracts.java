package org.scex.slashbladelegacy.contracts;

import com.mojang.authlib.GameProfile;
import java.util.*;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import org.scex.slashbladelegacy.*;

/** Check the actual SJAP methods after production SRG transformation. */
final class SjapCompatibilityContracts {
    static void run(MinecraftServer server,Map<String,Object> report) throws Exception {
        report.put("suite","sjap-compat-20261012");
        check(ModList.get().isLoaded("slashblade_addon"),"SJAP must be loaded");
        var player=FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"SjapCompatProbe"));
        var blade=server.registryAccess().registryOrThrow(SlashBladeDefinition.REGISTRY_KEY)
                .holders().filter(h->h.key().location().toString().equals("slashblade:sange")).findFirst().orElseThrow().value().getBlade();
        var state=BladeStateAccess.of(blade).orElseThrow();
        state.setNonEmpty();state.setBroken(false);state.setSealed(false);state.setProudSoulCount(1000);
        player.setPos(64,160,8);player.setYRot(0);player.setXRot(0);player.setNoGravity(true);
        player.setItemInHand(InteractionHand.MAIN_HAND,blade);server.overworld().addNewPlayer(player);
        var target=EntityType.ZOMBIE.create(server.overworld());target.setNoAi(true);target.setNoGravity(true);target.setPos(64,160,16);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);server.overworld().addFreshEntity(target);
        state.setTargetEntityId(target);
        var rank=player.getCapability(mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank.RANK_POINT).resolve().orElseThrow();
        rank.setRawRankPoint(0);rank.setLastUpdte(server.overworld().getGameTime());
        var cases=new ArrayList<Map<String,Object>>();report.put("cases",cases);
        try {
            for(boolean modern:List.of(false,true))for(String art:List.of("GaleSwords","LightingSwords")){
                player.getPersistentData().putBoolean(LegacyMode.MODERN,modern);
                Class.forName("cn.mmf.slashblade_addon.specialattacks."+art)
                        .getMethod("doSlash",LivingEntity.class,boolean.class,double.class,float.class).invoke(null,player,false,7d,2f);
                var swords=server.overworld().getEntitiesOfClass(EntityAbstractSummonedSword.class,player.getBoundingBox().inflate(100),
                        e->!e.isRemoved() && e.getOwner()==player && e.getClass().getName().startsWith("cn.mmf.slashblade_addon."));
                var delays=swords.stream().map(EntityAbstractSummonedSword::getDelay).sorted().toList();
                if(art.equals("GaleSwords")){
                    check(swords.size()==6,"Gale must create six swords");
                    check(swords.stream().allMatch(s->modern?s.getDelay()>=20:s.getDelay()==7 && s.getColor()==0xFF3333),"Gale delay and color");
                }else{
                    check(swords.size()==4,"Lighting must create four swords");
                    check(delays.equals(modern?List.of(20,21,22,24):List.of(7,7,8,18)),"Lighting delays: "+delays);
                }
                var fired=new ArrayList<Map<String,Object>>();
                for(var sword:swords){
                    check(sword.getVehicle()==player,"Sword must ride its owner before the fire tick");
                    sword.tickCount=sword.getDelay();sword.rideTick();
                    var field=Class.forName("cn.mmf.slashblade_addon.entity.BlisteringSwordsEntity").getDeclaredField("fireTime");field.setAccessible(true);
                    long actual=field.getLong(sword),expected=sword.tickCount+(modern?sword.getDelay():0);
                    check((boolean)sword.getClass().getMethod("itFired").invoke(sword),"Sword must fire at its delay");
                    check(actual==expected,"Fire time: "+actual+" != "+expected);
                    fired.add(Map.of("delay",sword.getDelay(),"tick_count",sword.tickCount,"fire_time",actual,"passed",true));
                    sword.discard();
                }
                cases.add(Map.of("art",art,"modern",modern,"delays",delays,"swords",fired,"passed",true));
            }
            report.put("checked_swords",20);report.put("case_count",cases.size());
            report.put("slashblade_version",ModList.get().getModContainerById("slashblade").orElseThrow().getModInfo().getVersion().toString());
            report.put("sjap_version",ModList.get().getModContainerById("slashblade_addon").orElseThrow().getModInfo().getVersion().toString());
        }finally{target.discard();player.discard();}
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError("SJAP: "+message);}
}
