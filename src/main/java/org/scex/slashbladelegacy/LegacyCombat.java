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
    public static ResourceLocation id(LegacyMove move) {return move==NONE?ComboStateRegistry.NONE.getId():MOVES.get(move).getId();}
    public static LegacyMove move(ResourceLocation id) {
        for(var entry:MOVES.entrySet())if(entry.getValue().getId().equals(id))return entry.getKey();
        return NONE;
    }
    private static ComboState build(LegacyMove move) {
        // Existing body clips remain a fallback; the client blade/sheath pose uses original 1.12.2 matrices.
        int start=animationStart(move);
        int reset=move==NOUTOU?10:move.resetTicks; // Legacy sheathing schedules LastActionTime five ticks ahead.
        var builder=ComboState.Builder.newInstance().startAndEnd(start,start+9).speed(1)
                .timeout(reset*50-300).priority(50)
                .next(user->id(NONE)).nextOfTimeout(user->id(move.scabbard || move==NOUTOU || move==IAI || move==S_IAI?NONE:NOUTOU))
                .clickAction(user->attack(user,move)).addHitEffect((target,user)->impact(user,target,move))
                .addHoldAction(user->hold(user,move,user.getTicksUsingItem()));
        if(move.aerial())builder.aerial();
        return builder.build();
    }
    /** Existing Resharpened player_motion.vmd clips, shared by body and blade-holder fallback. */
    public static int animationStart(LegacyMove move) {
        return switch(move) {
            case NOUTOU -> 21;
            case SAYA2,FORCE2,FORCE4 -> 100;
            case FORCE3 -> 200;
            case BATTOU,SLASH_EDGE,RETURN_EDGE,S_SLASH_EDGE,S_RETURN_EDGE,FORCE5 -> 200;
            case HIRA_TUKI,STINGER -> 700;
            case S_SLASH_BLADE,FORCE6 -> 900;
            case A_SLASH_EDGE -> 1100;
            case A_KIRIOROSI -> 1200;
            case A_KIRIAGE -> 1300;
            case A_KIRIOROSI_FINISH -> 1500;
            case KIRIAGE -> 1600;
            case KIRIOROSI,HELM_BRAKER -> 1800;
            case IAI,S_IAI -> 1900;
            case RAPID_SLASH,RAPID_SLASH_END,CALIBUR -> 2000;
            case RISING_STAR -> 2100;
            default -> 1;
        };
    }
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
        if(forward && sneak && current!=RAPID_SLASH && current!=RAPID_SLASH_END)return dual?STINGER:RAPID_SLASH;
        if(back && sneak && current!=KIRIAGE)return KIRIAGE;
        return switch(current) {
            case RAPID_SLASH -> RAPID_SLASH_END;
            case SAYA1 -> SAYA2;
            case SAYA2 -> rank>=5 && elapsed<=8?S_IAI:BATTOU;
            case KIRIAGE -> sneak?SAYA1:KIRIOROSI;
            case S_IAI -> S_SLASH_EDGE;
            case S_SLASH_EDGE -> S_RETURN_EDGE;
            case S_RETURN_EDGE -> S_SLASH_BLADE;
            default -> dual?switch(current) {case FORCE1->FORCE2;case FORCE2->FORCE3;case FORCE3->FORCE4;case FORCE4->FORCE5;case FORCE5->FORCE6;default->FORCE1;}:SAYA1;
        };
    }
    public static void nextCombo(SlashBladeEvent.NextComboEvent event) {
        if(!LegacyCompat.LEGACY_COMBAT.get() || !(event.getUser() instanceof Player player))return;
        var state=event.getSlashBladeState();
        // Custom addon combo roots retain their own move sets, until explicitly mapped and verified.
        if(!state.getComboRoot().equals(ComboStateRegistry.STANDBY.getId()))return;
        var input=player.getData(CapabilityInputState.INPUT_STATE);
        var commands=input.getCommands(player);
        boolean right=commands.contains(InputCommand.R_CLICK);
        if(!right && !commands.contains(InputCommand.L_CLICK))return;
        if(SwordType.from(event.getBlade()).contains(SwordType.NOSCABBARD))return;
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
    public static AABB box(LivingEntity user,LegacyMove move) {
        Vec3 look=user.getLookAngle().multiply(1,0,1).normalize();
        double x=1.2,y=1.25,z=2,dy=.5;
        if(move.scabbard){y=.25;dy=0;}
        else switch(move) {
            case BATTOU,CALIBUR,RAPID_SLASH,RISING_STAR,SLASH_EDGE,RETURN_EDGE,S_SLASH_EDGE,S_RETURN_EDGE,STINGER -> {x=2;y=.75;z=2.5;dy=0;}
            case S_SLASH_BLADE -> {x=3;y=1;z=2.5;dy=0;}
            case IAI,S_IAI -> {x=2;y=1;z=2.5;dy=0;}
            case HELM_BRAKER -> {x=2;y=2.5;z=2.5;dy=0;}
            default -> {}
        }
        // Broken blades retain the restored normal footprint; existing server reach/LOS guard also applies.
        return user.getBoundingBox().inflate(x,y,x).move(look.x*z,dy,look.z*z);
    }
    private static void attack(LivingEntity user,LegacyMove move) {
        LegacySheathingRepair.begin(user,move);
        if(move==NOUTOU){if(user instanceof Player player)LegacyUpthrust.blast(player,player.getMainHandItem());return;}
        if(move==NONE)return;
        var attackingBlade=user.getMainHandItem();
        movement(user,move);
        if(user.level().isClientSide || !(user instanceof Player player))return;
        // Restore the public slash/SE pipeline removed by the immediate-melee implementation.
        // Only this returned arc is visual-only; effects spawned by SE listeners retain their damage.
        var arc=AttackManager.doSlash(player,90-move.direction,true,false,move.scabbard?.44:1);
        if(arc==null)return; // Respect cancellation by SE/protection listeners.
        arc.getPersistentData().putBoolean("slashblade_legacy_compat.visual_arc",true);
        if(!holdingBlade(player,attackingBlade))return;
        LegacyAdditionalAttack.attack(player,move);
        if(!holdingBlade(player,attackingBlade))return;
        LegacyProjectileGuard.intercept(player,player.getMainHandItem(),box(player,move),true,true);
        if(!holdingBlade(player,attackingBlade))return;
        if(move==RAPID_SLASH || move==CALIBUR || move==HELM_BRAKER || move==STINGER)MOTIONS.put(player,new Motion(player,move));
        damageArea(player,move,box(player,move),null);
        if(!move.scabbard)player.level().playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                net.minecraft.sounds.SoundSource.PLAYERS,1,1);
    }
    private static boolean holdingBlade(Player player,net.minecraft.world.item.ItemStack blade) {
        return player.isAlive() && !blade.isEmpty() && player.getMainHandItem()==blade && BladeStateAccess.of(blade).isPresent();
    }
    private static void damageArea(Player player,LegacyMove move,AABB bounds,Set<UUID> alreadyHit) {
        var blade=player.getMainHandItem();
        if(!holdingBlade(player,blade))return;
        var state=BladeStateAccess.of(blade).orElseThrow();
        for(var entity:TargetSelector.getTargettableEntitiesWithinAABB(player.level(),player,bounds)) {
            if(!holdingBlade(player,blade))break;
            if(entity instanceof net.minecraft.world.entity.projectile.Projectile || entity instanceof net.minecraft.world.entity.item.PrimedTnt)continue;
            if(alreadyHit!=null && !alreadyHit.add(entity.getUUID()))continue;
            if(!player.hasLineOfSight(entity))continue;
            if(move.scabbard && entity instanceof LivingEntity living) {
                boolean previousClick=state.onClick();
                boolean canceled;
                try {
                    state.setOnClick(true);
                    canceled=net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                            new net.neoforged.neoforge.event.entity.player.AttackEntityEvent(player,entity)).isCanceled();
                } finally {state.setOnClick(previousClick);}
                if(canceled)continue;
                if(!holdingBlade(player,blade))break;
                float amount=rank(player)<3 || state.isBroken()?2:5;
                if(rank(player)>=3 && !state.isBroken() && SwordType.from(blade).contains(SwordType.FIERCEREDGE))amount+=state.getAttackAmplifier()*.5f;
                amount=Math.max(amount,net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDamage(
                        (net.minecraft.server.level.ServerLevel)player.level(),blade,living,player.damageSources().mobAttack(player),amount));
                amount=Math.min(amount,living.getHealth()-1);
                if(amount<=0)continue;
                living.invulnerableTime=0;
                if(living.hurt(player.damageSources().mobAttack(player),amount)
                        && !net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new SlashBladeEvent.HitEvent(blade,state,living,player)).isCanceled())
                    impact(player,living,move); // Legacy scabbard strikes do not consume blade durability.
            } else {
                // 1.12.2 calls Player.attackTargetEntityWithCurrentItem, including its cooldown.
                if(entity instanceof LivingEntity living)living.invulnerableTime=0;
                try {
                    state.setOnClick(true);
                    if((move==FORCE1 || move==FORCE2 || move==FORCE6 || move==STINGER) && BladeStateAccess.of(player.getOffhandItem()).isPresent()) {
                        var offhand=player.getOffhandItem();var offstate=BladeStateAccess.of(offhand).orElseThrow();
                        try {
                            offstate.setOnClick(true);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,offhand);
                            player.attack(entity);
                            LegacyAdditionalAttack.wear(offhand,1,player);
                        }finally{player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,blade);offstate.setOnClick(false);}
                        if(entity instanceof LivingEntity living && !net.neoforged.neoforge.common.NeoForge.EVENT_BUS
                                .post(new SlashBladeEvent.HitEvent(blade,state,living,player)).isCanceled())impact(player,living,move);
                    }else player.attack(entity);
                } finally {state.setOnClick(false);}
            }
        }
    }
    public static void tick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        Player player=event.getEntity();
        if(player.onGround())player.getPersistentData().remove(AIR_USED);
        if(!player.level().isClientSide){tickMotion(player,player.level().getGameTime());LegacyProjectileGuard.tick(player);LegacySheathingRepair.tick(player);}
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
        if(!LegacyCompat.LEGACY_COMBAT.get() || !(user instanceof Player player) || !player.onGround())return;
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
        if(!LegacyCompat.LEGACY_COMBAT.get() || !player.isAlive() || motion.blade.isEmpty() || player.getMainHandItem()!=motion.blade
                || !player.level().dimension().equals(motion.dimension)){MOTIONS.remove(player);return;}
        long age=now-motion.start;
        if(age<=0 || age<=motion.processed)return;
        motion.processed=age;
        var state=BladeStateAccess.of(motion.blade).orElse(null);
        if(state==null){MOTIONS.remove(player);return;}
        if((motion.move==HELM_BRAKER || motion.move==CALIBUR)
                && (player.onGround() || player.isInWater() || player.isInLava())){MOTIONS.remove(player);return;}
        switch(motion.move) {
            case RAPID_SLASH -> {
                if(age<=6 && age%3==0)damageArea(player,RAPID_SLASH,box(player,RAPID_SLASH),null);
                if(age>=6)MOTIONS.remove(player);
            }
            case HELM_BRAKER -> {
                if(age>=20 || move(state.getComboSeq())!=HELM_BRAKER){MOTIONS.remove(player);return;}
                // A ServerPlayer's position is driven by client movement packets. Send downward
                // velocity, not a server-only move followed by the previous (often upward) velocity.
                if(age>1){player.setDeltaMovement(0,-1.5,0);player.fallDistance=0;}
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
            case HELM_BRAKER -> mods.flammpfeil.slashblade.ability.Untouchable.setUntouchable(user,6);
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
        if(move.aerial() || !user.onGround())user.fallDistance=0;
        if(!velocity.equals(user.getDeltaMovement())) {
            user.hurtMarked=true;
            if(user instanceof net.minecraft.server.level.ServerPlayer player)player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(user));
        }
    }
    private static void impact(LivingEntity user,LivingEntity target,LegacyMove move) {
        Vec3 forward=Vec3.directionFromRotation(0,user.getYRot());
        switch(move) {
            case KIRIAGE,RISING_STAR -> {target.setOnGround(false);target.setDeltaMovement(0,.6,0);stun(target);}
            case KIRIOROSI -> {target.setDeltaMovement(target.getDeltaMovement().add(forward.x*.25,-.2,forward.z*.25));target.fallDistance+=4;target.invulnerableTime=0;}
            case BATTOU,RETURN_EDGE,HIRA_TUKI -> {
                int knockback=user.getMainHandItem().getEnchantmentLevel(user.registryAccess().holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.KNOCKBACK));
                double force=knockback>0?knockback*.5:.75;
                target.setDeltaMovement(forward.x*force,.2,forward.z*force);
                if(user instanceof Player player)LegacyUpthrust.attach(player,target);
            }
            case A_KIRIAGE -> {target.setDeltaMovement(0,.7,0);stun(target);}
            case A_KIRIOROSI_FINISH,HELM_BRAKER -> {target.setDeltaMovement(0,move==HELM_BRAKER?-1:-.8,0);target.fallDistance+=move==HELM_BRAKER?5:4;target.invulnerableTime=0;StunManager.removeStun(target);}
            default -> {target.setDeltaMovement(0,move.scabbard?0:(target.onGround()?0:(feather(user)>0?.3:.2)),0);stun(target);}
        }
        target.hurtMarked=true;
    }
    private static void stun(LivingEntity target) {
        StunManager.setStun(target,20);
        if(!target.level().isClientSide)target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,10,30,true,false));
    }
}
