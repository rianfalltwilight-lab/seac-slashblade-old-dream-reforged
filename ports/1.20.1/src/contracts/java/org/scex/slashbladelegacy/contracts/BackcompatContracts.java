package org.scex.slashbladelegacy.contracts;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.scex.slashbladelegacy.*;

/** Exercise shared APIs with actual items, damage, projectiles, and player events. */
final class BackcompatContracts {
    private BackcompatContracts() {}

    static void run(MinecraftServer server,Map<String,Object> report) throws Exception {
        var rows=new LinkedHashMap<String,Object>();report.put("backcompat",rows);
        rows.put("slashblade_version",ModList.get().getModContainerById("slashblade").orElseThrow().getModInfo().getVersion().toString());
        var player=player(server,"Backcompat");var level=server.overworld();
        level.addNewPlayer(player);
        var blade=blade();player.setItemInHand(InteractionHand.MAIN_HAND,blade);LegacyMode.bind(blade,player);
        var state=BladeStateAccess.of(blade).orElseThrow();
        var target=EntityType.ZOMBIE.create(level);check(target!=null,"target exists");
        target.setPos(24,160,2);target.setNoAi(true);target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.setHealth(500);
        check(level.addFreshEntity(target),"target joins the world");
        try {
            blade.removeTagKey("Enchantments");blade.setDamageValue(1);
            var combo=new ArrayList<Map<String,Object>>();
            for(var move:new LegacyMove[]{LegacyMove.SAYA1,LegacyMove.SAYA2,LegacyMove.BATTOU}){
                float before=target.getHealth();int wear=blade.getDamageValue();
                InputClock.use(player,blade);
                check(LegacyCombat.move(state.getComboSeq())==move,"native item use selects "+move);
                check(target.getHealth()<before,"combo damages target: "+move);
                check(blade.getDamageValue()-wear==(move.scabbard?0:1),"combo wear: "+move);
                combo.add(Map.of("move",move.name(),"damage",before-target.getHealth(),"passed",true));
            }
            rows.put("native_combo",combo);
            float managedBefore=target.getHealth();
            mods.flammpfeil.slashblade.util.AttackManager.class.getMethod("doMeleeAttack",net.minecraft.world.entity.LivingEntity.class,
                    Entity.class,boolean.class,boolean.class).invoke(null,player,target,true,true);
            check(Math.abs(managedBefore-target.getHealth()-3)<.001,"shared native melee overload hits exactly once");
            rows.put("native_managed_melee",Map.of("damage",managedBefore-target.getHealth(),"passed",true));
            player.stopUsingItem();target.setPos(24,160,16);
            blade.enchant(Enchantments.POWER_ARROWS,1);state.setProudSoulCount(1000);
            state.setSlashArtsKey(LegacyArts.key(LegacyArts.Art.DRIVE));state.setTargetEntityId(target);
            state.setComboSeq(LegacyCombat.id(LegacyMove.SAYA1));state.setLastActionTime(level.getGameTime());
            blade.releaseUsing(level,player,blade.getUseDuration()-20);
            var drives=level.getEntitiesOfClass(LegacyDrive.class,player.getBoundingBox().inflate(40),e->e.getOwner()==player);
            check(drives.size()==1 && state.getProudSoulCount()==990,"native release creates Drive and charges ten souls");
            rows.put("native_sa_release",Map.of("drives",drives.size(),"souls_after",state.getProudSoulCount(),"passed",true));
            drives.forEach(Entity::discard);
            state.setTargetEntityId((Entity)null);state.setProudSoulCount(100);
            LegacyRangeAttack.perform(player,blade,LegacyRangeAttack.Art.SINGLE);
            var swords=level.getEntitiesOfClass(LegacyPhantomSword.class,player.getBoundingBox().inflate(40),e->e.getOwner()==player);
            check(swords.size()==1 && state.getProudSoulCount()==99,"single sword spawn and cost");
            var sword=swords.get(0);Vec3 before=sword.position();
            check(sword.interval()==7,"single sword uses the seven-tick launch delay");
            for(int i=0;i<7;i++){
                sword.tick();
                check(!sword.isRemoved() && sword.position().equals(before),"single sword waits before launch: "+i);
            }
            sword.tick();
            check(!sword.isRemoved() && sword.position().distanceToSqr(before)>0,"single sword moves with a live owner");
            rows.put("phantom_sword",Map.of("spawned",1,"souls_after",99,"interval",sword.interval(),"launch_age",sword.age(),
                    "standby_ticks",7,"moved",true,"passed",true));
            sword.discard();
            durability(player,blade,rows);
            var saved=blade.save(new net.minecraft.nbt.CompoundTag());var loaded=ItemStack.of(saved);
            check(saved.equals(loaded.save(new net.minecraft.nbt.CompoundTag())) && BladeStateAccess.of(loaded).isPresent(),"blade NBT and capability survive serialization");
            rows.put("blade_nbt_roundtrip",true);
            state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(level.getGameTime());
            LegacyCapabilities.input(player).getCommands().clear();player.stopUsingItem();player.swinging=false;
            LegacyMode.request(player);
            check(LegacyMode.modern(player) && !LegacyMode.pending(player) && LegacyCapabilities.rank(player).getMaxCapacity()==1799,"server mode request settles");
            LegacyMode.request(player);
            check(LegacyMode.legacy(player) && !LegacyMode.pending(player),"server mode request returns to legacy");
            rows.put("server_mode_request",true);
            death(server,rows);
            rows.put("passed",true);
        } finally {
            level.getEntitiesOfClass(LegacyPhantomSword.class,player.getBoundingBox().inflate(60),e->e.getOwner()==player).forEach(Entity::discard);
            level.getEntitiesOfClass(LegacyDrive.class,player.getBoundingBox().inflate(60),e->e.getOwner()==player).forEach(Entity::discard);
            target.discard();player.discard();
        }
    }

