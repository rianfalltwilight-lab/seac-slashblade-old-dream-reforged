package org.scex.slashbladelegacy;

import java.util.*;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.entity.IShootable;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-owned player preference. Inventory markers carry item-only query context, not ownership. */
public final class LegacyMode {
    public static final String MODERN="slashblade_legacy_compat.modern_mode";
    private static final String EPOCH="slashblade_legacy_compat.mode_epoch",ACTION_OWNER="slashblade_legacy_compat.action_owner";
    private static final ThreadLocal<Boolean> CONTEXT=new ThreadLocal<>();
    private static final Map<UUID,Boolean> CLIENT=new HashMap<>(); // client main thread only
    private static final Map<ServerPlayer,Boolean> PENDING=new WeakHashMap<>();
    private static final Map<ServerPlayer,Long> REQUESTED=new WeakHashMap<>();
    private static final Map<UUID,Set<Entity>> OWNED=new HashMap<>();
    private static final Set<Entity> UNCLAIMED=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final EnumSet<InputCommand> HELD=EnumSet.of(InputCommand.M_DOWN,InputCommand.R_DOWN,InputCommand.L_DOWN,InputCommand.SPRINT);
    private LegacyMode(){}
    public interface RankMode { void legacy$modern(boolean modern); boolean legacy$modern(); }
    public static boolean modern(Entity entity) {
        if(!(entity instanceof Player player))return false;
        return player.level().isClientSide?CLIENT.getOrDefault(player.getUUID(),false):player.getPersistentData().getBoolean(MODERN);
    }
    public static boolean legacy(Entity entity){return enabled(entity,LegacyCompat.LEGACY_COMBAT);}
    public static boolean enabled(Entity entity,ModConfigSpec.BooleanValue option){return !modern(entity) && LegacyCompat.isEnabled(option);}
    public static boolean legacy(ItemStack blade){return enabled(blade,LegacyCompat.LEGACY_COMBAT);}
    public static boolean enabled(ItemStack blade,ModConfigSpec.BooleanValue option){
        Boolean context=CONTEXT.get();
        boolean modern=context!=null?context:blade.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).contains(MODERN);
        return !modern && LegacyCompat.isEnabled(option);
    }
    public static boolean legacyRank(IConcentrationRank rank){
        return !(rank instanceof RankMode mode && mode.legacy$modern()) && LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT)
                && LegacyCompat.isEnabled(LegacyCompat.LEGACY_RANK);
    }
    public static Scope context(Entity user){return new Scope(modern(user));}
    public static final class Scope implements AutoCloseable {
        private final Boolean previous;
        private Scope(boolean modern){previous=CONTEXT.get();CONTEXT.set(modern);}
        @Override public void close(){if(previous==null)CONTEXT.remove();else CONTEXT.set(previous);}
    }
    public static void bindRank(Player player){
        var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);
        if(rank instanceof RankMode mode)mode.legacy$modern(modern(player));
    }
    public static void bind(ItemStack blade,Entity user){
        var state=BladeStateAccess.of(blade).orElse(null);if(state==null)return;
        boolean modern=modern(user);
        var data=blade.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY);
        if(data.contains(MODERN)==modern)return;
        CustomData.update(DataComponents.CUSTOM_DATA,blade,t->{if(modern)t.putBoolean(MODERN,true);else t.remove(MODERN);});
        // A blade transferred between users must not bring the previous user's live combo/charge.
        state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(user.level().getGameTime());state.setOnClick(false);
        state.setAttackAmplifier(0);LegacyAdditionalAttack.clearCharged(blade);
    }
    public static void bindInventory(Player player){
        bindRank(player);
        for(int i=0;i<player.getInventory().getContainerSize();i++)bind(player.getInventory().getItem(i),player);
    }
    public static void receive(Player local,UUID id,boolean modern){
        CLIENT.put(id,modern);
        var player=local.level().getPlayerByUUID(id);if(player!=null){bindInventory(player);player.swinging=false;player.swingTime=0;player.attackAnim=0;player.oAttackAnim=0;}
    }
    public static void clearClient(){CLIENT.clear();}
    public static boolean pending(ServerPlayer player){return PENDING.containsKey(player);}
    public static void request(ServerPlayer player){
        long tick=player.server.overworld().getGameTime();
        Long last=REQUESTED.get(player);if(last!=null && tick-last>=0 && tick-last<10)return;
        REQUESTED.put(player,tick);
        if(!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT)){message(player,"disabled");return;}
        if(PENDING.remove(player)!=null){message(player,"cancelled");return;}
        PENDING.put(player,!modern(player));
        if(!finish(player))message(player,"waiting");
    }
    public static boolean ready(ServerPlayer player){
        if(!player.isAlive() || !player.onGround() || player.isUsingItem() || player.swinging || player.containerMenu!=player.inventoryMenu)return false;
        var input=player.getData(CapabilityInputState.INPUT_STATE);
        if(input.getCommands().stream().anyMatch(HELD::contains))return false;
        var data=player.getPersistentData();long now=player.level().getGameTime();
        if(data.getInt("sb.avoid.counter")>0 || data.getInt("sb.avoid.trickup")>0 || data.getInt("sb.airtrick.counter")>0
                || data.contains("SB.AvoidTimeout") && now<=data.getLong("SB.AvoidTimeout")+1)return false;
        for(var blade:List.of(player.getMainHandItem(),player.getOffhandItem())){
            var state=BladeStateAccess.of(blade).orElse(null);
            if(state!=null){var combo=state.peekCurrentComboStateTicks(player).getValue();if(!combo.equals(ComboStateRegistry.NONE.getId()) && !combo.equals(ComboStateRegistry.STANDBY.getId()))return false;}
        }
        // Only queried while a change is pending. No world scan and no per-attack network round trip.
        var projectiles=OWNED.get(player.getUUID());
        if(projectiles!=null && projectiles.stream().anyMatch(e->blocks(e,player)))return false;
        return UNCLAIMED.stream().noneMatch(e->blocks(e,player));
    }
    private static boolean knownAction(Entity entity){
        String namespace=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace();
        return namespace.equals("slashblade") || namespace.equals(LegacyCompat.MOD_ID);
    }
    private static boolean dormant(Entity entity){return knownAction(entity) && entity.level() instanceof net.minecraft.server.level.ServerLevel level && !level.isPositionEntityTicking(entity.blockPosition());}
    private static boolean blocks(Entity entity,Player player){return !entity.isRemoved() && owner(entity)==player && !dormant(entity);}
    private static void retireDormant(ServerPlayer player){
        var actions=new HashSet<Entity>(UNCLAIMED);var owned=OWNED.get(player.getUUID());if(owned!=null)actions.addAll(owned);
        for(var entity:actions)if(!entity.isRemoved() && owner(entity)==player && dormant(entity))entity.discard();
    }
    private static void message(ServerPlayer player,String key){player.displayClientMessage(Component.translatable("slashblade_legacy_compat.mode."+key),true);}
    public static boolean finish(ServerPlayer player){
        if(!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT)){if(PENDING.remove(player)!=null)message(player,"disabled");return false;}
        Boolean next=PENDING.get(player);if(next==null || !ready(player))return false;
        // Let a completed sheathing settle once under its original rules before changing the mode.
        if(legacy(player)){BladeStateAccess.of(player.getMainHandItem()).ifPresent(s->s.resolvCurrentComboState(player));LegacySheathingRepair.tick(player);}
        // Addons can start another action from a sheathing callback. Keep the request queued in that case.
        if(!ready(player))return false;
        PENDING.remove(player);
        // Loaded entities outside simulation distance do not age. They must not hold a request forever.
        retireDormant(player);
        clearTransient(player);
        player.getPersistentData().putBoolean(MODERN,next);
        player.getPersistentData().putLong(EPOCH,player.getPersistentData().getLong(EPOCH)+1);bindInventory(player);
        for(var blade:List.of(player.getMainHandItem(),player.getOffhandItem()))BladeStateAccess.of(blade).ifPresent(s->{s.setComboSeq(ComboStateRegistry.NONE.getId());s.setOnClick(false);});
        player.swinging=false;player.swingTime=0;player.attackAnim=0;player.oAttackAnim=0;
        LegacyDamage.update(player);sync(player);player.inventoryMenu.broadcastChanges();
        var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);
        PacketDistributor.sendToPlayer(player,new mods.flammpfeil.slashblade.network.RankSyncMessage(rank.getRankPoint(player.level().getGameTime())));
        message(player,next?"modern":"legacy");return true;
    }
    private static void clearTransient(Player player){
        LegacyRangeAttack.clear(player);LegacySuperArts.clear(player);LegacyJustGuard.clear(player);LegacyCombat.clear(player);LegacySheathingRepair.clear(player);LegacyInputBudget.clear(player);SummonedBladeMode.clear(player);
        var data=player.getPersistentData();
        var scheduler=player.getData(CapabilityInputState.INPUT_STATE).getScheduler();
        var queue=((org.scex.slashbladelegacy.mixin.LegacySchedulerAccessor)scheduler).legacy$queue();
        for(String key:List.of("scex_legacy_range","SpiralSwords","StormSwords","BlisteringSwords","HeavyRainSwords","sendPartical","chargeSuperSA"))queue.remove(key);
        for(String key:List.of("slashblade_legacy_compat.spiral","slashblade_legacy_compat.spiral_until","slashblade_legacy_compat.blistering_until","SB.AvoidTimeout","SB.AvoidComboTimeout","SB.AvoidCombo"))data.remove(key);
    }
    private static void sync(ServerPlayer player){PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,new LegacyModePayload.State(player.getUUID(),modern(player)));}
    private static Entity owner(Entity entity){return entity instanceof IShootable s?s.getShooter():null;}
    private static boolean currentAction(Entity entity){
        String namespace=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace();
        if(!namespace.equals("slashblade") && !namespace.equals(LegacyCompat.MOD_ID))return true;
        var data=entity.getPersistentData();var shooter=owner(entity);
        Player player=shooter instanceof Player p?p:data.hasUUID(ACTION_OWNER)?entity.getServer().getPlayerList().getPlayer(data.getUUID(ACTION_OWNER)):null;
        if(player==null)return true;
        if(data.hasUUID(ACTION_OWNER) && data.getUUID(ACTION_OWNER).equals(player.getUUID()))
            return data.getLong(EPOCH)==player.getPersistentData().getLong(EPOCH);
        // Also handle old saves made before actions carried an epoch.
        if(namespace.equals(LegacyCompat.MOD_ID) && modern(player))return false;
        data.putUUID(ACTION_OWNER,player.getUUID());data.putLong(EPOCH,player.getPersistentData().getLong(EPOCH));return true;
    }
    private static boolean track(Entity entity){
        if(!currentAction(entity)){entity.discard();return false;}
        var owner=owner(entity);if(owner instanceof Player)OWNED.computeIfAbsent(owner.getUUID(),ignored->Collections.newSetFromMap(new IdentityHashMap<>())).add(entity);
        else if(owner==null)UNCLAIMED.add(entity);
        return true;
    }
    public static void register(){
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.ItemTooltipEvent e)->{
            if(!modern(e.getEntity()))return;
            BladeStateAccess.of(e.getItemStack()).ifPresent(s->{
                var key=s.getSlashArtsKey();var art=LegacyArts.resolve(key);
                if(key!=null && key.getNamespace().equals(LegacyCompat.MOD_ID) && (art==LegacyArts.Art.WITHER || art==LegacyArts.Art.MAXIMUM))
                    e.getToolTip().add(Component.translatable("slashblade_legacy_compat.mode.fallback_sa"));
            });
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,(PlayerTickEvent.Pre e)->bindInventory(e.getEntity()));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,(PlayerTickEvent.Post e)->{if(e.getEntity() instanceof ServerPlayer p && pending(p))finish(p);});
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,(PlayerEvent.PlayerLoggedInEvent e)->{
            if(e.getEntity() instanceof ServerPlayer p){bindInventory(p);sync(p);for(var other:p.server.getPlayerList().getPlayers())if(other!=p)PacketDistributor.sendToPlayer(p,new LegacyModePayload.State(other.getUUID(),modern(other)));}
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,(PlayerEvent.Clone e)->{e.getEntity().getPersistentData().putBoolean(MODERN,modern(e.getOriginal()));e.getEntity().getPersistentData().putLong(EPOCH,e.getOriginal().getPersistentData().getLong(EPOCH));PENDING.remove(e.getOriginal());REQUESTED.remove(e.getOriginal());clearTransient(e.getOriginal());});
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,(PlayerEvent.PlayerRespawnEvent e)->{if(e.getEntity() instanceof ServerPlayer p){clearTransient(p);bindInventory(p);sync(p);}});
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,(PlayerEvent.PlayerChangedDimensionEvent e)->{if(e.getEntity() instanceof ServerPlayer p){PENDING.remove(p);clearTransient(p);bindInventory(p);sync(p);}});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking e)->{if(e.getEntity() instanceof ServerPlayer p && e.getTarget() instanceof Player other)PacketDistributor.sendToPlayer(p,new LegacyModePayload.State(other.getUUID(),modern(other)));});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e)->{PENDING.remove(e.getEntity());REQUESTED.remove(e.getEntity());clearTransient(e.getEntity());});
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e)->{if(!e.getLevel().isClientSide && e.getEntity() instanceof IShootable && !track(e.getEntity()))e.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener((EntityLeaveLevelEvent e)->{
            if(e.getLevel().isClientSide || !(e.getEntity() instanceof IShootable))return;var entity=e.getEntity();UNCLAIMED.remove(entity);
            OWNED.values().forEach(set->set.remove(entity));OWNED.values().removeIf(Set::isEmpty);
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e)->{
            var waiting=new ArrayList<>(UNCLAIMED);UNCLAIMED.clear();for(var entity:waiting)if(!entity.isRemoved()){
                if(owner(entity)!=null)track(entity);else if(entity.tickCount<5)UNCLAIMED.add(entity);
            }
        });
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload e)->{if(!e.getLevel().isClientSide()){UNCLAIMED.removeIf(x->x.level()==e.getLevel());OWNED.values().forEach(s->s.removeIf(x->x.level()==e.getLevel()));OWNED.values().removeIf(Set::isEmpty);}});
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e)->{PENDING.clear();REQUESTED.clear();OWNED.clear();UNCLAIMED.clear();});
    }
}
