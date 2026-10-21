package org.scex.slashbladelegacy.contracts;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.ability.SlayerStyleArts;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import org.scex.slashbladelegacy.LegacyAirTrick;
import org.scex.slashbladelegacy.LegacyMode;
import org.scex.slashbladelegacy.LegacyPhantomSword;

/** 验证真实输入分流、飞行扫掠和命中后的传送。 */
final class AirTrickContracts {
    private static final Vec3 ORIGIN=new Vec3(-12,180,-12);
    private static final List<Map<String,Object>> checks=new ArrayList<>();
    private static void check(String name,boolean passed) {
        checks.add(Map.of("name",name,"passed",passed));
        if(!passed)throw new AssertionError(name);
    }

    static void run(MinecraftServer server,Map<String,Object> report) {
        checks.clear();report.put("air_trick",checks);
        var level=server.overworld();
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new GameProfile(UUID.randomUUID(),"AirTrickContract"));
        // 保留实际服务端传送，仅丢弃发往测试玩家的网络包。
        player.connection=new ContractItems.PacketSink(server,player);
        level.addNewPlayer(player);
        var swords=new ArrayList<LegacyPhantomSword>();
        var targets=new ArrayList<Zombie>();
        var walls=new ArrayList<BlockPos>();
        Consumer<EntityJoinLevelEvent> collect=event->{
            if(event.getEntity() instanceof LegacyPhantomSword sword && sword.getOwner()==player)swords.add(sword);
        };
        MinecraftForge.EVENT_BUS.addListener(collect);
        try {
            reset(player,swords);
            input(player,false,false);input(player,true,false);
            var state=BladeStateAccess.of(player.getMainHandItem()).orElseThrow();
            check("unlocked fresh special-action input creates one marker and spends one soul",
                    swords.size()==1 && state.getProudSoulCount()==9 && player.position().equals(ORIGIN) && noTrickUp(player));
            input(player,true,false);input(player,true,true);input(player,false,false);
            check("held special-action and release do not repeat",swords.size()==1 && state.getProudSoulCount()==9 && noTrickUp(player));
            input(player,true,false);
            check("next special-action press creates one more marker",swords.size()==2 && state.getProudSoulCount()==8);

            for(String missing:List.of("power","souls","bewitched","unbroken")) {
                reset(player,swords);
                var blade=player.getMainHandItem();state=BladeStateAccess.of(blade).orElseThrow();
                if(missing.equals("power"))blade.removeTagKey("Enchantments");
                if(missing.equals("souls"))state.setProudSoulCount(0);
                if(missing.equals("bewitched"))state.setDefaultBewitched(false);
                if(missing.equals("unbroken"))state.setBroken(true);
                int souls=state.getProudSoulCount();
                input(player,false,false);input(player,true,false);
                check("missing "+missing+" rejects real input without native TrickUp",
                        swords.isEmpty() && state.getProudSoulCount()==souls && player.position().equals(ORIGIN)
                                && player.getDeltaMovement().equals(Vec3.ZERO) && noTrickUp(player));
            }

            reset(player,swords);state=BladeStateAccess.of(player.getMainHandItem()).orElseThrow();
            state.setTargetEntityId(Integer.MAX_VALUE);input(player,true,false);
            check("stale lock falls back to one forward marker",swords.size()==1 && state.getProudSoulCount()==9 && noTrickUp(player));

            reset(player,swords);player.getPersistentData().putBoolean(LegacyMode.MODERN,true);LegacyMode.bindInventory(player);
            input(player,false,false);input(player,true,false);
            check("modern unlocked input retains native TrickUp",swords.isEmpty()
                    && player.getPersistentData().getInt(SlayerStyleArts.AVOID_TRICKUP_PATH)>0
                    && player.getY()>ORIGIN.y && BladeStateAccess.of(player.getMainHandItem()).orElseThrow().getProudSoulCount()==10);

            reset(player,swords);
            var offAxis=target(player,targets,2,6);
            var sword=launch(player,swords,null);player.setYRot(90);
            for(int i=0;i<6;i++)sword.tick();
            check("unlocked marker ignores nearby targets and later owner turns",!sword.isRemoved() && sword.airTrick()
                    && Math.abs(sword.getX()-ORIGIN.x)<.00001 && Math.abs(sword.getY()-player.getEyeY())<.00001
                    && sword.getZ()>ORIGIN.z+6 && offAxis.getHealth()==200 && player.position().equals(ORIGIN));
            offAxis.discard();

            reset(player,swords);wall(player,walls,1,1);
            sword=launch(player,swords,null);sword.setDeltaMovement(0,0,4);sword.tick();
            check("thin wall crossed in one step does not terminate an unembedded marker",!sword.isRemoved()
                    && sword.getZ()>ORIGIN.z+2.25 && player.position().equals(ORIGIN));
            for(int i=0;i<30 && !sword.isRemoved();i++)sword.tick();
            check("unlocked miss expires without teleport",sword.isRemoved() && player.position().equals(ORIGIN));
            clearWalls(player,walls);

            reset(player,swords);wall(player,walls,1,4);
            var nearWall=target(player,targets,1.1,1.9);
            sword=launch(player,swords,null);sword.tick();
            check("thick wall first step enters collision bounds before breaking",!sword.isRemoved()
                    && level.getBlockCollisions(sword,sword.getBoundingBox()).iterator().hasNext() && player.position().equals(ORIGIN));
            sword.tick();
            check("embedded marker breaks without teleport or blast damage",sword.isRemoved()
                    && player.position().equals(ORIGIN) && nearWall.getHealth()==200);
            nearWall.discard();clearWalls(player,walls);

            reset(player,swords);wall(player,walls,1,1);
            var swept=target(player,targets,0,4.5);
            sword=launch(player,swords,null);sword.setDeltaMovement(0,0,5);sword.tick();
            var attached=new CompoundTag();sword.addAdditionalSaveData(attached);
            check("nonterminal wall preserves the full entity sweep and impact attachment",!sword.airTrick()
                    && attached.hasUUID("LegacyAttached") && attached.getUUID("LegacyAttached").equals(swept.getUUID())
                    && swept.getHealth()<200 && swept.getBoundingBox().inflate(.31).contains(sword.position())
                    && player.position().distanceTo(swept.position())<2);
            swept.discard();clearWalls(player,walls);

            reset(player,swords);var locked=target(player,targets,3,6);
            sword=launch(player,swords,locked);sword.tick();
            check("locked marker aims at its original target before teleport",sword.getX()>ORIGIN.x
                    && player.position().equals(ORIGIN) && locked.getHealth()==200);
            for(int i=0;i<30 && sword.airTrick() && !sword.isRemoved();i++)sword.tick();
            check("accepted locked impact attaches and teleports",!sword.airTrick() && locked.getHealth()<200
                    && player.position().distanceTo(locked.position())<2);
            locked.discard();

            reset(player,swords);var protectedTarget=target(player,targets,0,5);
            int[] deniedDamage={0};
            Consumer<LivingAttackEvent> protect=event->{
                if(event.getEntity()==protectedTarget){deniedDamage[0]++;event.setCanceled(true);}
            };
            MinecraftForge.EVENT_BUS.addListener(protect);
            try {
                sword=launch(player,swords,null);
                for(int i=0;i<30 && !sword.isRemoved();i++)sword.tick();
                check("damage protection rejects attachment and teleport after real impact",deniedDamage[0]>0
                        && protectedTarget.getHealth()==200 && player.position().equals(ORIGIN) && sword.isRemoved());
            } finally {MinecraftForge.EVENT_BUS.unregister(protect);}

            reset(player,swords);int[] deniedImpact={0};
            var canceled=launch(player,swords,null);
            Consumer<ProjectileImpactEvent> cancel=event->{
                if(event.getProjectile()==canceled){deniedImpact[0]++;event.setCanceled(true);}
            };
            MinecraftForge.EVENT_BUS.addListener(cancel);
            try {
                for(int i=0;i<30 && !canceled.isRemoved();i++)canceled.tick();
                check("projectile-impact cancellation prevents damage and teleport",deniedImpact[0]>0
                        && protectedTarget.getHealth()==200 && player.position().equals(ORIGIN) && canceled.isRemoved());
            } finally {MinecraftForge.EVENT_BUS.unregister(cancel);}
        } finally {
            clearWalls(player,walls);swords.forEach(Entity::discard);targets.forEach(Entity::discard);
            MinecraftForge.EVENT_BUS.unregister(collect);player.getPersistentData().remove(LegacyMode.MODERN);player.discard();
        }
    }

    private static void reset(ServerPlayer player,List<LegacyPhantomSword> swords) {
        swords.forEach(Entity::discard);swords.clear();
        player.getPersistentData().remove(LegacyMode.MODERN);
        for(String key:List.of(SlayerStyleArts.AVOID_TRICKUP_PATH,SlayerStyleArts.AVOID_COUNTER_PATH,
                SlayerStyleArts.AVOID_VEC_PATH,"sb.airtrick.counter","sb.airtrick.target","SB.AvoidCombo"))player.getPersistentData().remove(key);
        player.setPos(ORIGIN);player.setYRot(0);player.setXRot(0);player.setOnGround(false);player.setDeltaMovement(Vec3.ZERO);
        var blade=new ItemStack(SlashBladeItems.SLASHBLADE.get());
        var state=BladeStateAccess.of(blade).orElseThrow();state.setDefaultBewitched(true);state.setProudSoulCount(10);
        blade.enchant(Enchantments.POWER_ARROWS,1);
        player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        LegacyMode.bindInventory(player);player.getCapability(CapabilityInputState.INPUT_STATE).resolve().orElseThrow().getCommands().clear();
    }

    private static void input(ServerPlayer player,boolean special,boolean left) {
        InputClock.next(player);
        var state=player.getCapability(CapabilityInputState.INPUT_STATE).resolve().orElseThrow();
        var old=state.getCommands().clone();
        var next=EnumSet.of(InputCommand.SNEAK,InputCommand.FORWARD);
        if(special)next.add(InputCommand.SPRINT);
        if(left)next.add(InputCommand.LEFT);
        state.getCommands().clear();state.getCommands().addAll(next);
        InputCommandEvent.onInputChange(player,state,old,next);
    }

    private static boolean noTrickUp(ServerPlayer player) {
        return !player.getPersistentData().contains(SlayerStyleArts.AVOID_TRICKUP_PATH)
                && !player.getPersistentData().contains(SlayerStyleArts.AVOID_VEC_PATH)
                && player.getPersistentData().getInt(SlayerStyleArts.AVOID_COUNTER_PATH)==0
                && player.getPersistentData().getInt("sb.airtrick.counter")==0;
    }

    private static LegacyPhantomSword launch(ServerPlayer player,List<LegacyPhantomSword> swords,Entity target) {
        if(!LegacyAirTrick.launch(player,target))throw new AssertionError("Projectile fixture launch rejected");
        return swords.get(swords.size()-1);
    }

    private static Zombie target(ServerPlayer player,List<Zombie> targets,double x,double z) {
        var target=EntityType.ZOMBIE.create(player.level());
        target.setPos(ORIGIN.add(x,0,z));target.setNoAi(true);target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);target.setHealth(200);
        player.level().addFreshEntity(target);targets.add(target);return target;
    }

    private static void wall(ServerPlayer player,List<BlockPos> walls,int first,int last) {
        for(int x=-1;x<=1;x++)for(int y=0;y<=3;y++)for(int z=first;z<=last;z++) {
            var pos=BlockPos.containing(ORIGIN).offset(x,y,z);walls.add(pos);
            player.level().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
        }
    }

    private static void clearWalls(ServerPlayer player,List<BlockPos> walls) {
        for(var pos:walls)player.level().removeBlock(pos,false);
        walls.clear();
    }
}
