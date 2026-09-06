package org.scex.slashbladelegacy.contracts;

import java.util.Map;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import mods.flammpfeil.slashblade.SlashBladeConfig;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.TargetSelector;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.scex.slashbladelegacy.LegacyCompat;

final class GaiaContracts {
    static void run(ServerPlayer fixture,ItemStack blade,Map<String,Object> report) throws Exception {
        var id=ResourceLocation.parse("botania:gaia_guardian");
        if(!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            report.put("gaia_test","Skipped: Botania absent; full-contracts required");return;
        }
        var level=fixture.serverLevel();
        var gaia=(LivingEntity)BuiltInRegistries.ENTITY_TYPE.get(id).create(level);
        var cow=EntityType.COW.create(level);
        // Botania intentionally rejects FakePlayer. Use a real ServerPlayer class, with
        // the fixture's packet sink; this is still an automated server test, not a client login.
        var player=new ServerPlayer(level.getServer(),level,
                new GameProfile(UUID.randomUUID(),"GaiaContract"),ClientInformation.createDefault());
        player.connection=fixture.connection;
        player.setPos(12,160,0);player.setYRot(0);player.setXRot(0);
        player.setItemInHand(InteractionHand.MAIN_HAND,blade.copy());
        level.addNewPlayer(player);
        player.getMainHandItem().getAttributeModifiers().forEach(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                (attribute,modifier)->player.getAttribute(attribute).addOrUpdateTransientModifier(modifier));
        gaia.setPos(12,160,3);cow.setPos(13,160,3);
        level.getChunk(0,0);level.addFreshEntity(gaia);level.addFreshEntity(cow);
        var setInvul=gaia.getClass().getMethod("setInvulTime",int.class);
        boolean friendly=SlashBladeConfig.FRIENDLY_ENABLE.get(),pvp=SlashBladeConfig.PVP_ENABLE.get();
        boolean legacyCombat=LegacyCompat.LEGACY_COMBAT.get();
        LegacyCompat.LEGACY_COMBAT.set(false);
        try {
            SlashBladeConfig.FRIENDLY_ENABLE.set(false);SlashBladeConfig.PVP_ENABLE.set(false);
            LegacyCompat.GAIA_TARGETING.set(false);
            require(gaia.getLastHurtByMob()==null,"Gaia already attacked");
            require(!TargetSelector.getTargettableEntitiesWithinAABB(level,player).contains(gaia),"Baseline unexpectedly targets fresh Gaia");
            gaia.setLastHurtByMob(player);
            report.put("gaia_fixture",Map.of("reach",TargetSelector.getResolvedReach(player),
                    "distanceSquared",TargetSelector.distanceSqrBetweenEntity(player,gaia),
                    "directCondition",TargetSelector.test.test(player,gaia),"alive",gaia.isAlive(),
                    "removed",gaia.isRemoved(),"position",gaia.position().toString(),
                    "inWorld",level.getEntitiesOfClass(LivingEntity.class,gaia.getBoundingBox()).contains(gaia),
                    "look",player.getLookAngle().toString()));
            require(TargetSelector.test.test(player,gaia),"Prior attack does not enable revenge targeting");
            gaia.setLastHurtByMob(null);gaia.removeTag("RevengeAttacker");
            LegacyCompat.GAIA_TARGETING.set(true);
            var targets=TargetSelector.getTargettableEntitiesWithinAABB(level,player);
            require(targets.contains(gaia),"Fresh Gaia excluded with fix");
            require(!targets.contains(cow),"Friendly targeting broadened");
            require(!new TargetSelector.AttackablePredicate().test(fixture),"PVP disabled bypassed");
            require(gaia.getLastHurtByMob()==null && !gaia.getTags().contains("RevengeAttacker"),"Fix fabricated revenge state");
            setInvul.invoke(gaia,0);
            player.tick();
            player.setOnGround(true);
            var bladeState=mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess.of(player.getMainHandItem()).orElseThrow();
            bladeState.setComboSeq(mods.flammpfeil.slashblade.registry.ComboStateRegistry.NONE.getId());
            bladeState.setLastActionTime(level.getGameTime());
            float before=gaia.getHealth();
            InputClock.use(player,player.getMainHandItem());
            var slashes=level.getEntitiesOfClass(mods.flammpfeil.slashblade.entity.EntitySlashEffect.class,player.getBoundingBox().inflate(8));
            require(!slashes.isEmpty(),"Right click spawned no slash effect");
            for(var slash:slashes) {for(int tick=0;tick<4 && !slash.isRemoved();tick++)slash.tick();slash.discard();}
            float damage=before-gaia.getHealth();
            require(damage>0,"Fresh Gaia took no right-click use damage");
            require(damage<=32,"Gaia damage cap exceeded");
            gaia.invulnerableTime=0;
            float beforeHigh=gaia.getHealth();
            AttackManager.areaAttack(player,e->{},100,true,true,true);
            float cappedDamage=beforeHigh-gaia.getHealth();
            require(cappedDamage>0 && cappedDamage<=32,"Gaia high damage cap changed");
            setInvul.invoke(gaia,100);gaia.invulnerableTime=0;
            float protectedHealth=gaia.getHealth();
            AttackManager.areaAttack(player,e->{},1,true,true,true);
            require(gaia.getHealth()==protectedHealth,"Gaia spawn invulnerability bypassed");
            // Start the legacy graph against a newly created guardian: no previous attack or weapon switch.
            var fresh=(LivingEntity)BuiltInRegistries.ENTITY_TYPE.get(id).create(level);
            fresh.setPos(12,160,2);level.addFreshEntity(fresh);
            try {
                setInvul.invoke(fresh,0);LegacyCompat.LEGACY_COMBAT.set(true);
                bladeState.setComboSeq(mods.flammpfeil.slashblade.registry.ComboStateRegistry.NONE.getId());
                bladeState.setLastActionTime(level.getGameTime());
                player.setOnGround(true);
                float freshHealth=fresh.getHealth();
                InputClock.use(player,player.getMainHandItem());
                require(org.scex.slashbladelegacy.LegacyCombat.move(bladeState.getComboSeq())==org.scex.slashbladelegacy.LegacyMove.SAYA1,"Legacy Gaia test did not enter Saya1");
                float legacyDamage=freshHealth-fresh.getHealth();
                require(legacyDamage>0 && legacyDamage<=32,"Legacy first right click cannot damage fresh Gaia");
                setInvul.invoke(fresh,100);fresh.invulnerableTime=0;
                float invulnerableHealth=fresh.getHealth();
                InputClock.use(player,player.getMainHandItem());
                require(fresh.getHealth()==invulnerableHealth,"Legacy scabbard bypassed Gaia spawn invulnerability");
                var drive=new org.scex.slashbladelegacy.LegacyDrive(org.scex.slashbladelegacy.SummonedBladeMode.DRIVE.get(),level);
                try {
                    drive.initialize(player,player.getMainHandItem(),100,0,0,true);
                    drive.tick();drive.tick();
                    require(fresh.getHealth()==invulnerableHealth,"Legacy Drive bypassed Gaia invulnerability");
                    setInvul.invoke(fresh,0);fresh.invulnerableTime=0;
                    drive.tick();drive.tick();
                    float driveDamage=invulnerableHealth-fresh.getHealth();
                    require(driveDamage>0 && driveDamage<=32,"Legacy Drive Gaia cap/attribution");
                    report.put("gaia_legacy_drive",Map.of("capped_damage",driveDamage,"spawn_invulnerability_preserved",true));
                }finally{drive.discard();}
                player.getMainHandItem().enchant(level.registryAccess().holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.PUNCH),1);
                player.getMainHandItem().enchant(level.registryAccess().holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.POWER),1);
                bladeState.setDefaultBewitched(true);bladeState.setBroken(false);bladeState.setSealed(false);
                setInvul.invoke(fresh,100);float beforeBlast=fresh.getHealth();
                org.scex.slashbladelegacy.LegacyUpthrust.attach(player,fresh);
                org.scex.slashbladelegacy.LegacyUpthrust.blast(player,player.getMainHandItem());
                require(fresh.getHealth()==beforeBlast,"Upthrust bypassed Gaia invulnerability");
                setInvul.invoke(fresh,0);fresh.invulnerableTime=0;
                org.scex.slashbladelegacy.LegacyUpthrust.attach(player,fresh);
                org.scex.slashbladelegacy.LegacyUpthrust.blast(player,player.getMainHandItem());
                require(fresh.getHealth()<beforeBlast,"Upthrust cannot damage Gaia");
                report.put("gaia_upthrust_invulnerability",true);
                report.put("gaia_legacy_first_hit",Map.of("damage",legacyDamage,"fresh_entity",true,"spawn_invulnerability_preserved",true));
            }finally{fresh.discard();}
            report.put("gaia_first_hit",Map.of("damage",damage,"high_damage_capped",cappedDamage,"baseline_rejected",true,
                    "prior_hit_revenge_targeting",true,"fresh_target_fixed",true,"friendly_pvp_preserved",true,"spawn_invulnerability_preserved",true));
        } finally {
            LegacyCompat.GAIA_TARGETING.set(true);SlashBladeConfig.FRIENDLY_ENABLE.set(friendly);SlashBladeConfig.PVP_ENABLE.set(pvp);
            gaia.discard();cow.discard();
            LegacyCompat.LEGACY_COMBAT.set(legacyCombat);
            level.removePlayerImmediately(player,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
    }
    private static void require(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
}