    private static void durability(ServerPlayer player,ItemStack blade,Map<String,Object> rows){
        var state=BladeStateAccess.of(blade).orElseThrow();var checks=new LinkedHashMap<String,Object>();rows.put("durability",checks);
        blade.removeTagKey("Enchantments");blade.setDamageValue(20);state.setBroken(false);state.setProudSoulCount(0);
        int[] callbacks={0};blade.hurtAndBreak(2,player,p->callbacks[0]++);
        check(blade.getDamageValue()==22 && callbacks[0]==0,"ordinary wear uses native hurt");checks.put("ordinary_wear",true);
        blade.getOrCreateTag().putBoolean("Unbreakable",true);blade.hurtAndBreak(200,player,p->callbacks[0]++);
        check(blade.getDamageValue()==22 && !state.isBroken() && callbacks[0]==0,"NBT protects ordinary wear");checks.put("nbt_protection",true);
        blade.removeTagKey("Unbreakable");blade.setDamageValue(100);blade.enchant(Enchantments.UNBREAKING,1);
        long seed=0;while(RandomSource.create(seed).nextInt(2)==0)seed++;
        var expected=RandomSource.create(seed);expected.nextInt(2);player.getRandom().setSeed(seed);
        blade.hurtAndBreak(1,player,p->callbacks[0]++);
        check(blade.getDamageValue()==100 && !state.isBroken() && callbacks[0]==0,"Unbreaking protects the break boundary");
        check(player.getRandom().nextInt()==expected.nextInt(),"Unbreaking rolls once");checks.put("unbreaking_once",true);
        blade.removeTagKey("Enchantments");blade.hurtAndBreak(1,player,p->callbacks[0]++);
        check(state.isBroken() && blade.getCount()==1 && callbacks[0]==1,"legacy break retains one blade");checks.put("legacy_break",true);
        boolean combat=LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT);
        try {
            LegacyCompat.LEGACY_COMBAT.set(false);
            var attack=blade.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).stream()
                    .filter(value->value.getId().equals(UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF"))).findFirst().orElseThrow();
            check(attack.getAmount()==2,"independent broken-damage option supports both native formulas");
            checks.put("independent_broken_damage",true);
        } finally {LegacyCompat.LEGACY_COMBAT.set(combat);}
        blade.setDamageValue(0);check(!state.isBroken() && blade.getDamageValue()==0,"explicit repair clears break");checks.put("repair",true);
    }

    private static void death(MinecraftServer server,Map<String,Object> rows) throws Exception {
        var original=player(server,"BackcompatDead");var replacement=player(server,"BackcompatClone");
        try {
            original.setItemInHand(InteractionHand.MAIN_HAND,blade());original.setHealth(0);original.invalidateCaps();
            check(!original.getCapability(CapabilityConcentrationRank.RANK_POINT).isPresent(),"dead rank is invalid");
            for(int i=0;i<25;i++)ticks(original);
            check(!original.getCapability(CapabilityConcentrationRank.RANK_POINT).isPresent(),"dead ticks do not revive rank");
            original.getPersistentData().putBoolean(LegacyMode.MODERN,true);replacement.setItemInHand(InteractionHand.MAIN_HAND,blade());
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement,original,true));
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(replacement,false));ticks(replacement);
            check(LegacyMode.modern(replacement) && replacement.getCapability(CapabilityInputState.INPUT_STATE).isPresent()
                    && LegacyCapabilities.rank(replacement).getMaxCapacity()==1799,"clone and respawn restore mode and live capabilities");
            rows.put("death_clone_respawn",Map.of("dead_start_ticks",25,"dead_end_ticks",25,"dead_inventory_ticks",25,"rank_capacity",1799,"passed",true));
        } finally {original.discard();replacement.discard();}
    }

    private static ItemStack blade() throws Exception {
        var item=ForgeRegistries.ITEMS.getValue(new ResourceLocation("slashblade","slashblade"));check(item!=null,"registered blade item exists");
        var blade=new ItemStack(item);var state=BladeStateAccess.of(blade).orElseThrow();
        try{state.getClass().getMethod("setNonEmpty").invoke(state);}catch(NoSuchMethodException oldVersion){}
        state.setMaxDamage(100);state.setBaseAttackModifier(3);state.setProudSoulCount(1000);state.setBroken(false);state.setSealed(false);
        state.setComboSeq(ComboStateRegistry.NONE.getId());blade.setHoverName(Component.literal("Backcompat blade"));return blade;
    }
    private static ServerPlayer player(MinecraftServer server,String name){
        var player=FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),name));player.connection=new ContractItems.PacketSink(server,player);
        player.setPos(24,160,0);player.setYRot(0);player.setXRot(0);player.setOnGround(true);player.setHealth(player.getMaxHealth());player.getAbilities().instabuild=false;
        return player;
    }
    private static void ticks(ServerPlayer player){
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START,player));
        player.getMainHandItem().inventoryTick(player.level(),player,0,true);
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));
    }
    private static void check(boolean passed,String message){if(!passed)throw new AssertionError("backcompat: "+message);}
}
