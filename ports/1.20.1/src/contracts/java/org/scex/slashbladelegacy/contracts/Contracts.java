package org.scex.slashbladelegacy.contracts;

import com.google.gson.GsonBuilder;
import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import mods.flammpfeil.slashblade.util.AttackHelper;
import mods.flammpfeil.slashblade.util.TargetSelector;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import org.scex.slashbladelegacy.LegacyCompat;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Uses actual loaded items, attributes, damage, serialization; not a client acceptance claim. */
@Mod("slashblade_legacy_contracts")
public final class Contracts {
    public Contracts() { MinecraftForge.EVENT_BUS.addListener(this::started); }
    private void started(ServerStartedEvent event) {
        if(Boolean.getBoolean("scex.legacy.clientProbe") || Boolean.getBoolean("scex.legacy.dualModeProbe") || Boolean.getBoolean("scex.legacy.renderStallProbe"))return;
        if(Boolean.getBoolean("scex.legacy17Load")){
            ContractWorldReady.prepare(event.getServer(),preparation -> Legacy17LoadProbe.start(event.getServer(),preparation));return;
        }
        ContractWorldReady.prepare(event.getServer(),preparation -> run(event.getServer(),preparation));
    }
    private void run(net.minecraft.server.MinecraftServer server,java.util.Map<String,Object> preparation) {
        var report = new LinkedHashMap<String,Object>();
        report.put("fixture_preparation",preparation);
        try {
            require(Boolean.TRUE.equals(preparation.get("ready")),"Fixture entity lifecycle not ready: "+preparation);
            if(Files.exists(Path.of("integration-20261021.flag"))){
                report.put("suite","integration-20261021");
                Integration20261021Contracts.run(server,report);
                AirTrickContracts.run(server,report);
                PerformanceContracts.run(server,report);
                PerformanceContracts.regressions(server,report);
                DualModeContracts.run(server,report);
                Legacy17Contracts.run(server,report);
                report.put("success",true);return;
            }
            if(Files.exists(Path.of("backcompat-20261013.flag"))){
                report.put("suite","backcompat-20261013");
                BackcompatContracts.run(server,report);
                boolean modern=modernBackcompatEvents();report.put("modern_extension_contracts",modern);
                if(modern){DeathContracts.run(server,report);UnbreakableContracts.run(server,report);}
                report.put("success",true);return;
            }
            if(Files.exists(Path.of("death-enchantment-20261013.flag"))){
                report.put("suite","death-enchantment-20261013");
                DeathContracts.run(server,report);
                UnbreakableContracts.run(server,report);
                report.put("success",true);return;
            }
            if(Files.exists(Path.of("logout-unbreakable-20261012.flag"))){
                report.put("suite","logout-unbreakable-20261012");
                LogoutContracts.run(server,report);
                UnbreakableContracts.run(server,report);
                report.put("success",true);return;
            }
            if(Files.exists(Path.of("sjap-compat-20261012.flag"))){
                SjapCompatibilityContracts.run(server,report);
                report.put("success",true);return;
            }
            if(Files.exists(Path.of("port-1201.flag"))){
                runPort1201(server,report);
                report.put("success",true);return;
            }
            if(Files.exists(Path.of("airtrick-20261011.flag"))){
                AirTrickContracts.run(server,report);
                Avoid17Contracts.run(server,report);
                Super17Contracts.run(server,report);
                FeedbackInputContracts.run(server,report);
                ProjectileInteropContracts.run(server,report);
                BladeColorContracts.run(server,report);
                report.put("success",true);return;
            }
            if(Files.exists(Path.of("upstream-compat.flag"))){
                MaidTimelineContracts.runTimelineOnly(server,report);
                Legacy17Contracts.run(server,report);
            }
            boolean performance=Files.exists(Path.of("performance-20261008.flag"));
            if(performance){PerformanceContracts.run(server,report);PerformanceContracts.regressions(server,report);}
            if(performance || Files.exists(Path.of("feedback-20261008.flag"))){
                FeedbackInputContracts.run(server,report);
                var player=net.minecraftforge.common.util.FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"FeedbackStand"));
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(mods.flammpfeil.slashblade.registry.SlashBladeItems.SLASHBLADE.get()));
                server.overworld().addNewPlayer(player);
                try{BladeStandFeedbackContracts.run(player,report);FeedbackArtsContracts.run(player,report);NamedBladeFeedbackContracts.run(player,report);}finally{player.discard();}
                DualModeContracts.run(server,report);DualWieldContracts.run(server,report);Avoid17Contracts.run(server,report);ProjectileInteropContracts.run(server,report);
                report.put("success",true);return;
            }
            if(Files.exists(Path.of("bugfix-20260917.flag"))){Bugfix17Contracts.run(server,report);Super17Contracts.run(server,report);report.put("success",true);return;}
            if(Files.exists(Path.of("maid-timeline-contracts.flag"))){MaidTimelineContracts.run(server,report);DualModeContracts.run(server,report);ProjectileInteropContracts.run(server,report);BatchPhantomContracts.run(server,report);DualWieldContracts.run(server,report);Legacy17Contracts.run(server,report);report.put("success",true);return;}
            if(Files.exists(Path.of("dual-mode-contracts.flag"))){DualModeContracts.run(server,report);ProjectileInteropContracts.run(server,report);BatchPhantomContracts.run(server,report);DualWieldContracts.run(server,report);Legacy17Contracts.run(server,report);report.put("success",true);return;}
            if(Files.exists(Path.of("phantom-pack-contracts.flag"))){PhantomPackContracts.run(server,report);report.put("success",true);return;}
            if(Files.exists(Path.of("projectile-interop-contracts.flag"))){ProjectileInteropContracts.run(server,report);if(Files.exists(Path.of("stability-full-contracts.flag"))){BatchPhantomContracts.run(server,report);DualWieldContracts.run(server,report);Legacy17Contracts.run(server,report);}report.put("success",true);return;}
            if(Files.exists(Path.of("dual-wield-contracts.flag"))){DualWieldContracts.run(server,report);Legacy17Contracts.run(server,report);report.put("success",true);return;}
            if(Files.exists(Path.of("batch-phantom-contracts.flag"))){BatchPhantomContracts.run(server,report);report.put("success",true);return;}
            if(Files.exists(Path.of("feather-contracts.flag"))){FeatherContracts.run(server,report);report.put("success",true);return;}
            if(Files.exists(Path.of("super-contracts.flag"))){Super17Contracts.run(server,report);report.put("success",true);return;}
            if(Files.exists(Path.of("avoid-contracts.flag"))){Avoid17Contracts.run(server,report);report.put("success",true);return;}
            if(Files.exists(Path.of("followup-contracts.flag"))){Followup17Contracts.run(server,report);Advancement17Contracts.run(server,report);report.put("success",true);return;}
            if(Files.exists(Path.of("saved-left.snbt"))){SavedLeftContracts.run(server,report);report.put("success",true);return;}
            if(Boolean.getBoolean("scex.legacy17Contracts")){Legacy17Contracts.run(server,report);report.put("success",true);return;}
            var level = server.overworld();
            level.getChunk(0,0);
            for(int x=-2;x<=2;x++) for(int y=160;y<=163;y++)
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x,y,1),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            var definition = server.registryAccess().registryOrThrow(SlashBladeDefinition.REGISTRY_KEY)
                    .holders().filter(h -> h.key().location().toString().equals("slashblade:sange"))
                    .findFirst().orElseThrow();
            report.put("baseline_blade_definition",definition.key().location().toString());
            var blade=definition.value().getBlade();
            var state = BladeStateAccess.of(blade).orElseThrow();
            blade.removeTagKey("Enchantments");
            state.setBroken(false);
            state.setDamage(0);
            var healthyAttrs = blade.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            double healthyReach = amount(healthyAttrs, net.minecraftforge.common.ForgeMod.ENTITY_REACH.get());
            state.setBroken(true);
            state.setDamage(state.getMaxDamage() - 1);
            LegacyCompat.BROKEN_DAMAGE.set(false);
            LegacyCompat.BROKEN_REACH.set(false);
            double beforeDamage = amount(blade.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND), Attributes.ATTACK_DAMAGE);
            double beforeReach = amount(blade.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND), net.minecraftforge.common.ForgeMod.ENTITY_REACH.get());
            require(beforeDamage == -1.5 && beforeReach == 1.25, "Current JAR baseline changed");
            LegacyCompat.BROKEN_DAMAGE.set(true);
            LegacyCompat.BROKEN_REACH.set(true);
            require(amount(blade.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND), Attributes.ATTACK_DAMAGE) == 2, "Legacy modifier");
            require(amount(blade.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND), net.minecraftforge.common.ForgeMod.ENTITY_REACH.get()) == healthyReach, "Broken range equality");
            var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                    new GameProfile(UUID.randomUUID(),"LegacyContract"));
            player.setPos(0,160,0);
            player.setItemSlot(EquipmentSlot.MAINHAND,blade);
            blade.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND).forEach((attribute,modifier) -> ContractItems.attribute(player,attribute,modifier));
            var zombie = EntityType.ZOMBIE.create(level);
            zombie.setPos(0,160,2);
            zombie.getAttribute(Attributes.ARMOR).setBaseValue(0);
            require(player.getAttributeValue(Attributes.ATTACK_DAMAGE) == 3, "Player total damage");
            float before = zombie.getHealth();
            mods.flammpfeil.slashblade.util.AttackManager.doMeleeAttack(player,zombie,true,true,1);
            require(zombie.getHealth() < before, "Broken blade actual damage");
            require(BladeStateAccess.of(blade).orElseThrow().isBroken(), "Hit must not repair blade");
            require(!blade.isEmpty(), "Broken blade lost");
            report.put("actual_broken_damage", before-zombie.getHealth());
            report.put("healthy_entity_modifier", healthyReach);
            report.put("broken_entity_modifier_before",beforeReach);
            report.put("resolved_melee_reach",TargetSelector.getResolvedReach(player));
            var saved = blade.save(new net.minecraft.nbt.CompoundTag());
            var restored = ContractItems.parse(saved).orElseThrow();
            require(BladeStateAccess.of(restored).orElseThrow().isBroken(), "Broken save/load");
            require(amount(restored.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND),Attributes.ATTACK_DAMAGE)==2, "Reload attributes");
            state = BladeStateAccess.of(blade).orElseThrow();
            blade.setDamageValue(0);
            require(!BladeStateAccess.of(blade).orElseThrow().isBroken(), "Vanilla blade repair semantic");
            require(blade.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND).equals(healthyAttrs), "Normal attributes changed");
            int[] breaks = {0};
            blade.setDamageValue(blade.getMaxDamage()-1);
            var bladeItem = (mods.flammpfeil.slashblade.item.ItemSlashBlade)blade.getItem();
            bladeItem.damageItem(blade,1,player,item -> breaks[0]++);
            require(BladeStateAccess.of(blade).orElseThrow().isBroken(),"Wear failed to create broken state");
            for(int i=0;i<20;i++) bladeItem.damageItem(blade,1,player,item -> breaks[0]++);
            require(breaks[0]==1 && !blade.isEmpty(),"Repeated break callback or item loss");
            require(blade.getDamageValue()==blade.getMaxDamage()-1,"Broken damage clamp changed");
            report.put("wear_transition_and_20_repeated_wear",true);
            level.getChunk(0,0);
            var near = EntityType.ZOMBIE.create(level);
            var far = EntityType.ZOMBIE.create(level);
            near.setPos(0,160,3); far.setPos(0,160,6);
            level.addFreshEntity(near); level.addFreshEntity(far);
            player.setYRot(0); player.setXRot(0);
            var targets = TargetSelector.getTargettableEntitiesWithinAABB(level,player);
            require(targets.contains(near) && !targets.contains(far),"Server range boundary");
            for(int x=-2;x<=2;x++) for(int y=160;y<=163;y++)
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x,y,1),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            float wallHealth=near.getHealth();
            mods.flammpfeil.slashblade.util.AttackManager.doMeleeAttack(player,near,true,true,1);
            require(near.getHealth()==wallHealth,"Wall blocked actual damage");
            float farHealth=far.getHealth();
            mods.flammpfeil.slashblade.util.AttackManager.doMeleeAttack(player,far,true,true,1);
            require(far.getHealth()==farHealth,"Out of range actual damage");
            for(int x=-2;x<=2;x++) for(int y=160;y<=163;y++)
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x,y,1),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            near.discard(); far.discard();
            report.put("server_range_and_wall",true);
            var explicit=new AttributeModifier(UUID.randomUUID(),"test override",.75,AttributeModifier.Operation.ADDITION);
            blade.addAttributeModifier(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(),explicit,EquipmentSlot.MAINHAND);
            state.setBroken(true);
            require(amount(blade.getAttributeModifiers(EquipmentSlot.MAINHAND),net.minecraftforge.common.ForgeMod.ENTITY_REACH.get())==.75,
                    "Explicit item attribute override ignored");
            SbContracts.run(player,blade,report);
            GaiaContracts.run(player,player.getMainHandItem(),report);
            TargetAudit.run(level,report);
            TargetAudit.copyRules(player,report);
            CombatContracts.run(player,report);
            AdvancedContracts.run(player,report);
            GuardContracts.run(player,report);
            SheathingContracts.run(player,report);
            TauntContracts.run(player,report);
            RegressionContracts.run(player,report);
            SiContracts.run(player,report);
            AnimationContracts.run(player,report);
            if(Boolean.getBoolean("scex.dev9Audit"))Dev9Audit.run(player,report);
            report.put("success",true);
        } catch (Throwable failure) {
            report.put("success",false);
            report.put("error",failure.toString());
            report.put("error_stack",java.util.Arrays.stream(failure.getStackTrace()).map(Object::toString).toList());
            failure.printStackTrace();
        } finally {
            ContractWorldReady.release();
            try { Files.writeString(Path.of("contracts.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
            catch (Exception e) { throw new RuntimeException(e); }
            server.halt(false);
        }
    }
    private static double amount(Multimap<Attribute,AttributeModifier> attrs,Attribute type) {
        return attrs.get(type).stream().mapToDouble(AttributeModifier::getAmount).sum();
    }
    private static void runPort1201(net.minecraft.server.MinecraftServer server,java.util.Map<String,Object> report) throws Exception {
        report.put("suite","port-1201");
        Legacy17Contracts.run(server,report);
        DualModeContracts.run(server,report);
        DualWieldContracts.run(server,report);
        FeedbackInputContracts.run(server,report);
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"PortStand"));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(mods.flammpfeil.slashblade.registry.SlashBladeItems.SLASHBLADE.get()));
        server.overworld().addNewPlayer(player);
        try {
            BladeStandFeedbackContracts.run(player,report);
            FeedbackArtsContracts.run(player,report);
            NamedBladeFeedbackContracts.run(player,report);
        } finally { player.discard(); }
        AirTrickContracts.run(server,report);
        Avoid17Contracts.run(server,report);
        Super17Contracts.run(server,report);
        ProjectileInteropContracts.run(server,report);
        BladeColorContracts.run(server,report);
        // Legacy17Contracts also checks follow-up attacks and advancements.
        MaidTimelineContracts.runTimelineOnly(server,report);
    }
    private static boolean modernBackcompatEvents(){
        try{
            Class.forName("mods.flammpfeil.slashblade.event.SlashBladeEvent$BreakEvent",false,Contracts.class.getClassLoader());
            Class.forName("mods.flammpfeil.slashblade.event.SlashBladeEvent$PerformSlashArtEvent",false,Contracts.class.getClassLoader());
            return true;
        }catch(ClassNotFoundException oldApi){return false;}
    }
    private static void require(boolean result,String message) { if (!result) throw new AssertionError(message); }
}
