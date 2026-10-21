package org.scex.slashbladelegacy.contracts;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import org.scex.slashbladelegacy.*;

/** Physical and forced impacts must share old damage and one public framework notification. */
final class ProjectileInteropContracts {
    private static int checks;
    static void run(MinecraftServer server,Map<String,Object> report){
        checks=0;var level=server.overworld();
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"SwordInterop"));
        p.setPos(-8,160,-8);level.addNewPlayer(p);
        var blade=server.registryAccess().registryOrThrow(SlashBladeDefinition.REGISTRY_KEY).holders().filter(h->h.key().location().toString().equals("slashblade:sange")).findFirst().orElseThrow().value().getBlade();
        var state=BladeStateAccess.of(blade).orElseThrow();state.setBroken(false);state.setSealed(false);state.setSpecialEffects(new net.minecraft.nbt.ListTag());blade.setDamageValue(0);blade.enchant(Enchantments.POWER_ARROWS,1);
        p.setItemInHand(InteractionHand.MAIN_HAND,blade);UUID source=LegacyRangeAttack.sourceId(blade);
        var target=new Zombie(level){@Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){return source.getDirectEntity()==p && super.hurt(source,amount);}};
        target.setPos(-8,160,-3.5);target.setNoAi(true);target.setNoGravity(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.setHealth(200);level.addFreshEntity(target);
        var events=new ArrayList<EntityAbstractSummonedSword>();
        Consumer<SlashBladeEvent.SummonedSwordOnHitEntityEvent> listener=e->{
            if(e.getTarget()!=target)return;
            events.add(e.getSummonedSword());
            // A third-party listener re-enters the public method. It must not apply another hit.
            if(events.size()==1)e.getSummonedSword().doForceHitEntity(target);
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        var spawned=new ArrayList<EntityAbstractSummonedSword>();
        try {
            for(boolean sb:new boolean[]{false,true})for(boolean force:new boolean[]{false,true}){
                EntityAbstractSummonedSword sword;
                if(sb){var s=new LegacySummonedBlade(SummonedBladeMode.BLADE.get(),level);s.initialize(p,4,0x3333ff,source,target);sword=s;}
                else {var s=new LegacyPhantomSword(SummonedBladeMode.SWORD.get(),level);s.initialize(p,source,LegacyRangeAttack.Art.SINGLE,0,4,0x3333ff,target,null,false);sword=s;}
                spawned.add(sword);sword.setPos(-8,160.5,-4);sword.setDeltaMovement(0,0,1);level.addFreshEntity(sword);
                events.clear();target.invulnerableTime=0;float before=target.getHealth();int wear=blade.getDamageValue();
                if(force)sword.doForceHitEntity(target);else sword.tick();
                check(events.size()==1 && events.get(0)==sword,"one public hit event "+sb+"/"+force);
                check(target.getHealth()==before-4,"one old direct-player damage, including reentrant callback "+sb+"/"+force);
                check(blade.getDamageValue()==wear+1,"source hit callback/durability once "+sb+"/"+force);
                sword.doForceHitEntity(target);check(events.size()==1 && target.getHealth()==before-4,"attached sword cannot re-enter native damage");sword.discard();
            }
            var refused=new LegacyPhantomSword(SummonedBladeMode.SWORD.get(),level);refused.initialize(p,source,LegacyRangeAttack.Art.SINGLE,0,4,0x3333ff,target,null,false);level.addFreshEntity(refused);spawned.add(refused);
            Consumer<LivingAttackEvent> cancel=e->{if(e.getEntity()==target)e.setCanceled(true);};MinecraftForge.EVENT_BUS.addListener(cancel);
            events.clear();float hp=target.getHealth();
            try{refused.doForceHitEntity(target);}finally{MinecraftForge.EVENT_BUS.unregister(cancel);}
            check(events.isEmpty() && target.getHealth()==hp,"damage protection also suppresses addon impact effects");
            check(!refused.canBeHitByProjectile(),"legacy sword is not a projectile collision target");
            var modern=new EntityAbstractSummonedSword(mods.flammpfeil.slashblade.SlashBlade.RegistryEvents.SummonedSword,level);modern.setOwner(p);
            check(!modern.canBeHitByProjectile() && !LegacyProjectileGuard.destructible(p,modern),"same-owner modern sword is neither collidable nor redirected by old guard");
        }finally{spawned.forEach(Entity::discard);MinecraftForge.EVENT_BUS.unregister(listener);target.discard();p.discard();report.put("projectile_interop_checks",checks);}
    }
    private static void check(boolean ok,String message){++checks;if(!ok)throw new AssertionError("Projectile interop: "+message);}
}
