package org.scex.slashbladelegacy;

import java.util.*;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.item.SwordType;
import mods.flammpfeil.slashblade.util.*;
import mods.flammpfeil.slashblade.ability.StunManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import static org.scex.slashbladelegacy.LegacyMove.*;

/** Legacy input graph and immediate melee, using Resharpened state synchronization and damage hooks. */
public final class LegacyCombat {
    public static final DeferredRegister<ComboState> COMBOS=DeferredRegister.create(ComboState.REGISTRY_KEY,LegacyCompat.MOD_ID);
    private static final Map<LegacyMove,DeferredHolder<ComboState,ComboState>> MOVES=new EnumMap<>(LegacyMove.class);
    private static final String AIR_USED="slashblade_legacy_compat.air_used";
    private static final Map<Player,Motion> MOTIONS=new WeakHashMap<>();
    private static final class Motion {
        final LegacyMove move;
        final net.minecraft.world.item.ItemStack blade;
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        final long start;
        final double height;
        Vec3 velocity;
        long processed=-1;
        final Set<UUID> hit=new HashSet<>();
        Motion(Player player,LegacyMove move) {
            this.move=move;blade=player.getMainHandItem();dimension=player.level().dimension();start=player.level().getGameTime();
            height=player.getY();velocity=player.getDeltaMovement();
        }
    }
    static {
        for(var move:LegacyMove.values()) if(move!=NONE) {
            MOVES.put(move,COMBOS.register(move.name().toLowerCase(Locale.ROOT),()->build(move)));
        }
    }
    private LegacyCombat() {}
    public static void resetAirAttack(Player player) {player.getPersistentData().remove(AIR_USED);}
    public static ResourceLocation id(LegacyMove move) {return move==NONE?ComboStateRegistry.NONE.getId():MOVES.get(move).getId();}
    public static LegacyMove move(ResourceLocation id) {
        for(var entry:MOVES.entrySet())if(entry.getValue().getId().equals(id))return entry.getKey();
        return NONE;
    }
    private static ComboState build(LegacyMove move) {
        // Registry entries carry input/timeouts only. The 1.7 renderer uses vanilla swing progress.
        int reset=move==NOUTOU?10:move.resetTicks;
        var builder=ComboState.Builder.newInstance().startAndEnd(0,0).speed(1)
                .timeout(reset*50).priority(100)
                .next(user->id(NONE)).nextOfTimeout(user->id(move==HELM_BRAKER && user.onGround()?HELM_LANDING:move.scabbard || move==NOUTOU || move==SLASH_DIM || move==IAI || move==S_IAI?NONE:NOUTOU))
                .clickAction(user->attack(user,move)).addHitEffect((target,user)->impact(user,target,move))
                .addHoldAction(user->hold(user,move,user.getTicksUsingItem()));
        if(move.aerial())builder.aerial();
        return builder.build();
    }
    /** Ordinary input uses registered legacy states. Charged foreign arts retain their identity;
     * the old dimension-slash gesture is their default visual adapter until explicitly mapped. */
    public static LegacyMove visualMove(ResourceLocation combo) {
        var art=LegacyArts.visual(combo);if(art!=null)return art;
        var legacy=move(combo);
        if(legacy!=NONE || combo.equals(ComboStateRegistry.NONE.getId()) || combo.equals(ComboStateRegistry.STANDBY.getId()))return legacy;
        return switch(combo.toString()) {
            case "slashblade:piercing","slashblade:piercing_just","si_slashblade:spear" -> HIRA_TUKI;
            case "si_slashblade:legacy_kiriorosi","si_slashblade:kinetic_impact" -> KIRIOROSI;
            case "prinegorerouse:zenith12th_end","prinegorerouse:magnetic_storm_sword_end",
                    "prinegorerouse:divine_cross_sa_end","prinegorerouse:burning_fire_sa_end",
                    "prinegorerouse:cosmic_line_end","prinegorerouse:over_the_horizon_end",
                    "si_slashblade:legacy_dimension_recovery","slashblade:drive_horizontal_end",
                    "slashblade:piercing_end","slashblade:piercing_end2","slashblade:judgement_cut_sheath",
                    "slashblade:judgement_cut_sheath_air","slashblade:judgement_cut_slash_just_sheath" -> NOUTOU;
            default -> SLASH_DIM;
        };
    }
    public static float slashRoll(LegacyMove move) {return -move.direction;}
    public static int rank(LivingEntity user) {
        var rank=user.getData(CapabilityConcentrationRank.RANK_POINT).getRank(user.level().getGameTime());
        return rank==null?0:rank.level;
    }
    public static LegacyMove next(LegacyMove current,boolean right,boolean ground,boolean sneak,
                                 boolean forward,boolean back,boolean recentBack,boolean airUsed,boolean dual,int rank,long elapsed) {
        if(!ground) {
            if(forward && sneak && recentBack && current!=CALIBUR && !airUsed)return CALIBUR;
            if(forward && sneak && current!=HELM_BRAKER)return HELM_BRAKER;
            return switch(current) {
                case IAI -> BATTOU;
                case A_SLASH_EDGE -> A_KIRIOROSI;
                case A_KIRIOROSI -> elapsed>7?A_KIRIAGE:BATTOU;
                case A_KIRIAGE -> A_KIRIOROSI_FINISH;
                default -> right?A_SLASH_EDGE:IAI;
            };
        }
        if(!right)return current==KIRIAGE?KIRIOROSI:KIRIAGE;
        if(forward && sneak && current!=RAPID_SLASH && current!=RAPID_SLASH_END)return RAPID_SLASH;
        if(back && sneak && current!=KIRIAGE)return KIRIAGE;
        return switch(current) {
            case RAPID_SLASH -> RAPID_SLASH_END;
            case SAYA1 -> SAYA2;
            case SAYA2 -> rank>=5 && elapsed<=8?S_IAI:BATTOU;
            case KIRIAGE -> sneak?SAYA1:KIRIOROSI;
            case S_IAI -> S_SLASH_EDGE;
            case S_SLASH_EDGE -> S_RETURN_EDGE;
            case S_RETURN_EDGE -> S_SLASH_BLADE;
            default -> SAYA1; // r87 predates offhand blades; saved Force IDs remain registered only.
        };
    }
    public static void nextCombo(SlashBladeEvent.NextComboEvent event) {
        if(!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) || !(event.getUser() instanceof Player player))return;
        var state=event.getSlashBladeState();
        var input=player.getData(CapabilityInputState.INPUT_STATE);
        var commands=input.getCommands(player);
        boolean right=commands.contains(InputCommand.R_CLICK);
        if(!right && !commands.contains(InputCommand.L_CLICK))return;
        if(SwordType.from(event.getBlade()).contains(SwordType.NOSCABBARD)){event.setNextCombo(id(NONE));return;}
        if(player.onGround())player.getPersistentData().remove(AIR_USED);
        long now=player.level().getGameTime();
        Long backTime=input.getLastPressTimes().get(InputCommand.BACK);
        var current=move(state.resolvCurrentComboState(player));
        var selected=next(current,right,player.onGround(),commands.contains(InputCommand.SNEAK),
                commands.contains(InputCommand.FORWARD),commands.contains(InputCommand.BACK),
                backTime!=null && now-backTime>=0 && now-backTime<=7,player.getPersistentData().getBoolean(AIR_USED),
                BladeStateAccess.of(player.getOffhandItem()).isPresent(),rank(player),Math.max(0,now-state.getLastActionTime()));
        event.setNextCombo(id(selected));
    }
    /** Exact ItemSlashBlade.getBBofCombo from 1.7.10 r87, independent of modern reach attributes. */
    public static AABB box(LivingEntity user,LegacyMove move) {
        var blade=user.getMainHandItem();var state=BladeStateAccess.of(blade).orElseThrow();
        Vec3 look=user.getLookAngle().multiply(1,0,1).normalize();
        boolean broken=state.isBroken();
        double x=broken?1:1.2,y=broken?0:1.25,z=broken?1:2,dy=broken?0:.5;
        if(move.scabbard){x=1.2;y=.25;z=2;dy=0;}
        else switch(move) {
            case BATTOU,CALIBUR,RAPID_SLASH,RISING_STAR,SLASH_EDGE,RETURN_EDGE,S_SLASH_EDGE,S_RETURN_EDGE -> {
                if(!broken) {
                    boolean perfect=blade.getDamageValue()==0 && !state.isSealed() && SwordType.from(blade).contains(SwordType.BEWITCHED);
                    x=perfect?5:2;y=.75;z=perfect?0:2.5;dy=0;
                }
            }
            case S_SLASH_BLADE -> {if(!broken){x=3;y=1;z=2.5;dy=0;}}
            case IAI,S_IAI -> {if(!broken){x=2;y=1;z=2.5;dy=0;}}
            case HELM_BRAKER -> {if(!broken){x=2;y=2.5;z=2.5;dy=0;}}
            default -> {}
        }
        return user.getBoundingBox().inflate(x,y,x).move(look.x*z,dy,look.z*z);
    }
    private static void attack(LivingEntity user,LegacyMove move) {
        if(move==HELM_LANDING)return;
        LegacySheathingRepair.begin(user,move);
        if(move==NOUTOU){if(user instanceof Player player)LegacyUpthrust.blast(player,player.getMainHandItem());return;}
        if(move==NONE)return;
        var attackingBlade=user.getMainHandItem();
        movement(user,move);
        if(user.level().isClientSide || !(user instanceof Player player))return;
        LegacyDamage.update(player);
        // Left-click selects a pose; the clicked entity receives the single vanilla-style hit.
        // It must not also run the right-click area strike.
        if(player.getData(CapabilityInputState.INPUT_STATE).getCommands().contains(InputCommand.L_CLICK))return;
        boolean perfectBattou=move==BATTOU && attackingBlade.getDamageValue()==0
                && SwordType.from(attackingBlade).contains(SwordType.BEWITCHED);
        // The original mesh trail follows the blade. Keep the public addon hook without spawning
        // a second, independently timed Resharpened arc over the old animation.
        var slash=new SlashBladeEvent.DoSlashEvent(attackingBlade,BladeStateAccess.of(attackingBlade).orElseThrow(),
                player,slashRoll(move),false,move.scabbard?.44:1,KnockBacks.cancel);
        if(net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(slash).isCanceled())return;
        if(!holdingBlade(player,attackingBlade))return;
        LegacyAdditionalAttack.attack(player,move);
        if(!holdingBlade(player,attackingBlade))return;
        LegacyProjectileGuard.intercept(player,player.getMainHandItem(),box(player,move),true,true);
        if(!holdingBlade(player,attackingBlade))return;
        if(move==RAPID_SLASH || move==CALIBUR || move==HELM_BRAKER || move==STINGER)MOTIONS.put(player,new Motion(player,move));
        damageArea(player,move,box(player,move),null);
        if(perfectBattou && holdingBlade(player,attackingBlade))LegacyAdditionalAttack.wear(attackingBlade,10,player);
        if(!move.scabbard)player.level().playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                net.minecraft.sounds.SoundSource.PLAYERS,1,1);
    }
    private static boolean holdingBlade(Player player,net.minecraft.world.item.ItemStack blade) {
        return player.isAlive() && !blade.isEmpty() && player.getMainHandItem()==blade && BladeStateAccess.of(blade).isPresent();
    }
    private static void damageArea(Player player,LegacyMove move,AABB bounds,Set<UUID> alreadyHit) {
        var blade=player.getMainHandItem();
        if(!holdingBlade(player,blade))return;
        for(var entity:LegacyTargets.within(player,bounds)) {
            if(!holdingBlade(player,blade))break;
            if(alreadyHit!=null && !alreadyHit.add(entity.getUUID()))continue;
            LegacyDamage.hit(player,entity,move,bounds,true);
        }
    }
    public static void tick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        Player player=event.getEntity();
        LegacyDamage.update(player);
        if(player.onGround())player.getPersistentData().remove(AIR_USED);
        if(!player.level().isClientSide){tickMotion(player,player.level().getGameTime());LegacyJustGuard.tick(player);LegacyProjectileGuard.tick(player);LegacySheathingRepair.tick(player);}
    }
    public static void projectileHit(net.minecraft.world.item.ItemStack blade,LivingEntity target,Player player) {
        var state=BladeStateAccess.of(blade).orElseThrow();var current=move(state.resolvCurrentComboState(player));
        if(state.getComboRoot().equals(ComboStateRegistry.STANDBY.getId())
                && (current.scabbard || current==FORCE1 || current==FORCE2 || current==FORCE6 || current==STINGER)) {
            if(!net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new SlashBladeEvent.HitEvent(blade,state,target,player)).isCanceled())
                impact(player,target,current);
        } else blade.hurtEnemy(target,player);
    }
    public static void hold(LivingEntity user,LegacyMove move,int elapsed) {
        if(!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) || !(user instanceof Player player) || !player.onGround())return;
        if((move==KIRIAGE && elapsed==3) || (move==RAPID_SLASH && elapsed==7)) {
            player.jumpFromGround();player.setDeltaMovement(player.getDeltaMovement().add(0,.2,0));player.setOnGround(false);
            player.hurtMarked=true;
            if(move==RAPID_SLASH) {
                BladeStateAccess.of(player.getMainHandItem()).ifPresent(state->state.updateComboSeq(player,id(RISING_STAR)));
                player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
        }
    }
    public static void tickMotion(Player player,long now) {
        Motion motion=MOTIONS.get(player);
        if(motion==null)return;
        if(!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) || !player.isAlive() || motion.blade.isEmpty() || player.getMainHandItem()!=motion.blade
                || !player.level().dimension().equals(motion.dimension)){MOTIONS.remove(player);return;}
        long age=now-motion.start;
        if(age<=0 || age<=motion.processed)return;
        motion.processed=age;
        var state=BladeStateAccess.of(motion.blade).orElse(null);
        if(state==null){MOTIONS.remove(player);return;}
        if((motion.move==HELM_BRAKER || motion.move==CALIBUR)
                && (player.onGround() || player.isInWater() || player.isInLava())){
            MOTIONS.remove(player);
            if(motion.move==HELM_BRAKER && move(state.getComboSeq())==HELM_BRAKER)
                state.updateComboSeq(player,id(player.onGround()?HELM_LANDING:NOUTOU));
            return;
        }
        switch(motion.move) {
            case RAPID_SLASH -> {
                if(age<=6 && age%3==0)damageArea(player,RAPID_SLASH,box(player,RAPID_SLASH),null);
                if(age>=6)MOTIONS.remove(player);
            }
            case HELM_BRAKER -> {
                if(age>=20 || move(state.getComboSeq())!=HELM_BRAKER){MOTIONS.remove(player);return;}
                // A ServerPlayer's position is driven by client movement packets. Send downward
                // velocity, not a server-only move followed by the previous (often upward) velocity.
                player.setDeltaMovement(0,-1.5,0);player.fallDistance=0;
                if(age%3==0)damageArea(player,HELM_BRAKER,box(player,HELM_BRAKER),null);
            }
            case CALIBUR -> {
                if(age>=14){MOTIONS.remove(player);return;}
                Entity locked=state.getTargetEntity(player.level());
                if(locked!=null && player.distanceToSqr(locked)<3)motion.velocity=Vec3.ZERO;
                motion.velocity=motion.velocity.multiply(.6,0,.6);
                player.setDeltaMovement(motion.velocity);
                player.move(MoverType.SELF,new Vec3(motion.velocity.x,motion.height-player.getY(),motion.velocity.z));
                player.fallDistance=0;
                if(age<5 && age%3==0)damageArea(player,CALIBUR,new AABB(player.position(),player.position()).inflate(2.5),null);
                if(age==5)player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            case STINGER -> {
                if(age==1){Vec3 v=Vec3.directionFromRotation(0,player.getYRot()).scale(player.onGround()?1.5:1.5*.35);
                    player.setDeltaMovement(v.x,player.getDeltaMovement().y,v.z);
                    mods.flammpfeil.slashblade.ability.Untouchable.setUntouchable(player,5);}
                Entity locked=state.getTargetEntity(player.level());
                if(locked!=null && player.distanceToSqr(locked)<3)player.setDeltaMovement(Vec3.ZERO);
                if(age<=4)damageArea(player,STINGER,box(player,STINGER),motion.hit);
                if(age>=4){player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);MOTIONS.remove(player);}
            }
            default -> MOTIONS.remove(player);
        }
        player.hurtMarked=true;
        if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
            serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
    }
    private static int feather(LivingEntity user) {
        return user.getMainHandItem().getEnchantmentLevel(user.registryAccess().holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.FEATHER_FALLING));
    }
    private static void movement(LivingEntity user,LegacyMove move) {
        Vec3 velocity=user.getDeltaMovement();
        boolean used=user.getPersistentData().getBoolean(AIR_USED);
        switch(move) {
            case HELM_BRAKER -> {
                user.setDeltaMovement(0,-1.5,0);
                mods.flammpfeil.slashblade.ability.Untouchable.setUntouchable(user,6);
            }
            case RAPID_SLASH,CALIBUR -> {
                var forward=Vec3.directionFromRotation(0,user.getYRot()).scale(2.5);
                user.setDeltaMovement(forward.x,move==CALIBUR?0:velocity.y,forward.z);
                mods.flammpfeil.slashblade.ability.Untouchable.setUntouchable(user,6);
                if(move==CALIBUR)user.getPersistentData().putBoolean(AIR_USED,true);
            }
            case A_SLASH_EDGE,A_KIRIOROSI,IAI -> {if(!user.onGround() && !used && feather(user)==0)user.setDeltaMovement(velocity.x,.3,velocity.z);}
            case A_KIRIAGE -> user.setDeltaMovement(velocity.x,.7,velocity.z);
            case A_KIRIOROSI_FINISH -> user.setDeltaMovement(velocity.x,.1,velocity.z);
            case BATTOU -> {if(!user.onGround() && !used){if(feather(user)==0)user.setDeltaMovement(velocity.x,.2,velocity.z);user.getPersistentData().putBoolean(AIR_USED,true);}}
            default -> {}
        }
        // r87 only clears falling for these air techniques. Kiriage/Kiriorosi can still crit.
        switch(move) {
            case HELM_BRAKER,CALIBUR,A_SLASH_EDGE,A_KIRIOROSI,A_KIRIAGE,A_KIRIOROSI_FINISH -> user.fallDistance=0;
            case IAI,BATTOU -> {if(!user.onGround())user.fallDistance=0;}
            default -> {}
        }
        if(!velocity.equals(user.getDeltaMovement())) {
            user.hurtMarked=true;
            if(user instanceof net.minecraft.server.level.ServerPlayer player)player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(user));
        }
    }
    public static void impact(LivingEntity user,LivingEntity target,LegacyMove move) {
        Vec3 forward=Vec3.directionFromRotation(0,user.getYRot());
        switch(move) {
            case SLASH_DIM -> target.setDeltaMovement(Vec3.ZERO);
            case KIRIAGE,RISING_STAR -> {target.setOnGround(false);target.setDeltaMovement(0,.6,0);stun(target);}
            case KIRIOROSI -> {var v=target.getDeltaMovement();target.setDeltaMovement(v.x+forward.x*.25,Math.min(0,v.y)-.2,v.z+forward.z*.25);target.fallDistance+=4;target.invulnerableTime=0;}
            case BATTOU,RETURN_EDGE,HIRA_TUKI -> {
                if(move==HIRA_TUKI)stun(target);
                int knockback=user.getMainHandItem().getEnchantmentLevel(user.registryAccess().holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.KNOCKBACK));
                double force=knockback>0?knockback*.5:.75;
                target.setDeltaMovement(forward.x*force,.2,forward.z*force);
                if(user instanceof Player player)LegacyUpthrust.attach(player,target);
            }
            case A_KIRIAGE -> {target.setDeltaMovement(0,.7,0);stun(target);}
            case A_KIRIOROSI_FINISH,HELM_BRAKER -> {target.setDeltaMovement(0,move==HELM_BRAKER?-1:-.8,0);target.fallDistance+=move==HELM_BRAKER?5:4;target.invulnerableTime=0;StunManager.removeStun(target);}
            default -> {target.setDeltaMovement(0,move.scabbard?0:(feather(user)>0?.3:.2),0);stun(target);}
        }
        if(move.scabbard && target instanceof Mob mob) {
            int fortune=user.getMainHandItem().getEnchantmentLevel(user.registryAccess().holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE));
            if(fortune>0)mob.setDropChance(EquipmentSlot.MAINHAND,.99f);
            if(fortune>1)for(var slot:new EquipmentSlot[]{EquipmentSlot.FEET,EquipmentSlot.LEGS,EquipmentSlot.CHEST,EquipmentSlot.HEAD})mob.setDropChance(slot,.99f);
        }
        target.hurtMarked=true;
    }
    private static void stun(LivingEntity target) {
        StunManager.setStun(target,20);
        if(!target.level().isClientSide)target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,10,30,true,false));
    }
}
