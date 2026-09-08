package org.scex.slashbladelegacy.contracts;

import java.util.*;
import com.mojang.authlib.GameProfile;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.*;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import org.scex.slashbladelegacy.*;

/** Public input event plus the real upstream scheduler; GUI packets are tested separately. */
final class Super17Contracts {
    private static final List<Map<String,Object>> rows=new ArrayList<>();
    private static void check(String name,boolean pass){rows.add(Map.of("case",name,"passed",pass));}
    static void run(MinecraftServer server,Map<String,Object> report) {
        rows.clear();report.put("super_input",rows);
        var p=net.neoforged.neoforge.common.util.FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"SuperContract"));
        p.setPos(8,160,8);p.setYRot(0);p.setGameMode(GameType.SURVIVAL);
        server.overworld().addNewPlayer(p);
        if(server.overworld().getEntity(p.getUUID())!=p)throw new IllegalStateException("Super fixture owner not in level lookup");
        try {
            var blade=prepare(p,LegacyArts.Art.DIMENSION);input(p,true,0);
            var state=p.getData(CapabilityInputState.INPUT_STATE.get());
            for(int i=0;i<25;i++)InputClock.next(p);state.getScheduler().onTick(p);
            check("holding beyond 20 ticks does not auto release or charge durability",managers(p).isEmpty() && blade.getDamageValue()==0);
            clear(p);
            for(var art:LegacyArts.Art.values()){
                blade=prepare(p,art);input(p,true,0);input(p,false,20);
                check("release 20 ticks uses super for "+art,managers(p).size()==1);
                check("no upfront durability or soul fee "+art,blade.getDamageValue()==0 && BladeStateAccess.of(blade).orElseThrow().getProudSoulCount()==1000);
            }
            blade=prepare(p,LegacyArts.Art.DRIVE);input(p,true,0);input(p,false,19);check("19 ticks insufficient",managers(p).isEmpty());
            for(String gate:List.of("kills999","damaged","sealed","broken","unenchanted")){
                blade=prepare(p,LegacyArts.Art.DIMENSION);var s=BladeStateAccess.of(blade).orElseThrow();
                switch(gate){case "kills999"->s.setKillCount(999);case "damaged"->blade.setDamageValue(1);case "sealed"->s.setSealed(true);case "broken"->s.setBroken(true);case "unenchanted"->blade.set(DataComponents.ENCHANTMENTS,net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);}
                input(p,true,0);input(p,false,20);check("eligibility "+gate,managers(p).isEmpty());
            }
            blade=prepare(p,LegacyArts.Art.DIMENSION);p.setOnGround(false);input(p,true,0);input(p,false,20);check("airborne release",managers(p).size()==1);
            blade=prepare(p,LegacyArts.Art.DIMENSION);p.setGameMode(GameType.SPECTATOR);input(p,true,0);input(p,false,20);check("r87 no spectator activation gate",managers(p).size()==1);
            blade=prepare(p,LegacyArts.Art.DIMENSION);
            java.util.function.Consumer<SlashBladeEvent.PerformSlashArtEvent> cancel=e->{if(e.getEntityLiving()==p)e.setCanceled(true);};
            NeoForge.EVENT_BUS.addListener(cancel);try{input(p,true,0);input(p,false,20);}finally{NeoForge.EVENT_BUS.unregister(cancel);}
            check("public cancellation before effect and payment",managers(p).isEmpty() && blade.getDamageValue()==0);
            blade=prepare(p,LegacyArts.Art.DIMENSION);blade.set(DataComponents.UNBREAKABLE,new Unbreakable(true));input(p,true,0);input(p,false,20);
            input(p,false,20);check("duplicate release cannot launch twice",managers(p).size()==1);
            var active=managers(p);if(!active.isEmpty())for(int i=0;i<30;i++)active.getFirst().tick();
            check("finish sets half durability even Unbreakable",blade.getDamageValue()==blade.getMaxDamage()/2 && managers(p).isEmpty());
            blade=prepare(p,LegacyArts.Art.DIMENSION);input(p,true,0);input(p,false,20);
            active=managers(p);p.getInventory().setItem(1,blade);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
            if(!active.isEmpty())for(int i=0;i<30;i++)active.getFirst().tick();
            check("switch item cannot evade source blade half durability",blade.getDamageValue()==blade.getMaxDamage()/2 && managers(p).isEmpty());
        }finally{clear(p);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.getInventory().setItem(1,ItemStack.EMPTY);p.setGameMode(GameType.SURVIVAL);p.discard();}
        long failed=rows.stream().filter(r->Boolean.FALSE.equals(r.get("passed"))).count();
        if(failed!=0)throw new AssertionError("Super input contracts failed "+failed+" / "+rows.size());
    }
    private static ItemStack prepare(ServerPlayer p,LegacyArts.Art art){
        clear(p);p.setGameMode(GameType.SURVIVAL);p.setOnGround(true);p.removeAllEffects();
        var blade=new ItemStack(SlashBladeItems.SLASHBLADE.get());blade.set(DataComponents.CUSTOM_NAME,Component.literal("Super r87"));blade.enchant(p.registryAccess().holderOrThrow(Enchantments.POWER),1);
        var state=BladeStateAccess.of(blade).orElseThrow();state.setKillCount(1000);state.setProudSoulCount(1000);state.setBroken(false);state.setSealed(false);state.setSlashArtsKey(LegacyArts.key(art));state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(p.level().getGameTime());blade.setDamageValue(0);
        p.setItemInHand(InteractionHand.MAIN_HAND,blade);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);return blade;
    }
    private static void input(ServerPlayer p,boolean held,int elapsed){
        for(int i=0;i<elapsed;i++)InputClock.next(p);
        var s=p.getData(CapabilityInputState.INPUT_STATE.get());var old=s.getCommands().clone();s.getCommands().clear();if(held)s.getCommands().add(InputCommand.SPRINT);
        s.getLastPressTimes().put(InputCommand.SPRINT,p.level().getGameTime()-elapsed);
        InputCommandEvent.onInputChange(p,s,old,s.getCommands().clone());
    }
    private static List<LegacyArtEntity> managers(ServerPlayer p){return p.level().getEntitiesOfClass(LegacyArtEntity.class,new AABB(-50,100,-50,70,220,70),e->!e.isRemoved() && e.mode()==LegacyArtEntity.Mode.JUDGEMENT);}
    private static void clear(ServerPlayer p){for(var e:managers(p))e.discard();p.getData(CapabilityInputState.INPUT_STATE.get()).getCommands().clear();}
}
