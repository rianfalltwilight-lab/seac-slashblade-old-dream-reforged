package org.scex.slashbladelegacy.contracts;

import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.scex.slashbladelegacy.*;

/** Counts every summoned-sword subtype on the real input bus and records its origin.
 * Scheduler time is advanced explicitly; this suite is not a GUI/network latency test. */
final class PhantomPackContracts {
    static void run(MinecraftServer server,Map<String,Object> report) {
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"PhantomPack"));
        player.setPos(-8,160,-8);player.setOnGround(true);server.overworld().addNewPlayer(player);
        var target=EntityType.ZOMBIE.create(server.overworld());target.setPos(-8,160,0);target.setNoAi(true);server.overworld().addFreshEntity(target);
        var rows=new ArrayList<Map<String,Object>>();var failures=new ArrayList<String>();
        report.put("phantom_pack_cases",rows);report.put("phantom_pack_failures",failures);
        report.put("loaded_mods",net.neoforged.fml.ModList.get().getMods().stream().map(m->m.getModId()+"@"+m.getVersion()).sorted().toList());
        var joined=new ArrayList<EntityAbstractSummonedSword>();var origins=new ArrayList<Map<String,Object>>();
        Consumer<EntityJoinLevelEvent> collect=e->{if(e.getLevel()==player.level() && e.getEntity() instanceof EntityAbstractSummonedSword sword) {
            joined.add(sword);origins.add(Map.of("type",BuiltInRegistries.ENTITY_TYPE.getKey(sword.getType()).toString(),"class",sword.getClass().getName(),
                "position",List.of(sword.getX(),sword.getY(),sword.getZ()),"owner_at_join",sword.getOwner()==player,
                "stack",StackWalker.getInstance().walk(s->s.limit(24).map(Object::toString).toList())));
        }};
        NeoForge.EVENT_BUS.addListener(collect);
        try {
            for(var definition:server.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements().toList()) {
                for(String action:List.of("tap","spiral","storm","blistering","rain","rapid_taps")) {
                    var blade=definition.value().getBlade(server.registryAccess());var state=BladeStateAccess.of(blade).orElse(null);if(state==null)continue;
                    blade.enchant(server.registryAccess().holderOrThrow(Enchantments.POWER),1);state.setProudSoulCount(1000);state.setBroken(false);state.setSealed(false);blade.setDamageValue(0);
                    if(!mods.flammpfeil.slashblade.item.SwordType.from(blade).contains(mods.flammpfeil.slashblade.item.SwordType.BEWITCHED))blade.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("input fixture"));
                    player.setItemInHand(InteractionHand.MAIN_HAND,blade);state.setTargetEntityId(target.getId());
                    var input=player.getData(CapabilityInputState.INPUT_STATE);input.getCommands().clear();input.getLastPressTimes().clear();LegacyRangeAttack.clear(player);
                    player.getPersistentData().remove("slashblade_legacy_compat.spiral");player.getPersistentData().remove("slashblade_legacy_compat.spiral_until");player.getPersistentData().remove("slashblade_legacy_compat.blistering_until");
                    var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);rank.setRawRankPoint(0);rank.setLastUpdte(player.level().getGameTime());
                    joined.clear();origins.clear();
                    EnumSet<InputCommand> keys=EnumSet.of(InputCommand.M_DOWN);
                    if(action.equals("storm"))keys.addAll(EnumSet.of(InputCommand.SNEAK,InputCommand.BACK));
                    if(action.equals("blistering") || action.equals("rain"))keys.addAll(EnumSet.of(InputCommand.SNEAK,InputCommand.FORWARD));
                    if(action.equals("rain"))input.getLastPressTimes().put(InputCommand.BACK,player.level().getGameTime());
                    press(player,keys);int onDown=joined.size();
                    int hold=action.equals("tap") || action.equals("rapid_taps")?1:12;
                    for(int i=0;i<hold;i++)clock(player);
                    int beforeRelease=joined.size();press(player,EnumSet.noneOf(InputCommand.class));
                    if(action.equals("rapid_taps"))for(int i=1;i<12;i++){clock(player);press(player,keys);clock(player);press(player,EnumSet.noneOf(InputCommand.class));}
                    for(int i=0;i<14;i++)clock(player);
                    int expected=switch(action){case "spiral","storm"->7;case "blistering"->4;case "rain"->21;case "rapid_taps"->12;default->1;};
                    int cost=switch(action){case "spiral","storm","rain"->11;case "blistering"->10;case "rapid_taps"->12;default->1;};
                    boolean passed=onDown==0 && joined.size()==expected && state.getProudSoulCount()==1000-cost
                        && joined.stream().allMatch(s->s instanceof LegacyPhantomSword || s instanceof LegacySummonedBlade);
                    var row=new LinkedHashMap<String,Object>();row.put("blade",definition.key().location().toString());row.put("action",action);row.put("on_down",onDown);row.put("before_release",beforeRelease);row.put("expected_total",expected);row.put("total",joined.size());row.put("souls_used",1000-state.getProudSoulCount());row.put("passed",passed);row.put("spawns",new ArrayList<>(origins));rows.add(row);
                    if(!passed)failures.add(definition.key().location()+"/"+action);
                    joined.forEach(Entity::discard);
                }
            }
        } finally {
            joined.forEach(Entity::discard);NeoForge.EVENT_BUS.unregister(collect);LegacyRangeAttack.clear(player);target.discard();player.discard();report.put("phantom_pack_checks",rows.size());
        }
        if(rows.isEmpty() || !failures.isEmpty())throw new AssertionError("Phantom input failures: "+failures);
    }
    private static void clock(ServerPlayer player){InputClock.next(player);player.getData(CapabilityInputState.INPUT_STATE).getScheduler().onTick(player);}
    private static void press(ServerPlayer player,EnumSet<InputCommand> keys){
        var state=player.getData(CapabilityInputState.INPUT_STATE);var before=state.getCommands().clone();
        for(var key:keys)if(!before.contains(key))state.getLastPressTimes().put(key,player.level().getGameTime());
        state.getCommands().clear();state.getCommands().addAll(keys);NeoForge.EVENT_BUS.post(new InputCommandEvent(player,state,before,keys.clone()));
    }
}
