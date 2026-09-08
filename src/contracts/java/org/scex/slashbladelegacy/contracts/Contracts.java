package org.scex.slashbladelegacy.contracts;

import com.google.gson.GsonBuilder;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import mods.flammpfeil.slashblade.util.AttackHelper;
import mods.flammpfeil.slashblade.util.TargetSelector;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.scex.slashbladelegacy.LegacyCompat;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Uses actual loaded items, attributes, damage, serialization; not a client acceptance claim. */
@Mod("slashblade_legacy_contracts")
public final class Contracts {
    public Contracts() { NeoForge.EVENT_BUS.addListener(this::started); }
    private void started(ServerStartedEvent event) {
        if(Boolean.getBoolean("scex.legacy.clientProbe"))return;
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
            if(Files.exists(Path.of("feather-contracts.flag"))){FeatherContracts.run(server,report);report.put("success",true);return;}
            if(Files.exists(Path.of("super-contracts.flag"))){Super17Contracts.run(server,report);report.put("success",true);return;}
            if(Files.exists(Path.of("avoid-contracts.flag"))){Avoid17Contracts.run(server,report);report.put("success",true);return;}
            if(Files.exists(Path.of("saved-left.snbt"))){SavedLeftContracts.run(server,report);report.put("success",true);return;}
            if(Boolean.getBoolean("scex.legacy17Contracts")){Legacy17Contracts.run(server,report);report.put("success",true);return;}
            var level = server.overworld();
            level.getChunk(0,0);
            for(int x=-2;x<=2;x++) for(int y=160;y<=163;y++)
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x,y,1),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            var definition = server.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY)
                    .listElements().filter(h -> h.key().location().toString().equals("slashblade:sange"))
                    .findFirst().orElseThrow();
            report.put("baseline_blade_definition",definition.key().location().toString());
            var blade=definition.value().getBlade(server.registryAccess());
            var state = BladeStateAccess.of(blade).orElseThrow();
            blade.set(DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
            state.setBroken(false);
            state.setDamage(0);
            var healthyAttrs = blade.getAttributeModifiers();
            double healthyReach = amount(healthyAttrs, Attributes.ENTITY_INTERACTION_RANGE);
            state.setBroken(true);
            state.setDamage(state.getMaxDamage() - 1);
            LegacyCompat.BROKEN_DAMAGE.set(false);
            LegacyCompat.BROKEN_REACH.set(false);
            double beforeDamage = amount(blade.getAttributeModifiers(), Attributes.ATTACK_DAMAGE);
            double beforeReach = amount(blade.getAttributeModifiers(), Attributes.ENTITY_INTERACTION_RANGE);
            require(beforeDamage == -1.5 && beforeReach == 1.25, "Current JAR baseline changed");
            LegacyCompat.BROKEN_DAMAGE.set(true);
            LegacyCompat.BROKEN_REACH.set(true);
            require(amount(blade.getAttributeModifiers(), Attributes.ATTACK_DAMAGE) == 2, "Legacy modifier");
            require(amount(blade.getAttributeModifiers(), Attributes.ENTITY_INTERACTION_RANGE) == healthyReach, "Broken range equality");
            var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                    new GameProfile(UUID.randomUUID(),"LegacyContract"));
            player.setPos(0,160,0);
            player.setItemSlot(EquipmentSlot.MAINHAND,blade);
            blade.getAttributeModifiers().forEach(EquipmentSlot.MAINHAND,(attribute,modifier) -> player.getAttribute(attribute).addOrUpdateTransientModifier(modifier));
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
            var saved = blade.save(server.registryAccess());
            var restored = ItemStack.parse(server.registryAccess(),saved).orElseThrow();
            require(BladeStateAccess.of(restored).orElseThrow().isBroken(), "Broken save/load");
            require(amount(restored.getAttributeModifiers(),Attributes.ATTACK_DAMAGE)==2, "Reload attributes");
            state = BladeStateAccess.of(blade).orElseThrow();
            blade.setDamageValue(0);
            require(!BladeStateAccess.of(blade).orElseThrow().isBroken(), "Vanilla blade repair semantic");
            require(blade.getAttributeModifiers().equals(healthyAttrs), "Normal attributes changed");
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
            var override = ItemAttributeModifiers.builder().add(Attributes.ENTITY_INTERACTION_RANGE,
                    new net.minecraft.world.entity.ai.attributes.AttributeModifier(ResourceLocation.parse("test:override"),
                            0.75,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                    net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND).build();
            blade.set(DataComponents.ATTRIBUTE_MODIFIERS,override);
            state.setBroken(true);
            require(blade.getAttributeModifiers().equals(override),"Explicit item attribute override ignored");
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
            failure.printStackTrace();
        } finally {
            ContractWorldReady.release();
            try { Files.writeString(Path.of("contracts.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
            catch (Exception e) { throw new RuntimeException(e); }
            server.halt(false);
        }
    }
    private static double amount(ItemAttributeModifiers attrs,net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> type) {
        return attrs.modifiers().stream().filter(e -> e.attribute().equals(type)).mapToDouble(e -> e.modifier().amount()).sum();
    }
    private static void require(boolean result,String message) { if (!result) throw new AssertionError(message); }
}
