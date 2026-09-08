package org.scex.slashbladelegacy.contracts;

import java.util.*;
import com.mojang.authlib.GameProfile;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.scex.slashbladelegacy.*;

/** Event entry followed by real vanilla travel; GUI movement remains a separate acceptance. */
final class FeatherContracts {
    static void run(MinecraftServer server,Map<String,Object> report) {
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"FeatherContract"));
        player.setGameMode(GameType.SURVIVAL);player.setPos(8,240,8);player.setOnGround(false);
        var blade=server.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements()
                .filter(h->h.key().location().toString().equals("slashblade:sange")).findFirst().orElseThrow().value().getBlade(server.registryAccess());
        var feather=player.registryAccess().holderOrThrow(Enchantments.FEATHER_FALLING);
        var state=BladeStateAccess.of(blade).orElseThrow();state.setComboSeq(ComboStateRegistry.NONE.getId());state.setSpecialEffects(new net.minecraft.nbt.ListTag());
        state.setSealed(false);state.setBroken(false);blade.setDamageValue(1);
        blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);blade.enchant(feather,1);
        player.setItemInHand(InteractionHand.MAIN_HAND,blade);
        var rows=new ArrayList<Map<String,Object>>();report.put("feather_cases",rows);
        player.swinging=true;player.swingTime=2;player.setDeltaMovement(.2,-.7,.3);
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
        boolean hover=player.getDeltaMovement().equals(new Vec3(.2,0,.3));
        rows.add(Map.of("case","swing_hover","actual",player.getDeltaMovement().toString(),"passed",hover));
        player.swinging=false;
        for(int n=1;n<=4;n++) {
            blade.enchant(feather,n);player.startUsingItem(InteractionHand.MAIN_HAND);player.setDeltaMovement(.2,-.8,.3);player.fallDistance=12;
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
            var actual=player.getDeltaMovement();boolean passed=Math.abs(actual.y+.8/n)<1e-9 && actual.x==.2 && actual.z==.3 && player.fallDistance==0;
            rows.add(Map.of("case","held_level_"+n,"vertical",actual.y,"fall_distance",player.fallDistance,"passed",passed));
            player.stopUsingItem();
        }
        blade.enchant(feather,4);player.startUsingItem(InteractionHand.MAIN_HAND);player.setDeltaMovement(0,.4,0);
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
        rows.add(Map.of("case","upward_unchanged","passed",player.getDeltaMovement().y==.4));
        player.stopUsingItem();player.setDeltaMovement(0,-.8,0);
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
        rows.add(Map.of("case","released_falls","passed",player.getDeltaMovement().y==-.8));
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_SWORD));player.setItemInHand(InteractionHand.OFF_HAND,blade);
        player.swinging=true;player.setDeltaMovement(0,-.8,0);NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
        rows.add(Map.of("case","offhand_blade_ignored","passed",player.getDeltaMovement().y==-.8));
        player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        player.startUsingItem(InteractionHand.MAIN_HAND);state.setComboSeq(LegacyCombat.id(LegacyMove.HELM_BRAKER));player.setDeltaMovement(0,-1.5,0);
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
        rows.add(Map.of("case","helm_descent_preserved","passed",player.getDeltaMovement().y==-1.5));
        state.setComboSeq(ComboStateRegistry.NONE.getId());player.swinging=false;
        for(int n=2;n<=4;n++) {
            blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);blade.enchant(feather,n);player.setPos(8,240,8);player.setOnGround(false);player.setDeltaMovement(Vec3.ZERO);
            int ticks=0;
            while(player.getY()>220 && ticks<1000){NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));player.travel(Vec3.ZERO);ticks++;}
            double expected=n==2?13.32:n==3?26.11:38.53;
            rows.add(Map.of("case","vanilla_travel_20m_level_"+n,"ticks",ticks,"seconds",ticks/20.0,"passed",Math.abs(ticks/20.0-expected)<1));
        }
        player.stopUsingItem();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        if(rows.stream().anyMatch(row->!Boolean.TRUE.equals(row.get("passed"))))throw new AssertionError("Feather event/travel contracts failed: "+rows);
    }
}
