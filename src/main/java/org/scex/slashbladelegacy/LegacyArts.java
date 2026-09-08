package org.scex.slashbladelegacy;

import java.util.*;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.item.SwordType;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraft.sounds.*;
import net.neoforged.neoforge.registries.DeferredRegister;

/** r87 numeric arts, with stable aliases for installed core SA identities and public perform events. */
public final class LegacyArts {
    public enum Art {
        DIMENSION("slash_dimension",LegacyMove.SLASH_DIM,20,10), DRIVE("drive",LegacyMove.KIRIAGE,10,5),
        WAVE("wave_edge",LegacyMove.KIRIAGE,20,10), QUICK("quick_drive",LegacyMove.IAI,10,5),
        SPEAR("spear",LegacyMove.HIRA_TUKI,20,10), CIRCLE("circle_slash",LegacyMove.BATTOU,20,10),
        WITHER("wither_swords",LegacyMove.KIRIOROSI,40,10), SAKURA("sakura_end",LegacyMove.SLASH_EDGE,20,10),
        MAXIMUM("maximum_bet",LegacyMove.SLASH_EDGE,20,10);
        public final String key;public final LegacyMove pose;public final int souls,wear;
        Art(String key,LegacyMove pose,int souls,int wear){this.key=key;this.pose=pose;this.souls=souls;this.wear=wear;}
    }
    private record Plan(Art art,SlashArts.ArtsType type,LegacyMove pose,boolean recovery){}
    public static final DeferredRegister<SlashArts> ARTS=DeferredRegister.create(SlashArts.REGISTRY_KEY,LegacyCompat.MOD_ID);
    private static final Map<ResourceLocation,Plan> PLANS=new HashMap<>();
    static {
        for(var art:Art.values()) {
            register(art,SlashArts.ArtsType.Success,art.pose,false);
            if(art==Art.DIMENSION){register(art,SlashArts.ArtsType.Jackpot,art.pose,false);register(art,SlashArts.ArtsType.Super,art.pose,false);register(art,SlashArts.ArtsType.Super,LegacyMove.BATTOU,true);}
            if(art==Art.SAKURA || art==Art.MAXIMUM)register(art,SlashArts.ArtsType.Success,LegacyMove.RETURN_EDGE,true);
            ARTS.register(art.key,()->new SlashArts(user->combo(art,SlashArts.ArtsType.Success,false))
                    .setComboStateJust(user->combo(art,SlashArts.ArtsType.Jackpot,false))
                    .setComboStateSuper(user->combo(Art.DIMENSION,SlashArts.ArtsType.Super,false)).setProudSoulCost(art.souls));
        }
    }
    private LegacyArts(){}
    public static void initialize(){}
    public static ResourceLocation key(Art art){return ResourceLocation.fromNamespaceAndPath(LegacyCompat.MOD_ID,art.key);}
    private static ResourceLocation combo(Art art,SlashArts.ArtsType type,boolean recovery) {
        if(art!=Art.DIMENSION)type=SlashArts.ArtsType.Success;
        return ResourceLocation.fromNamespaceAndPath(LegacyCompat.MOD_ID,"sa_"+art.key+"_"+type.name().toLowerCase(Locale.ROOT)+(recovery?"_end":""));
    }
    private static void register(Art art,SlashArts.ArtsType type,LegacyMove pose,boolean recovery) {
        var id=combo(art,type,recovery);var plan=new Plan(art,type,pose,recovery);PLANS.put(id,plan);
        LegacyCombat.COMBOS.register(id.getPath(),()->ComboState.Builder.newInstance().startAndEnd(0,0).speed(1)
                .priority(type==SlashArts.ArtsType.Super?30:50).timeout(type==SlashArts.ArtsType.Super && !recovery?1600:pose.resetTicks*50)
                .next(user->ComboStateRegistry.NONE.getId()).nextOfTimeout(user->LegacyCombat.id(pose==LegacyMove.SLASH_DIM || pose==LegacyMove.IAI?LegacyMove.NONE:LegacyMove.NOUTOU))
                .clickAction(user->{if(recovery){LegacySheathingRepair.begin(user,pose);user.swing(InteractionHand.MAIN_HAND);}else if(user instanceof Player player)start(player,plan);})
                .addHitEffect((target,user)->LegacyCombat.impact(user,target,pose)).build());
    }
    public static LegacyMove visual(ResourceLocation combo){var plan=PLANS.get(combo);return plan==null?null:plan.pose;}
    public static Art resolve(ResourceLocation key) {
        if(key==null)return null;
        if(key.getNamespace().equals(LegacyCompat.MOD_ID))for(var art:Art.values())if(key.getPath().equals(art.key))return art;
        if(!key.getNamespace().equals("slashblade"))return null;
        return switch(key.getPath()) {
            // r87 unknown base IDs use its default SlashDimension. Preserve modern void_slash's saved identity.
            case "judgement_cut","void_slash"->Art.DIMENSION;
            case "drive_vertical"->Art.DRIVE;case "drive_horizontal"->Art.QUICK;case "wave_edge"->Art.WAVE;
            case "piercing"->Art.SPEAR;case "circle_slash"->Art.CIRCLE;case "sakura_end"->Art.SAKURA;default->null;
        };
    }
    public static ResourceLocation select(SlashArts arts,SlashArts.ArtsType type,LivingEntity user) {
        if(!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) || !(user instanceof Player))return null;
        var art=resolve(SlashArts.getRegistryKey(arts));
        if(type==SlashArts.ArtsType.Super) {
            if(art!=null)return combo(Art.DIMENSION,type,false);
            // Old ISuperSpecialAttack implementations may supply their own Super. Otherwise
            // the framework's default (or no Super) falls back to r87 SlashDimension.
            var selected=arts.getComboStateSuper().apply(user);
            if(selected==null || selected.equals(ComboStateRegistry.NONE.getId()) || selected.equals(ComboStateRegistry.JUDGEMENT_CUT_END.getId()))return combo(Art.DIMENSION,type,false);
            return selected;
        }
        if(art==null)return null;
        return type==SlashArts.ArtsType.Fail?ComboStateRegistry.NONE.getId():combo(art,type,false);
    }
    public static boolean release(ItemStack blade,Level level,LivingEntity user,int timeLeft) {
        if(!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) || !(user instanceof Player player))return false;
        var state=BladeStateAccess.of(blade).orElse(null);if(state==null || resolve(state.getSlashArtsKey())==null)return false;
        if(level.isClientSide || !LegacyDamage.holding(player,blade) || state.isBroken() || state.isSealed() || !SwordType.from(blade).contains(SwordType.ENCHANTED))return true;
        var result=state.doChargeAction(player,blade.getUseDuration(user)-timeLeft);
        if(result!=null && !result.equals(ComboStateRegistry.NONE.getId())) {
            // Core legacy combos pay in their click action, after PerformSlashArtEvent acceptance.
            // A foreign event redirect retains the original framework's post-action cost convention.
            if(!PLANS.containsKey(result) && LegacyDamage.holding(player,blade) && !player.isCreative())pay(player,blade,state.getSlashArts().getProudSoulCost(),1);
            player.swing(InteractionHand.MAIN_HAND);
        }
        return true;
    }
    private static boolean pay(Player player,ItemStack blade,int souls,int wear) {
        var state=BladeStateAccess.of(blade).orElseThrow();
        if(state.getProudSoulCount()>=souls)state.setProudSoulCount(state.getProudSoulCount()-souls);else LegacyAdditionalAttack.wear(blade,wear,player);
        return LegacyDamage.holding(player,blade);
    }
    public static int power(Player player,ItemStack blade){return blade.getEnchantmentLevel(player.registryAccess().holderOrThrow(Enchantments.POWER));}
    public static float driveDamage(Player player,ItemStack blade) {
        var state=BladeStateAccess.of(blade).orElseThrow();
        return state.getBaseAttackModifier()+(LegacyCombat.rank(player)>=5?state.getAttackAmplifier()*(.5f+power(player,blade)/5f):0);
    }
    private static float waveDamage(Player player,ItemStack blade) {
        var state=BladeStateAccess.of(blade).orElseThrow();
        return state.getBaseAttackModifier()/2+(LegacyCombat.rank(player)>=5?state.getAttackAmplifier()*(.25f+Math.max(1,power(player,blade))/5f):0);
    }
    public static LegacyDrive drive(Player player,ItemStack blade,float damage,float speed,float roll,boolean multi,int life) {
        var entity=new LegacyDrive(SummonedBladeMode.DRIVE.get(),player.level());entity.initialize(player,blade,damage,speed,roll,multi);entity.setLifetime(life);player.level().addFreshEntity(entity);return entity;
    }
    public static void melee(Player player,ItemStack blade,AABB area,LegacyMove pose,String action,float award,boolean crit) {
        for(var target:LegacyTargets.within(player,area)) {
            if(!LegacyDamage.holding(player,blade))return;
            if(LegacyDamage.hit(player,target,pose,area,true,1,action,award) && crit)player.magicCrit(target);
        }
    }
    private static void start(Player player,Plan plan) {
        LegacySheathingRepair.begin(player,plan.pose);player.swing(InteractionHand.MAIN_HAND);
        if(player.level().isClientSide)return;
        var blade=player.getMainHandItem();if(!LegacyDamage.holding(player,blade))return;
        LegacyDamage.update(player);var art=plan.art;var state=BladeStateAccess.of(blade).orElseThrow();
        if(plan.type==SlashArts.ArtsType.Super) {
            LegacyArtEntity.spawn(player,blade,LegacyArtEntity.Mode.JUDGEMENT,player.position(),30,0,false);
            mods.flammpfeil.slashblade.ability.Untouchable.setUntouchable(player,30);return;
        }
        var target=art==Art.DIMENSION || art==Art.WITHER?findTarget(player,blade):null;
        if(art==Art.WITHER && target==null)return;
        if(!pay(player,blade,art.souls,art.wear))return;
        switch(art) {
            case DRIVE,QUICK->drive(player,blade,driveDamage(player,blade),art==Art.DRIVE?.75f:1.5f,90-art.pose.direction,art==Art.DRIVE,art==Art.DRIVE?20:10);
            case WAVE->{float damage=waveDamage(player,blade);for(float speed:new float[]{.25f,.3f,.35f})drive(player,blade,damage,speed,0,true,20);drive(player,blade,damage,.225f,0,false,20);}
            case CIRCLE->{sound(player,SoundEvents.BLAZE_HURT,.2f,.6f);melee(player,blade,player.getBoundingBox().inflate(5,.25,5),art.pose,"CircleSlash",.3f,true);if(!LegacyDamage.holding(player,blade))return;float damage=waveDamage(player,blade);for(int i=0;i<6;i++){var entity=drive(player,blade,damage,.5f,90,true,10);entity.setVector(player.getYRot()+60*i,0,.5f);}}
            case SPEAR->{var velocity=Vec3.directionFromRotation(0,player.getYRot()).scale(player.onGround()?3.5:3.5*.35);player.setDeltaMovement(velocity.x,player.getDeltaMovement().y,velocity.z);syncMotion(player);player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,10,0));sound(player,SoundEvents.GENERIC_EXPLODE,1,1);LegacyArtEntity.spawn(player,blade,LegacyArtEntity.Mode.SPEAR,player.position(),7,0,false);}
            case SAKURA,MAXIMUM->LegacyArtEntity.spawn(player,blade,art==Art.SAKURA?LegacyArtEntity.Mode.SAKURA:LegacyArtEntity.Mode.MAXIMUM,player.position(),5,0,false);
            case DIMENSION->dimension(player,blade,target,plan.type==SlashArts.ArtsType.Jackpot);
            case WITHER->{
                if(LegacyDamage.hit(player,target,LegacyMove.SLASH_DIM,target.getBoundingBox(),true,1,"PhantomSword",.2f)){player.crit(target);target.setDeltaMovement(Vec3.ZERO);target.hurtTime=0;target.invulnerableTime=0;mods.flammpfeil.slashblade.ability.StunManager.setStun(target);}
                if(!LegacyDamage.holding(player,blade))return;
                float damage=1+state.getAttackAmplifier()*power(player,blade)/5f;
                for(int i=0,n=1+LegacyCombat.rank(player);i<n;i++) {
                    var sword=new LegacyPhantomSword(SummonedBladeMode.SWORD.get(),player.level());sword.initializeWither(player,LegacyRangeAttack.sourceId(blade),i,damage,target);player.level().addFreshEntity(sword);
                }
            }
        }
    }
    private static LivingEntity findTarget(Player player,ItemStack blade) {
        var locked=BladeStateAccess.of(blade).orElseThrow().getTargetEntity(player.level());
        if(locked instanceof LivingEntity living && player.distanceToSqr(living)<900 && LegacyTargets.attackable(player,living))return living;
        for(int distance=2;distance<20;distance+=2) {
            var candidates=LegacyTargets.within(player,player.getBoundingBox().inflate(2,.25,2).move(player.getLookAngle().scale(distance)));
            var target=candidates.stream().filter(e->e instanceof LivingEntity && player.distanceToSqr(e)<900).min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
            if(target instanceof LivingEntity living)return living;
        }
        return null;
    }
    private static void dimension(Player player,ItemStack blade,LivingEntity target,boolean just) {
        sound(player,SoundEvents.ENDERMAN_TELEPORT,.5f,1);var state=BladeStateAccess.of(blade).orElseThrow();
        Vec3 center;
        if(target==null) {
            center=player.getEyePosition().add(player.getLookAngle().scale(5));
            var collision=player.level().clip(new ClipContext(player.getEyePosition(),center,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
            if(collision.getType()!=HitResult.Type.MISS && player.position().distanceToSqr(collision.getLocation())>1)center=collision.getLocation();
        }else {
            center=target.position().add(0,target.getBbHeight()/2,0);
            melee(player,blade,target.getBoundingBox().inflate(2,.25,2),LegacyMove.SLASH_DIM,"SLASH_DIM",.6f,just);
        }
        if(!LegacyDamage.holding(player,blade))return;
        float power=power(player,blade)/5f;
        float damage=(target==null || just?1:.5f)+state.getAttackAmplifier()*(target==null?.5f+power:power);
        LegacyArtEntity.spawn(player,blade,LegacyArtEntity.Mode.DIMENSION,center,10,damage,target==null || just);
        if(just && (target==null || power>0)) {
            Vec3 driveCenter=target==null?center:target.position().add(0,target.getEyeHeight()/2,0);
            spread(player,blade,driveCenter,target==null?0:target.getYRot(),Math.min(1,(1+state.getAttackAmplifier()*power)/3));
        }
    }
    public static void spread(Player player,ItemStack blade,Vec3 center,float baseYaw,float damage) {
        for(int i=0;i<5;i++) {
            float yaw=baseYaw+60*i+(player.getRandom().nextFloat()-.5f)*60,pitch=(player.getRandom().nextFloat()-.5f)*60;
            var entity=drive(player,blade,damage,.5f,90+120*(player.getRandom().nextFloat()-.5f),true,8);entity.setVector(yaw,pitch,.5f);
            var offset=entity.getDeltaMovement();entity.setPos(center.subtract(offset.multiply(2,1,2)));
            entity.setDimension(LegacyCombat.rank(player)>=5 && SwordType.from(blade).contains(SwordType.FIERCEREDGE));
        }
    }
    public static void recovery(Player player,Art art) {BladeStateAccess.of(player.getMainHandItem()).ifPresent(state->state.updateComboSeq(player,combo(art,art==Art.DIMENSION?SlashArts.ArtsType.Super:SlashArts.ArtsType.Success,true)));}
    public static void finishSuper(Player player,ItemStack blade) {
        if(LegacyDamage.holding(player,blade))recovery(player,Art.DIMENSION);
        else {BladeStateAccess.of(blade).ifPresent(state->state.setComboSeq(combo(Art.DIMENSION,SlashArts.ArtsType.Super,true)));player.swing(InteractionHand.MAIN_HAND);}
        blade.setDamageValue(blade.getMaxDamage()/2);
    }
    public static void syncMotion(Player player){player.hurtMarked=true;if(player instanceof ServerPlayer server)server.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));}
    public static void sound(Player player,SoundEvent sound,float volume,float pitch){player.level().playSound(null,player.blockPosition(),sound,SoundSource.PLAYERS,volume,pitch);}
    public static void sound(Player player,net.minecraft.core.Holder<SoundEvent> sound,float volume,float pitch){sound(player,sound.value(),volume,pitch);}
}
