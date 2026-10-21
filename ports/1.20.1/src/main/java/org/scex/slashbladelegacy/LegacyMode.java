package org.scex.slashbladelegacy;

import java.util.*;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.entity.IShootable;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.network.PacketDistributor;

/** 服务端保存玩家模式。物品标记只提供模式信息，不表示所有权。 */
public final class LegacyMode {
    public static final String MODERN="slashblade_legacy_compat.modern_mode";
    private static final String EPOCH="slashblade_legacy_compat.mode_epoch",ACTION_OWNER="slashblade_legacy_compat.action_owner";
    private static final ThreadLocal<Boolean> CONTEXT=new ThreadLocal<>();
    private static final Map<UUID,Boolean> CLIENT=new HashMap<>(); // 只在客户端主线程访问。
    private static final Map<ServerPlayer,Boolean> PENDING=new WeakHashMap<>();
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
    public static boolean enabled(Entity entity,ForgeConfigSpec.BooleanValue option){return !modern(entity) && LegacyCompat.isEnabled(option);}
    public static boolean legacy(ItemStack blade){return enabled(blade,LegacyCompat.LEGACY_COMBAT);}
    public static boolean enabled(ItemStack blade,ForgeConfigSpec.BooleanValue option){
        Boolean context=CONTEXT.get();
        boolean modern=context!=null?context:blade.hasTag() && blade.getTag().contains(MODERN);
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
        var rank=LegacyCapabilities.rank(player);
        if(rank instanceof RankMode mode)mode.legacy$modern(modern(player));
    }
    public static void bind(ItemStack blade,Entity user){
        if(blade.isEmpty() || !(blade.getItem() instanceof ItemSlashBlade))return;
        boolean modern=modern(user);
        boolean marked=blade.hasTag() && blade.getTag().contains(MODERN);
        if(marked==modern){
            if(!modern && !user.level().isClientSide)LegacyNamedBlades.seed(blade);
            return;
        }
        var state=BladeStateAccess.of(blade).orElseThrow();
        if(modern)blade.getOrCreateTag().putBoolean(MODERN,true);else if(blade.hasTag())blade.getTag().remove(MODERN);
        // 模式变化时清除原用户的连段和蓄力。
        state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(user.level().getGameTime());state.setOnClick(false);
        state.setAttackAmplifier(0);LegacyAdditionalAttack.clearCharged(blade);
        if(!modern && !user.level().isClientSide)LegacyNamedBlades.seed(blade,state);
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
        if(!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT)){message(player,"disabled");return;}
        if(PENDING.remove(player)!=null){message(player,"cancelled");return;}
        PENDING.put(player,!modern(player));
        if(!finish(player))message(player,"waiting");
    }
    private static boolean canSettle(ServerPlayer player){
        if(!player.isAlive() || !player.onGround() || player.isUsingItem() || player.swinging || player.containerMenu!=player.inventoryMenu)return false;
        var input=LegacyCapabilities.input(player);
        if(input.getCommands().stream().anyMatch(HELD::contains))return false;
        var data=player.getPersistentData();long now=player.level().getGameTime();
        return !(data.getInt("sb.avoid.counter")>0 || data.getInt("sb.avoid.trickup")>0 || data.getInt("sb.airtrick.counter")>0
                || data.contains("SB.AvoidTimeout") && now<=data.getLong("SB.AvoidTimeout")+1);
    }
    public static boolean ready(ServerPlayer player){
        if(!canSettle(player) || player.swinging)return false;
        for(var blade:List.of(player.getMainHandItem(),player.getOffhandItem())){
            var state=BladeStateAccess.of(blade).orElse(null);
            if(state!=null){var combo=BladeStateAccess.peek(state,player).getValue();if(!combo.equals(ComboStateRegistry.NONE.getId()) && !combo.equals(ComboStateRegistry.STANDBY.getId()))return false;}
        }
        // 只在等待切换时查询已登记的投射物。
        var projectiles=OWNED.get(player.getUUID());
        if(projectiles!=null && projectiles.stream().anyMatch(e->blocks(e,player)))return false;
        return UNCLAIMED.stream().noneMatch(e->blocks(e,player));
    }
    private static boolean knownAction(Entity entity){
        String namespace=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace();
        return namespace.equals("slashblade") || namespace.equals(LegacyCompat.MOD_ID);
    }
    private static boolean dormant(Entity entity){return knownAction(entity) && entity.level() instanceof net.minecraft.server.level.ServerLevel level && !level.isPositionEntityTicking(entity.blockPosition());}
    private static boolean expired(Entity entity){
        // 模拟距离外的投射物可能停止 tick。
        // 切换模式时检查到期条件。保留下一 tick 的正常到期边界。
        if(entity instanceof LegacyPhantomSword sword)return sword.age()+1>=Math.max(1,sword.lifetime());
        if(entity instanceof LegacyArtEntity art)return art.age()+1>=Math.max(1,art.lifetime());
        if(entity instanceof LegacyDrive drive)return drive.age()+1>=Math.max(1,drive.getLifetime());
        return false;
    }
    private static boolean blocks(Entity entity,Player player){
        if(entity.isRemoved() || owner(entity)!=player)return false;
        if(expired(entity)){entity.discard();return false;}
        return !dormant(entity);
    }
    private static void retireDormant(ServerPlayer player){
        var actions=new HashSet<Entity>(UNCLAIMED);var owned=OWNED.get(player.getUUID());if(owned!=null)actions.addAll(owned);
        for(var entity:actions)if(!entity.isRemoved() && owner(entity)==player && dormant(entity))entity.discard();
    }
    private static void message(ServerPlayer player,String key){player.displayClientMessage(Component.translatable("slashblade_legacy_compat.mode."+key),true);}
    public static boolean finish(ServerPlayer player){
        if(!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT)){if(PENDING.remove(player)!=null)message(player,"disabled");return false;}
        Boolean next=PENDING.get(player);if(next==null || !canSettle(player))return false;
        // 模式切换前，按原模式结算已完成的收刀。
        if(legacy(player)){
            // 副手没有主手物品 tick。此处结算两手已超时的连段。
            for(var blade:List.of(player.getMainHandItem(),player.getOffhandItem()))
                BladeStateAccess.of(blade).filter(s->timedOut(s,player)).ifPresent(s->s.resolvCurrentComboState(player));
            LegacySheathingRepair.tick(player);
        }
        // 附属可能在收刀回调中开始新动作。此时继续等待。
        if(!ready(player))return false;
        PENDING.remove(player);
        // 移除模拟距离外停止计时的剑技实体，以便完成切换。
        retireDormant(player);
        clearTransient(player);
        player.getPersistentData().putBoolean(MODERN,next);
        player.getPersistentData().putLong(EPOCH,player.getPersistentData().getLong(EPOCH)+1);bindInventory(player);
        for(var blade:List.of(player.getMainHandItem(),player.getOffhandItem()))BladeStateAccess.of(blade).ifPresent(s->{s.setComboSeq(ComboStateRegistry.NONE.getId());s.setOnClick(false);});
        player.swinging=false;player.swingTime=0;player.attackAnim=0;player.oAttackAnim=0;
        LegacyDamage.update(player);sync(player);player.inventoryMenu.broadcastChanges();
        var rank=LegacyCapabilities.rank(player);
        var rankMessage=new mods.flammpfeil.slashblade.network.RankSyncMessage();
        rankMessage.rawPoint=rank.getRankPoint(player.level().getGameTime());
        mods.flammpfeil.slashblade.network.NetworkManager.INSTANCE.send(PacketDistributor.PLAYER.with(()->player),rankMessage);
        message(player,next?"modern":"legacy");return true;
    }
    private static boolean timedOut(mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState state,ServerPlayer player){
        var id=state.getComboSeq();
        if(id.equals(ComboStateRegistry.NONE.getId()) || id.equals(ComboStateRegistry.STANDBY.getId()))return false;
        var combo=ComboStateRegistry.REGISTRY.get().getValue(id);
        return combo!=null && state.getElapsedTime(player)*50L>=combo.getTimeoutMS();
    }
    private static void clearTransient(Player player){
        LegacyRangeAttack.clear(player);LegacySuperArts.clear(player);LegacyJustGuard.clear(player);LegacyCombat.clear(player);LegacySheathingRepair.clear(player);LegacyInputBudget.clear(player);SummonedBladeMode.clear(player);LegacyAirTrick.clear(player);
        var data=player.getPersistentData();
        // 退出事件可能发生在能力失效之后。
        player.getCapability(CapabilityInputState.INPUT_STATE).ifPresent(input->{
            var queue=((org.scex.slashbladelegacy.mixin.LegacySchedulerAccessor)input.getScheduler()).legacy$queue();
            for(String key:List.of("scex_legacy_range","SpiralSwords","StormSwords","BlisteringSwords","HeavyRainSwords","sendPartical","chargeSuperSA"))queue.remove(key);
        });
        for(String key:List.of("slashblade_legacy_compat.spiral","slashblade_legacy_compat.spiral_until","slashblade_legacy_compat.blistering_until","SB.AvoidTimeout","SB.AvoidComboTimeout","SB.AvoidCombo"))data.remove(key);
    }
    private static void sync(ServerPlayer player){LegacyNetwork.sendToTrackingEntityAndSelf(player,new LegacyModePayload.State(player.getUUID(),modern(player)));}
    private static Entity owner(Entity entity){return entity instanceof IShootable s?s.getShooter():null;}
    private static boolean currentAction(Entity entity){
        String namespace=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace();
        if(!namespace.equals("slashblade") && !namespace.equals(LegacyCompat.MOD_ID))return true;
        var data=entity.getPersistentData();var shooter=owner(entity);
        Player player=shooter instanceof Player p?p:data.hasUUID(ACTION_OWNER)?entity.getServer().getPlayerList().getPlayer(data.getUUID(ACTION_OWNER)):null;
        if(player==null)return true;
        if(data.hasUUID(ACTION_OWNER) && data.getUUID(ACTION_OWNER).equals(player.getUUID()))
            return data.getLong(EPOCH)==player.getPersistentData().getLong(EPOCH);
        // 兼容尚未记录动作代次的旧存档。
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
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.ItemTooltipEvent e)->{
            if(!modern(e.getEntity()))return;
            BladeStateAccess.of(e.getItemStack()).ifPresent(s->{
                var key=s.getSlashArtsKey();var art=LegacyArts.resolve(key);
                if(key!=null && key.getNamespace().equals(LegacyCompat.MOD_ID) && (art==LegacyArts.Art.WITHER || art==LegacyArts.Art.MAXIMUM || art==LegacyArts.Art.DIMENSION_SPIRAL))
                    e.getToolTip().add(Component.translatable("slashblade_legacy_compat.mode.fallback_sa"));
            });
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,(PlayerTickEvent e)->{if(e.phase==TickEvent.Phase.START && e.player.isAlive())bindInventory(e.player);});
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,(PlayerTickEvent e)->{if(e.phase==TickEvent.Phase.END && e.player instanceof ServerPlayer p && pending(p))finish(p);});
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,(PlayerEvent.PlayerLoggedInEvent e)->{
            if(e.getEntity() instanceof ServerPlayer p){bindInventory(p);sync(p);for(var other:p.server.getPlayerList().getPlayers())if(other!=p)LegacyNetwork.sendToPlayer(p,new LegacyModePayload.State(other.getUUID(),modern(other)));}
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,(PlayerEvent.Clone e)->{e.getEntity().getPersistentData().putBoolean(MODERN,modern(e.getOriginal()));e.getEntity().getPersistentData().putLong(EPOCH,e.getOriginal().getPersistentData().getLong(EPOCH));PENDING.remove(e.getOriginal());e.getOriginal().reviveCaps();try{clearTransient(e.getOriginal());}finally{e.getOriginal().invalidateCaps();}});
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,(PlayerEvent.PlayerRespawnEvent e)->{if(e.getEntity() instanceof ServerPlayer p){clearTransient(p);bindInventory(p);sync(p);}});
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,(PlayerEvent.PlayerChangedDimensionEvent e)->{if(e.getEntity() instanceof ServerPlayer p){PENDING.remove(p);clearTransient(p);bindInventory(p);sync(p);}});
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.StartTracking e)->{if(e.getEntity() instanceof ServerPlayer p && e.getTarget() instanceof Player other)LegacyNetwork.sendToPlayer(p,new LegacyModePayload.State(other.getUUID(),modern(other)));});
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e)->{PENDING.remove(e.getEntity());clearTransient(e.getEntity());});
        MinecraftForge.EVENT_BUS.addListener((EntityJoinLevelEvent e)->{if(!e.getLevel().isClientSide && e.getEntity() instanceof IShootable && !track(e.getEntity()))e.setCanceled(true);});
        MinecraftForge.EVENT_BUS.addListener((EntityLeaveLevelEvent e)->{
            if(e.getLevel().isClientSide || !(e.getEntity() instanceof IShootable))return;var entity=e.getEntity();UNCLAIMED.remove(entity);
            OWNED.values().forEach(set->set.remove(entity));OWNED.values().removeIf(Set::isEmpty);
        });
        MinecraftForge.EVENT_BUS.addListener((ServerTickEvent e)->{
            if(e.phase!=TickEvent.Phase.END || UNCLAIMED.isEmpty())return;
            var waiting=new ArrayList<>(UNCLAIMED);UNCLAIMED.clear();for(var entity:waiting)if(!entity.isRemoved()){
                if(owner(entity)!=null)track(entity);else if(entity.tickCount<5)UNCLAIMED.add(entity);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((LevelEvent.Unload e)->{if(!e.getLevel().isClientSide()){UNCLAIMED.removeIf(x->x.level()==e.getLevel());OWNED.values().forEach(s->s.removeIf(x->x.level()==e.getLevel()));OWNED.values().removeIf(Set::isEmpty);}});
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e)->{PENDING.clear();OWNED.clear();UNCLAIMED.clear();});
    }
}
