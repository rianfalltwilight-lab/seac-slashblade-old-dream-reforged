package org.scex.slashbladelegacy.contracts;

import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.event.*;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.scex.slashbladelegacy.*;

/** Actual anvil pickup and input-bus contracts; this does not claim real client visual acceptance. */
final class BatchPhantomContracts {
    private static int checks;
    static void run(MinecraftServer server,Map<String,Object> report) {
        checks=0;
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"BatchPhantom"));
        player.setPos(-8,160,-8);player.setOnGround(true);player.getAbilities().instabuild=false;
        server.overworld().addNewPlayer(player);
        var base=server.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).getOrThrow(ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY,ResourceLocation.parse("slashblade:sange"))).value().getBlade(server.registryAccess());
        player.setItemInHand(InteractionHand.MAIN_HAND,base);
        try {
            Repair17Contracts.run(player,report);
            batch(player,report);
            player.experienceLevel=1000;BladeSoul17Contracts.run(player,report);
            Range17Contracts.run(player,report);
            inputCounts(player,report);
            rank(player,report);
        } finally {
            LegacyRangeAttack.clear(player);player.discard();report.put("batch_phantom_checks",checks);
        }
    }
    private static ItemStack blade(ServerPlayer player) {
        var result=player.getMainHandItem().copy();result.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);result.remove(DataComponents.REPAIR_COST);
        var state=BladeStateAccess.of(result).orElseThrow();state.setSpecialEffects(new net.minecraft.nbt.ListTag());state.setMaxDamage(100);state.setRefine(300);state.setProudSoulCount(0);state.setSealed(false);state.setBroken(false);result.setDamageValue(75);return result;
    }
    private static AnvilMenu menu(ServerPlayer player,ItemStack blade,ItemStack souls) {
        var menu=new AnvilMenu(91,player.getInventory());menu.getSlot(0).set(blade);menu.getSlot(1).set(souls);menu.createResult();return menu;
    }
    private static void batch(ServerPlayer player,Map<String,Object> report) {
        var rows=new ArrayList<Map<String,Object>>();report.put("batch_anvil",rows);
        var materials=List.of(SlashBladeItems.PROUDSOUL.get(),SlashBladeItems.PROUDSOUL_INGOT.get(),SlashBladeItems.PROUDSOUL_SPHERE.get(),SlashBladeItems.PROUDSOUL_TINY.get(),SlashBladeItems.PROUDSOUL_CRYSTAL.get(),SlashBladeItems.PROUDSOUL_TRAPEZOHEDRON.get());
        int[] costs={2,3,4,1,5,5},souls={200,400,400,100,500,500};
        for(int i=0;i<materials.size();i++) {
            player.experienceLevel=1000;var input=blade(player);var menu=menu(player,input,new ItemStack(materials.get(i),64));
            var output=menu.getSlot(2).getItem();var state=BladeStateAccess.of(output).orElseThrow();
            check(state.getRefine()==364 && state.getProudSoulCount()==souls[i]*64 && output.getDamageValue()==0 && output.getMaxDamage()==100,"64 units, r87 per-unit rewards and fixed max durability "+i);
            check(menu.getCost()==costs[i]*64 && menu.getSlot(2).mayPickup(player),"survival pickup above vanilla 40-level cap "+i);
            menu.getSlot(2).onTake(player,output);
            check(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty() && player.experienceLevel==1000-costs[i]*64,"actual pickup consumes exactly 64 and correct levels "+i);
            check(BladeStateAccess.of(input).orElseThrow().getRefine()==300 && input.getDamageValue()==75,"preview preserves input "+i);
            rows.add(Map.of("material",materials.get(i).toString(),"consumed",64,"cost",costs[i]*64,"souls",souls[i]*64));
        }
        player.experienceLevel=7;var menu=menu(player,blade(player),new ItemStack(materials.getFirst(),64));var output=menu.getSlot(2).getItem();
        check(BladeStateAccess.of(output).orElseThrow().getRefine()==303 && menu.getCost()==6,"partial affordable batch");
        menu.getSlot(2).onTake(player,output);check(menu.getSlot(1).getItem().getCount()==61 && player.experienceLevel==1,"partial pickup preserves unpaid materials and XP");
        player.experienceLevel=0;check(menu(player,blade(player),new ItemStack(materials.getFirst(),64)).getSlot(2).getItem().isEmpty(),"zero XP cannot fall through vanilla repair");
        player.experienceLevel=1000;
        Consumer<RefineProgressEvent> partial=e->{if(e.getMaterialCost()==4){e.getBlade().set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("rejected"));e.setCanceled(true);}};
        NeoForge.EVENT_BUS.addListener(partial);
        try {menu=menu(player,blade(player),new ItemStack(materials.getFirst(),64));output=menu.getSlot(2).getItem();check(BladeStateAccess.of(output).orElseThrow().getRefine()==303 && !output.getHoverName().getString().equals("rejected"),"canceled fourth step retains only accepted preview");}finally{NeoForge.EVENT_BUS.unregister(partial);}
        Consumer<RefineSettlementEvent> cancel=e->e.setCanceled(true);NeoForge.EVENT_BUS.addListener(cancel);
        try {check(menu(player,blade(player),new ItemStack(materials.getFirst(),64)).getSlot(2).getItem().isEmpty(),"final settlement cancellation");}finally{NeoForge.EVENT_BUS.unregister(cancel);}
        Consumer<RefineProgressEvent> stalled=e->e.setMaterialCost(0);NeoForge.EVENT_BUS.addListener(stalled);
        try {check(menu(player,blade(player),new ItemStack(materials.getFirst(),64)).getSlot(2).getItem().isEmpty(),"invalid non-advancing listener terminates");}finally{NeoForge.EVENT_BUS.unregister(stalled);}
        var saturated=blade(player);var saturation=BladeStateAccess.of(saturated).orElseThrow();saturation.setRefine(Integer.MAX_VALUE-1);saturation.setProudSoulCount(Integer.MAX_VALUE-50);
        menu=menu(player,saturated,new ItemStack(materials.getFirst(),64));output=menu.getSlot(2).getItem();var state=BladeStateAccess.of(output).orElseThrow();
        check(state.getRefine()==Integer.MAX_VALUE && state.getProudSoulCount()==Integer.MAX_VALUE,"refine and soul overflow bounded");
        menu.getSlot(2).onTake(player,output);check(menu.getSlot(1).getItem().getCount()==63,"overflow boundary consumes one accepted unit");
        var blank=new ItemStack(SlashBladeItems.SLASHBLADE.get());BladeStateAccess.of(blank).orElseThrow().setProudSoulCount(2000);
        var named=LegacyBladeSouls.crystal(player,LegacyBladeSouls.POOL.getFirst());named.setCount(64);menu=menu(player,blank,named);output=menu.getSlot(2).getItem();
        check(BladeStateAccess.of(output).orElseThrow().getProudSoulCount()==1000 && BladeStateAccess.of(output).orElseThrow().getRefine()==1,"named soul transforms once");
        menu.getSlot(2).onTake(player,output);check(menu.getSlot(1).getItem().getCount()==63,"named soul consumes only one");
        rows.add(Map.of("boundaries","partial XP, zero XP, canceled progress/settlement, non-progress, overflow, named soul single application"));
    }
    private static void inputCounts(ServerPlayer player,Map<String,Object> report) {
        var rows=new ArrayList<Map<String,Object>>();report.put("phantom_input_spawn_counts",rows);
        var joined=new ArrayList<Entity>();Consumer<EntityJoinLevelEvent> collect=e->{if(e.getLevel()==player.level() && e.getEntity() instanceof EntityAbstractSummonedSword sword && sword.getOwner()==player)joined.add(sword);};
        NeoForge.EVENT_BUS.addListener(collect);var original=player.getMainHandItem();
        try {
            for(var definition:player.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements().toList()) {
                var blade=definition.value().getBlade(player.registryAccess());var state=BladeStateAccess.of(blade).orElse(null);if(state==null)continue;
                blade.enchant(player.registryAccess().holderOrThrow(Enchantments.POWER),1);state.setProudSoulCount(1000);state.setBroken(false);state.setSealed(false);blade.setDamageValue(0);
                // Named/bewitched fixture, retaining the actual registered SA/SE/model and item identity.
                if(!mods.flammpfeil.slashblade.item.SwordType.from(blade).contains(mods.flammpfeil.slashblade.item.SwordType.BEWITCHED))blade.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("input contract"));
                player.setItemInHand(InteractionHand.MAIN_HAND,blade);LegacyRangeAttack.clear(player);joined.clear();
                var input=player.getData(CapabilityInputState.INPUT_STATE);input.getCommands().clear();
                input.getCommands().add(InputCommand.M_DOWN);NeoForge.EVENT_BUS.post(new InputCommandEvent(player,input,EnumSet.noneOf(InputCommand.class),EnumSet.of(InputCommand.M_DOWN)));
                check(joined.isEmpty(),"no native or legacy sword on key-down "+definition.key());
                InputClock.next(player);input.getCommands().clear();NeoForge.EVENT_BUS.post(new InputCommandEvent(player,input,EnumSet.of(InputCommand.M_DOWN),EnumSet.noneOf(InputCommand.class)));
                check(joined.size()==1 && (joined.getFirst() instanceof LegacyPhantomSword || joined.getFirst() instanceof LegacySummonedBlade),"exactly one sword across all native/legacy types "+definition.key());
                rows.add(Map.of("blade",definition.key().location().toString(),"spawned",joined.size(),"type",net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(joined.getFirst().getType()).toString()));
                joined.forEach(Entity::discard);
            }
            check(!rows.isEmpty(),"nonzero registered blade input cases");
        } finally {joined.forEach(Entity::discard);NeoForge.EVENT_BUS.unregister(collect);LegacyRangeAttack.clear(player);player.getData(CapabilityInputState.INPUT_STATE).getCommands().clear();player.setItemInHand(InteractionHand.MAIN_HAND,original);}
    }
    private static void rank(ServerPlayer player,Map<String,Object> report) {
        var blade=player.getMainHandItem();var state=BladeStateAccess.of(blade).orElseThrow();state.setBroken(false);state.setSealed(false);
        var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);long now=player.level().getGameTime();rank.setRawRankPoint(3*rank.getUnitCapacity()+60);rank.setLastUpdte(now-10);
        long raw=rank.getRawRankPoint(),last=rank.getLastUpdate();state.setAttackAmplifier(-100);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player,net.minecraft.world.level.Level.NETHER,net.minecraft.world.level.Level.OVERWORLD));
        check(state.getAttackAmplifier()==0 && rank.getRawRankPoint()==raw && rank.getLastUpdate()==last,"dimension event refreshes damage without awarding or rewriting rank");
        rank.setRawRankPoint(0);NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));check(state.getBaseAttackModifier()+state.getAttackAmplifier()==2,"login preserves r87 low rank total player damage 3");
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(player,false));check(rank.getRawRankPoint()==0,"respawn does not grant pre-death rank");
        report.put("rank_lifecycle_server_events",Map.of("checks",3,"real_client_dimension_transition",false));
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError("batch/phantom: "+message);}
}
