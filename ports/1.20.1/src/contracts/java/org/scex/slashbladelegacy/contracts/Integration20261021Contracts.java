package org.scex.slashbladelegacy.contracts;

import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.TickEvent;
import org.scex.slashbladelegacy.*;

/** Focused contracts for the recovered gameplay changes and transient rank cooldowns. */
final class Integration20261021Contracts {
    private static final Vec3 ORIGIN=new Vec3(12,180,-12);
    private static final List<Map<String,Object>> checks=new ArrayList<>();
    private static final List<Entity> entities=new ArrayList<>();
    private static final Map<BlockPos,BlockState> blocks=new LinkedHashMap<>();
    static void run(MinecraftServer server,Map<String,Object> report) {
        checks.clear();report.put("suite","integration-20261021");report.put("integration_20261021",checks);
        var level=server.overworld();
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"Integration21"));
        player.connection=new ContractItems.PacketSink(server,player);
        level.addNewPlayer(player);
        boolean friendly=LegacyCompat.FRIENDLY_TARGETS.get();LegacyCompat.FRIENDLY_TARGETS.set(false);
        try {
            reset(player);
            var cow=EntityType.COW.create(level);cow.setPos(ORIGIN.add(0,0,5));cow.setNoAi(true);add(player,cow);
            player.setXRot(8);input(player,true);
            var state=BladeStateAccess.of(player.getMainHandItem()).orElseThrow();
            check("manual input locks a passive target without making it attackable",state.getTargetEntity(level)==cow && !LegacyTargets.attackable(player,cow));
            cow.discard();input(player,false);
            check("release clears the stored ID after the target is removed",state.getTargetEntityId()==-1);
            input(player,true);
            var later=EntityType.COW.create(level);later.setPos(ORIGIN.add(0,0,5));later.setNoAi(true);add(player,later);
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));
            check("held lock retries when a target appears",state.getTargetEntity(level)==later);
            input(player,false);
            check("release clears a live target",state.getTargetEntityId()==-1);

            reset(player);var target=target(player,4.5);
            var sword=phantom(player,target);sword.tick();
            check("ordinary phantom sword records an accepted natural impact",sword.stuckTarget()==target && target.getHealth()<200 && player.position().equals(ORIGIN));
            wall(player,2);state=BladeStateAccess.of(player.getMainHandItem()).orElseThrow();state.setProudSoulCount(0);
            check("fixture blocks the player's line of sight",!player.hasLineOfSight(target));
            check("stuck sword allows a direct wall-crossing trick with zero souls",LegacyAirTrick.launch(player,target)
                    && player.position().distanceTo(target.position())<2 && state.getProudSoulCount()==0 && state.getTargetEntity(level)==target);
            player.setPos(ORIGIN);
            check("same sword cannot trick again before five ticks",!LegacyAirTrick.launch(player,target) && player.position().equals(ORIGIN));
            for(int i=0;i<5;i++)InputClock.next(player);
            check("same sword becomes available after five ticks",LegacyAirTrick.launch(player,target) && state.getProudSoulCount()==0);
            player.setPos(ORIGIN);target.discard();
            check("removed target cannot be used as a trick anchor",!LegacyAirTrick.launch(player,null) && player.position().equals(ORIGIN));

            reset(player);target=target(player,6);wall(player,2);
            var sb=new LegacySummonedBlade(SummonedBladeMode.BLADE.get(),level);
            sb.initialize(player,2,0xFFFFFF,LegacyRangeAttack.sourceId(player.getMainHandItem()),null);
            sb.setPos(ORIGIN.add(0,.8,0));add(player,sb);sb.tick();
            var saved=new CompoundTag();sb.addAdditionalSaveData(saved);
            check("unlocked SB acquires an enemy beyond the wall",!player.hasLineOfSight(target)
                    && saved.hasUUID("LegacyTarget") && saved.getUUID("LegacyTarget").equals(target.getUUID()));
            for(int i=0;i<60 && !sb.isRemoved() && sb.stuckTarget()==null;i++)sb.tick();
            check("SB travels through terrain and attaches to the acquired enemy",sb.stuckTarget()==target && target.getHealth()<200);
            state=BladeStateAccess.of(player.getMainHandItem()).orElseThrow();state.setProudSoulCount(0);
            check("accepted SB impact also supplies the direct trick anchor",LegacyAirTrick.launch(player,target) && state.getProudSoulCount()==0);

            reset(player);target=target(player,4.5);wall(player,2);sword=phantom(player,target);sword.tick();
            check("entity hit takes precedence over this step's block hit",sword.stuckTarget()==target && target.getHealth()<200);
            reset(player);target=target(player,4.5);wall(player,2);var canceled=phantom(player,target);
            Consumer<ProjectileImpactEvent> cancel=event->{if(event.getProjectile()==canceled && event.getRayTraceResult() instanceof EntityHitResult)event.setCanceled(true);};
            MinecraftForge.EVENT_BUS.addListener(cancel);
            try {canceled.tick();} finally {MinecraftForge.EVENT_BUS.unregister(cancel);}
            saved=new CompoundTag();canceled.addAdditionalSaveData(saved);
            check("canceled entity impact still stops at the same step's block",target.getHealth()==200 && saved.getBoolean("LegacyBlocked") && canceled.getZ()<ORIGIN.z+3);

            reset(player);
            var rank=player.getCapability(CapabilityConcentrationRank.RANK_POINT).resolve().orElseThrow();long unit=rank.getUnitCapacity();
            rank.setRawRankPoint(0);rank.setLastUpdte(level.getGameTime());
            LegacyRank.awardAction(player,rank,"integration_a",.3f);LegacyRank.awardAction(player,rank,"integration_a",.3f);
            check("first award and same-tick repeat keep the original rank rule",rank.getRawRankPoint()==31*unit/100);
            LegacyRank.awardAction(player,rank,"integration_b",.3f);LegacyRank.clearCooldown(player,"integration_a");
            LegacyRank.awardAction(player,rank,"integration_a",.3f);LegacyRank.awardAction(player,rank,"integration_b",.3f);
            check("clearing one action preserves another action's cooldown",rank.getRawRankPoint()==92*unit/100);
            LegacyRank.clearCooldowns(player);LegacyRank.awardAction(player,rank,"integration_b",.3f);
            check("clearing all cooldowns restores the next full award",rank.getRawRankPoint()==122*unit/100);
            player.getPersistentData().putLong("slashblade_legacy_compat.rank_cd.old",level.getGameTime()+20);
            player.getPersistentData().putBoolean("integration_keep",true);LegacyRank.synchronize(player);
            check("synchronize removes legacy cooldown keys and preserves unrelated data",player.getPersistentData().getAllKeys().stream().noneMatch(k->k.startsWith("slashblade_legacy_compat.rank_cd."))
                    && player.getPersistentData().getBoolean("integration_keep") && rank.getRawRankPoint()==122*unit/100);
        } finally {
            cleanup(player);LegacyRank.clearCooldowns(player);LegacyAirTrick.clear(player);LegacyCompat.FRIENDLY_TARGETS.set(friendly);
            player.discard();report.put("integration_checks",checks.size());
        }
    }
    private static void check(String name,boolean passed) {checks.add(Map.of("name",name,"passed",passed));if(!passed)throw new AssertionError(name);}
    private static void add(ServerPlayer player,Entity entity) {entities.add(entity);if(!player.level().addFreshEntity(entity))throw new AssertionError("Fixture entity join rejected");}
    private static void cleanup(ServerPlayer player) {entities.forEach(Entity::discard);entities.clear();blocks.forEach((pos,state)->player.level().setBlockAndUpdate(pos,state));blocks.clear();}
    private static void reset(ServerPlayer player) {
        cleanup(player);LegacyAirTrick.clear(player);LegacyRank.clearCooldowns(player);LegacyRangeAttack.clear(player);
        player.setPos(ORIGIN);player.setYRot(0);player.setXRot(0);player.setDeltaMovement(Vec3.ZERO);player.setOnGround(false);
        player.getPersistentData().remove(LegacyMode.MODERN);player.getCapability(CapabilityInputState.INPUT_STATE).resolve().orElseThrow().getCommands().clear();
        var blade=new ItemStack(SlashBladeItems.SLASHBLADE.get());var state=BladeStateAccess.of(blade).orElseThrow();
        state.setDefaultBewitched(true);state.setProudSoulCount(100);blade.enchant(Enchantments.POWER_ARROWS,1);
        player.setItemInHand(InteractionHand.MAIN_HAND,blade);LegacyMode.bindInventory(player);
    }
    private static void input(ServerPlayer player,boolean held) {
        InputClock.next(player);var input=player.getCapability(CapabilityInputState.INPUT_STATE).resolve().orElseThrow();var old=input.getCommands().clone();
        var next=held?EnumSet.of(InputCommand.SNEAK):EnumSet.noneOf(InputCommand.class);
        input.getCommands().clear();input.getCommands().addAll(next);InputCommandEvent.onInputChange(player,input,old,next);
    }
    private static Zombie target(ServerPlayer player,double z) {
        var target=EntityType.ZOMBIE.create(player.level());target.setPos(ORIGIN.add(0,0,z));target.setNoAi(true);target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);target.setHealth(200);add(player,target);return target;
    }
    private static LegacyPhantomSword phantom(ServerPlayer player,LivingEntity target) {
        var sword=new LegacyPhantomSword(SummonedBladeMode.SWORD.get(),player.level());
        sword.initialize(player,LegacyRangeAttack.sourceId(player.getMainHandItem()),LegacyRangeAttack.Art.SINGLE,0,2,0xFFFFFF,target,null,false);
        sword.setPos(ORIGIN.add(0,.8,.2));sword.setDeltaMovement(0,0,5);add(player,sword);return sword;
    }
    private static void wall(ServerPlayer player,int z) {
        for(int x=-1;x<=1;x++)for(int y=0;y<=3;y++) {
            var pos=BlockPos.containing(ORIGIN).offset(x,y,z);blocks.putIfAbsent(pos,player.level().getBlockState(pos));
            player.level().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
        }
    }
}
