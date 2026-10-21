package org.scex.slashbladelegacy.contracts;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import mods.flammpfeil.slashblade.ability.SlayerStyleArts;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.scex.slashbladelegacy.*;

final class FeedbackInputContracts {
    private static final List<Map<String,Object>> checks=new ArrayList<>();
    private static void check(String name,boolean passed){checks.add(Map.of("name",name,"passed",passed));if(!passed)throw new AssertionError(name);}
    static void run(MinecraftServer server,Map<String,Object> report) {
        checks.clear();report.put("feedback_input",checks);
        var level=server.overworld();
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"FeedbackInput"));
        // FakePlayer's listener deliberately makes teleport() a no-op. Exercise the actual
        // server teleport implementation with a transport sink, so collision remains real.
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(),false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener) {}
        };
        player.setPos(-12,160,-12);player.setYRot(0);player.setXRot(0);level.addNewPlayer(player);
        var blade=new ItemStack(SlashBladeItems.SLASHBLADE.get());
        var state=BladeStateAccess.of(blade).orElseThrow();state.setDefaultBewitched(true);state.setProudSoulCount(10);
        blade.enchant(player.registryAccess().holderOrThrow(Enchantments.POWER),1);
        player.setItemInHand(InteractionHand.MAIN_HAND,blade);
        var target=EntityType.ZOMBIE.create(level);target.setPos(-12,160,-3.5);target.setNoAi(true);target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);target.setHealth(200);level.addFreshEntity(target);
        state.setTargetEntityId(target.getId());
        var spawned=new ArrayList<LegacyPhantomSword>();
        Consumer<EntityJoinLevelEvent> collect=e->{if(e.getEntity() instanceof LegacyPhantomSword sword && sword.getOwner()==player)spawned.add(sword);};
        NeoForge.EVENT_BUS.addListener(collect);
        var wall=new ArrayList<BlockPos>();
        try {
            for(String missing:List.of("power","souls","bewitched","unbroken")) {
                var originalEnchant=blade.get(DataComponents.ENCHANTMENTS);
                if(missing.equals("power"))blade.remove(DataComponents.ENCHANTMENTS);
                if(missing.equals("souls"))state.setProudSoulCount(0);
                if(missing.equals("bewitched"))state.setDefaultBewitched(false);
                if(missing.equals("unbroken"))state.setBroken(true);
                int souls=state.getProudSoulCount();var position=player.position();
                SlayerStyleArts.getInstance().executeAirTrick(player,level,target,state);
                check("AirTrick requires "+missing,spawned.isEmpty() && player.position().equals(position) && state.getProudSoulCount()==souls && player.getPersistentData().getInt("sb.airtrick.counter")==0);
                blade.set(DataComponents.ENCHANTMENTS,originalEnchant);state.setProudSoulCount(10);state.setDefaultBewitched(true);state.setBroken(false);
            }
            var start=player.position();
            player.setLastHurtMob(target); // Previously this branch skipped the projectile entirely.
            SlayerStyleArts.getInstance().executeAirTrick(player,level,target,state);
            check("AirTrick launches from player and charges one soul",spawned.size()==1 && spawned.getFirst().position().distanceTo(player.getEyePosition())<.01 && state.getProudSoulCount()==9);
            var sword=spawned.getFirst();sword.doForceHitEntity(target);
            check("external forced hit cannot trigger AirTrick",player.position().equals(start) && target.getHealth()==200 && sword.airTrick());
            sword.tick();
            check("AirTrick travels before teleport",player.position().equals(start) && sword.position().distanceTo(player.getEyePosition())>1 && player.getPersistentData().getInt("sb.airtrick.counter")==0);
            for(int i=0;i<20 && sword.airTrick() && !sword.isRemoved();i++)sword.tick();
            check("accepted physical impact teleports",player.position().distanceTo(target.position())<2 && target.getHealth()<200);
            sword.discard();spawned.clear();player.setPos(start);target.invulnerableTime=0;
            for(int x=-13;x<=-11;x++)for(int y=160;y<=163;y++)for(int z=-8;z<=-6;z++){var pos=new BlockPos(x,y,z);wall.add(pos);level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());}
            SlayerStyleArts.getInstance().executeAirTrick(player,level,target,state);sword=spawned.getFirst();
            sword.tick();sword.tick();
            check("marker enters thick wall before embedded check",!sword.isRemoved() && player.position().equals(start)
                    && level.getBlockCollisions(sword,sword.getBoundingBox()).iterator().hasNext());
            for(int i=0;i<30 && !sword.isRemoved();i++)sword.tick();
            check("marker embedded in thick wall breaks without teleport",player.position().equals(start) && sword.isRemoved());
            spawned.clear();for(var pos:wall)level.removeBlock(pos,false);wall.clear();
            Consumer<LivingIncomingDamageEvent> cancel=e->{if(e.getEntity()==target)e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(cancel);
            try {
                SlayerStyleArts.getInstance().executeAirTrick(player,level,target,state);sword=spawned.getFirst();
                for(int i=0;i<30 && !sword.isRemoved();i++)sword.tick();
                check("damage protection prevents teleport",player.position().equals(start));
            }finally{NeoForge.EVENT_BUS.unregister(cancel);spawned.forEach(Entity::discard);spawned.clear();}
            target.setPos(-12,160,-9.5);target.invulnerableTime=0;
            for(int x=-13;x<=-11;x++)for(int y=160;y<=162;y++){var pos=new BlockPos(x,y,-11);wall.add(pos);level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());}
            player.getPersistentData().putBoolean(LegacyMode.MODERN,true);LegacyMode.bindInventory(player);
            check("wall fixture blocks line of sight",!player.hasLineOfSight(target));
            float health=target.getHealth();AttackManager.doMeleeAttack(player,target,true,true,1);
            check("modern native melee retains through-wall hit",target.getHealth()<health);
            NeoForge.EVENT_BUS.addListener(cancel);health=target.getHealth();
            try{AttackManager.doMeleeAttack(player,target,true,true,1);check("modern protection cancellation remains effective",target.getHealth()==health);}finally{NeoForge.EVENT_BUS.unregister(cancel);}
            check("attacks do not duplicate inventory blade",player.getInventory().items.stream().filter(s->!s.isEmpty()).count()==1 && blade.getCount()==1);
        } finally {
            for(var pos:wall)level.removeBlock(pos,false);
            NeoForge.EVENT_BUS.unregister(collect);spawned.forEach(Entity::discard);target.discard();player.getPersistentData().remove(LegacyMode.MODERN);player.discard();
        }
    }
}
