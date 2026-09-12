package org.scex.slashbladelegacy.contracts;

import java.util.*;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicInteger;
import com.mojang.authlib.GameProfile;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.RegistryEvents;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.entity.*;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.registry.*;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.inventory.AnvilMenu;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.scex.slashbladelegacy.*;

final class DualModeContracts {
    private static int checks;
    private static final List<Entity> spawned=new ArrayList<>();
    static void run(MinecraftServer server,Map<String,Object> report){
        checks=0;var a=player(server,"ModeLegacy",-8);var b=player(server,"ModeModern",8);
        Consumer<EntityJoinLevelEvent> collect=e->{if(!e.getLevel().isClientSide && e.getEntity() instanceof IShootable)spawned.add(e.getEntity());};
        NeoForge.EVENT_BUS.addListener(collect);
        var rows=new ArrayList<String>();report.put("dual_mode_results",rows);
        try {
            check(LegacyMode.legacy(a) && LegacyMode.legacy(b),"old worlds default legacy");
            var blade=b.getMainHandItem();var state=BladeStateAccess.of(blade).orElseThrow();
            state.setProudSoulCount(1000);state.setKillCount(1234);state.setRefine(17);blade.setDamageValue(13);var sa=state.getSlashArtsKey();var enchant=blade.get(DataComponents.ENCHANTMENTS);
            LegacyMode.request(b);
            check(LegacyMode.modern(b) && !LegacyMode.pending(b) && LegacyMode.legacy(a),"one player switches without changing peer or global config");
            check(LegacyCompat.LEGACY_COMBAT.get(),"server configuration untouched");
            check(state.getProudSoulCount()==1000 && state.getKillCount()==1234 && state.getRefine()==17 && blade.getDamageValue()==13 && state.getSlashArtsKey().equals(sa) && blade.get(DataComponents.ENCHANTMENTS).equals(enchant),"switch preserves all permanent progression");
            check(!LegacyMode.legacy(blade) && LegacyMode.legacy(a.getMainHandItem()),"item-only damage and enchant gates isolated");
            var rankA=a.getData(CapabilityConcentrationRank.RANK_POINT);var rankB=b.getData(CapabilityConcentrationRank.RANK_POINT);long now=a.level().getGameTime();
            rankA.setRawRankPoint(1680);rankB.setRawRankPoint(1680);rankA.setLastUpdte(now);rankB.setLastUpdte(now);
            check(rankA.getRank(now).level==6 && rankB.getRank(now).level==7,"different rank thresholds on same server tick");
            check(rankA.getMaxCapacity()==1797 && rankB.getMaxCapacity()==1799,"rank capacity follows owner");
            check(BladeStateAccess.of(a.getMainHandItem()).orElseThrow().getFullChargeTicks(a)==15 && state.getFullChargeTicks(b)==9,"charge windows restored per player");
            check(!LegacySheathingRepair.handles(blade) && !LegacyTaunt.handles(blade) && !LegacyDualWield.active(b),"old auxiliary systems disabled for modern user");
            rows.add("Independent player state, permanent blade data, rank thresholds, charge and auxiliary gates");
            boolean friendly=mods.flammpfeil.slashblade.SlashBladeConfig.FRIENDLY_ENABLE.get();
            try {
                mods.flammpfeil.slashblade.SlashBladeConfig.FRIENDLY_ENABLE.set(false);
                var allay=net.minecraft.world.entity.EntityType.ALLAY.create(a.level());allay.setPos(0,160,-8);
                var condition=mods.flammpfeil.slashblade.util.TargetSelector.test.copy().ignoreLineOfSight();
                check(condition.test(a,allay) && !condition.test(b,allay),"legacy default targeting cannot leak to modern player");
                check(HostileTargeting.SOURCE_CONTEXT.get()==null,"attacker context restored after target query");
            }finally{mods.flammpfeil.slashblade.SlashBladeConfig.FRIENDLY_ENABLE.set(friendly);}
            for(var art:LegacyArts.Art.values())for(var type:List.of(mods.flammpfeil.slashblade.slasharts.SlashArts.ArtsType.Success,mods.flammpfeil.slashblade.slasharts.SlashArts.ArtsType.Super)){
                var alias=mods.flammpfeil.slashblade.registry.SlashArtsRegistry.REGISTRY.get(LegacyArts.key(art));
                check(alias.doArts(type,a).getNamespace().equals(LegacyCompat.MOD_ID) && alias.doArts(type,b).getNamespace().equals("slashblade"),"persisted SA alias mode "+art+" / "+type);
            }
            for(var p:List.of(a,b)){
                var input=p.getData(CapabilityInputState.INPUT_STATE);input.getCommands().clear();input.getLastPressTimes().clear();
                var bs=BladeStateAccess.of(p.getMainHandItem()).orElseThrow();bs.setProudSoulCount(1000);
                CustomData.update(DataComponents.CUSTOM_DATA,p.getMainHandItem(),t->t.putBoolean(SummonedBladeMode.MODE,true));
                spawned.clear();press(p,EnumSet.of(InputCommand.M_DOWN));int down=spawned.size();
                check(down==(p==a?0:1),"key down count "+p.getScoreboardName());
                InputClock.next(p);press(p,EnumSet.noneOf(InputCommand.class));
                check(spawned.size()==1 && (p==a?spawned.getFirst() instanceof LegacySummonedBlade:spawned.getFirst().getClass()==EntityAbstractSummonedSword.class),"release count and exact source type "+p.getScoreboardName());
                int fee=p==a?1:mods.flammpfeil.slashblade.SlashBladeConfig.SUMMON_SWORD_COST.get();
                check(bs.getProudSoulCount()==1000-fee,"one summon fee "+p.getScoreboardName()+": "+bs.getProudSoulCount()+" / fee "+fee);
                discard();
                CustomData.update(DataComponents.CUSTOM_DATA,p.getMainHandItem(),t->t.remove(SummonedBladeMode.MODE));
                bs.setComboSeq(ComboStateRegistry.NONE.getId());bs.setLastActionTime(p.level().getGameTime());
                p.getMainHandItem().getItem().use(p.level(),p,InteractionHand.MAIN_HAND);
                check(bs.getComboSeq().getNamespace().equals(p==a?LegacyCompat.MOD_ID:"slashblade"),"right click combo selects only one system "+p.getScoreboardName());
                p.stopUsingItem();p.swinging=false;bs.setComboSeq(ComboStateRegistry.NONE.getId());input.getCommands().clear();discard();
            }
            rows.add("Old SB release and modern native press each spawn once; right-click graph selection");
            var active=new EntityAbstractSummonedSword(RegistryEvents.SummonedSword,b.level());active.setOwner(b);active.setPos(b.position());b.level().addFreshEntity(active);
            var dormant=new net.minecraft.nbt.CompoundTag();active.save(dormant);
            advance(b,12);LegacyMode.request(b);check(LegacyMode.pending(b) && LegacyMode.modern(b),"live modern sword defers switch");
            advance(b,12);LegacyMode.request(b);check(!LegacyMode.pending(b) && LegacyMode.modern(b),"second accepted key cancels pending switch");
            advance(b,12);LegacyMode.request(b);check(LegacyMode.pending(b),"request queued again");
            active.discard();check(LegacyMode.finish(b) && LegacyMode.legacy(b),"last active sword ending releases switch");discard();
            var reloaded=net.minecraft.world.entity.EntityType.loadEntityRecursive(dormant,b.level(),e->e);
            check(reloaded!=null && !b.level().addFreshEntity(reloaded),"projectile saved in previous mode cannot resume after a switch");
            advance(b,12);b.startUsingItem(InteractionHand.MAIN_HAND);LegacyMode.request(b);
            check(LegacyMode.pending(b) && !LegacyMode.ready(b),"held use blocks transition");b.stopUsingItem();b.swinging=false;
            state.setComboSeq(LegacyCombat.id(LegacyMove.SAYA1));state.setLastActionTime(b.level().getGameTime());
            check(!LegacyMode.ready(b),"unfinished combo blocks transition");advance(b,70);
            var oldJob=new AtomicInteger();var addonJob=new AtomicInteger();var scheduler=b.getData(CapabilityInputState.INPUT_STATE).getScheduler();long time=b.level().getGameTime();
            scheduler.schedule("scex_legacy_range",time+1,(e,q,t)->oldJob.incrementAndGet());scheduler.schedule("test_foreign_job",time+1,(e,q,t)->addonJob.incrementAndGet());
            check(LegacyMode.finish(b) && LegacyMode.modern(b),"completed sheathing commits deferred change");
            advance(b,2);scheduler.onTick(b);check(oldJob.get()==0 && addonJob.get()==1,"old input callbacks retired without removing addon jobs");
            rows.add("Busy sword, cancellation, held input, completed sheathing and precise scheduler cleanup");
            var transferred=b.getMainHandItem();var transferState=BladeStateAccess.of(transferred).orElseThrow();int souls=transferState.getProudSoulCount(),wear=transferred.getDamageValue();
            transferState.setComboSeq(ComboStateRegistry.JUDGEMENT_CUT.getId());LegacyMode.bind(transferred,a);
            check(LegacyMode.legacy(transferred) && transferState.getComboSeq().equals(ComboStateRegistry.NONE.getId()) && transferState.getProudSoulCount()==souls && transferred.getDamageValue()==wear,"transfer changes rules and retires old combo without progression loss");
            LegacyMode.bind(transferred,b);
            var power=b.registryAccess().holderOrThrow(Enchantments.POWER);var fresh=blade(server);fresh.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);
            try(var outer=LegacyMode.context(b)){
                check(fresh.supportsEnchantment(power),"modern actor accepts power through native item query");
                try(var nested=LegacyMode.context(a)){check(!fresh.supportsEnchantment(power),"nested old actor overrides context");}
                check(fresh.supportsEnchantment(power),"context restored after nested callback");
            }
            check(!fresh.supportsEnchantment(power),"no global context leak");
            for(var p:List.of(a,b)){
                var input=fresh.copy();var book=new ItemStack(Items.ENCHANTED_BOOK);EnchantmentHelper.updateEnchantments(book,x->x.set(power,1));
                p.experienceLevel=1000;var menu=new AnvilMenu(77,p.getInventory());menu.getSlot(0).set(input);menu.getSlot(1).set(book);menu.createResult();
                check(menu.getSlot(2).getItem().isEmpty()==(p==a),"actual anvil preview follows operator "+p.getScoreboardName());
                var table=new net.minecraft.world.inventory.EnchantmentMenu(78,p.getInventory());table.getSlot(0).set(fresh.copy());
                check(LegacyMode.legacy(table.getSlot(0).getItem())==(p==a),"enchanting table slot bound to operator "+p.getScoreboardName());
            }
            rows.add("Transferred blades, nested item contexts and actual anvil previews");
            var nativeControl=modernMelee(server,b,false);var nativeMode=modernMelee(server,b,true);
            report.put("modern_melee_control",nativeControl);report.put("modern_melee_mode",nativeMode);
            check(nativeControl.equals(nativeMode),"native-mode left/right melee equals disabled-compat control");
            check(nativeMode.stream().anyMatch(r->((Number)r.get("damage")).doubleValue()>0),"modern mode actually damages a loaded target");
            var cloned=player(server,"ModeClone",16);try {
                NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(cloned,b,true));check(LegacyMode.modern(cloned),"death clone preserves preference");
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(cloned,false));
                check(cloned.getData(CapabilityConcentrationRank.RANK_POINT).getMaxCapacity()==1799,"new rank attachment binds after respawn");
                var saved=new net.minecraft.nbt.CompoundTag();cloned.saveWithoutId(saved);
                var restored=new ServerPlayer(server,server.overworld(),new GameProfile(UUID.randomUUID(),"ModeReload"),net.minecraft.server.level.ClientInformation.createDefault());restored.load(saved);LegacyMode.bindInventory(restored);
                check(LegacyMode.modern(restored) && restored.getData(CapabilityConcentrationRank.RANK_POINT).getMaxCapacity()==1799,"actual player NBT reload restores preference and rank rules");
            }finally{cloned.discard();}
            rows.add("Death clone and player save persistence");
        } finally {discard();NeoForge.EVENT_BUS.unregister(collect);a.discard();b.discard();report.put("dual_mode_checks",checks);}
    }
    private static List<Map<String,Object>> modernMelee(MinecraftServer server,ServerPlayer p,boolean compatibility){
        var settings=new LinkedHashMap<net.neoforged.neoforge.common.ModConfigSpec.BooleanValue,Boolean>();
        var held=p.getMainHandItem();var off=p.getOffhandItem();var result=new ArrayList<Map<String,Object>>();
        var target=net.minecraft.world.entity.EntityType.ZOMBIE.create(p.level());
        try {
            for(var field:LegacyCompat.class.getFields())if(field.getType()==net.neoforged.neoforge.common.ModConfigSpec.BooleanValue.class){var option=(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue)field.get(null);settings.put(option,option.get());if(!compatibility)option.set(false);}
            var blade=blade(server);blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);var state=BladeStateAccess.of(blade).orElseThrow();state.setBroken(false);state.setSealed(false);state.setSpecialEffects(new net.minecraft.nbt.ListTag());state.setComboSeq(ComboStateRegistry.NONE.getId());state.setProudSoulCount(1000);state.setMaxDamage(500);blade.setDamageValue(1);
            p.setItemInHand(InteractionHand.MAIN_HAND,blade);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);LegacyMode.bindInventory(p);p.getData(CapabilityInputState.INPUT_STATE).getCommands().clear();
            var rank=p.getData(CapabilityConcentrationRank.RANK_POINT);rank.setRawRankPoint(0);rank.setLastUpdte(p.level().getGameTime());
            target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);target.setNoAi(true);target.setNoGravity(true);target.setPos(8,160,-6);check(p.level().addFreshEntity(target),"native damage target enters entity query");
            for(int i=0;i<7;i++){
                p.setPos(8,160,-8);p.setYRot(0);p.setXRot(0);p.setOnGround(true);p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);p.resetAttackStrengthTicker();p.swinging=false;
                target.setPos(8,160,-6);target.setOnGround(true);target.setHealth(500);target.invulnerableTime=0;target.hurtTime=0;target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                blade.getItem().inventoryTick(blade,p.level(),p,0,true);
                blade.getAttributeModifiers().forEach(net.minecraft.world.entity.EquipmentSlot.MAINHAND,(a,m)->p.getAttribute(a).addOrUpdateTransientModifier(m));
                int wear=blade.getDamageValue();
                if(i==0){InputClock.next(p);blade.getItem().onLeftClickEntity(blade,p,target);}else InputClock.use(p,blade);
                p.stopUsingItem();String combo=state.getComboSeq().toString();
                // Native melee damages through ticking slash entities; the click itself only spawns them.
                for(int step=0;step<8;step++){
                    InputClock.next(p);blade.getItem().inventoryTick(blade,p.level(),p,0,true);
                    for(var entity:List.copyOf(spawned))if(!entity.isRemoved()){entity.tickCount++;entity.tick();}
                }
                result.add(Map.of("input",i==0?"left":"right-"+i,"combo",combo,"damage",500-target.getHealth(),"wear",blade.getDamageValue()-wear));
            }
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        finally{target.discard();settings.forEach((option,value)->option.set(value));p.setItemInHand(InteractionHand.MAIN_HAND,held);p.setItemInHand(InteractionHand.OFF_HAND,off);p.stopUsingItem();p.swinging=false;discard();}
        return result;
    }
    private static ItemStack blade(MinecraftServer s){return s.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).getOrThrow(ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY,ResourceLocation.parse("slashblade:sange"))).value().getBlade(s.registryAccess());}
    private static ServerPlayer player(MinecraftServer s,String name,int x){
        var p=net.neoforged.neoforge.common.util.FakePlayerFactory.get(s.overworld(),new GameProfile(UUID.randomUUID(),name));p.setPos(x,160,-8);p.setOnGround(true);s.overworld().addNewPlayer(p);
        var blade=blade(s);blade.enchant(s.registryAccess().holderOrThrow(Enchantments.POWER),1);var state=BladeStateAccess.of(blade).orElseThrow();state.setProudSoulCount(1000);state.setMaxDamage(200);state.setBroken(false);state.setSealed(false);blade.setDamageValue(0);p.setItemInHand(InteractionHand.MAIN_HAND,blade);LegacyMode.bindInventory(p);return p;
    }
    private static void advance(ServerPlayer p,int ticks){for(int i=0;i<ticks;i++)InputClock.next(p);}
    private static void discard(){spawned.forEach(Entity::discard);spawned.clear();}
    private static void press(ServerPlayer p,EnumSet<InputCommand> next){var input=p.getData(CapabilityInputState.INPUT_STATE);var old=input.getCommands().clone();for(var c:next)if(!old.contains(c))input.getLastPressTimes().put(c,p.level().getGameTime());input.getCommands().clear();input.getCommands().addAll(next);NeoForge.EVENT_BUS.post(new InputCommandEvent(p,input,old,next));}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError("Dual mode: "+message);}
}
