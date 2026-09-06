package org.scex.slashbladelegacy.contracts;

import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import org.scex.slashbladelegacy.*;

/** Bounded observation probes, not fixes or a load benchmark. */
final class Dev9Audit {
    static void run(ServerPlayer player,Map<String,Object> report) {
        var saved=player.getMainHandItem();var pos=player.position();var blade=saved.copy();
        var level=player.serverLevel();var state=BladeStateAccess.of(blade).orElseThrow();
        state.setComboRoot(ComboStateRegistry.STANDBY.getId());state.setSpecialEffects(new net.minecraft.nbt.ListTag());
        state.setComboSeq(ComboStateRegistry.NONE.getId());
        player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.setPos(8,180,8);player.setYRot(0);player.setXRot(0);player.setOnGround(true);
        player.getData(CapabilityInputState.INPUT_STATE).getCommands().clear();
        var target=EntityType.ZOMBIE.create(level);target.setPos(8,180,10);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);level.addFreshEntity(target);
        var bounds=player.getBoundingBox().inflate(20);int[] attackEvents={0};
        Consumer<AttackEntityEvent> protect=e->{if(e.getEntity()==player && e.getTarget()==target){attackEvents[0]++;e.setCanceled(true);}};
        try {
            NeoForge.EVENT_BUS.addListener(protect);
            float before=target.getHealth();
            state.updateComboSeq(player,LegacyCombat.id(LegacyMove.SAYA1));
            float scabbardDamage=before-target.getHealth();int scabbardEvents=attackEvents[0];
            before=target.getHealth();
            state.updateComboSeq(player,LegacyCombat.id(LegacyMove.BATTOU));
            report.put("audit_protection_event",Map.of("scabbard_damage",scabbardDamage,"scabbard_attack_event_count",scabbardEvents,
                    "blade_damage",before-target.getHealth(),"blade_attack_event_count",attackEvents[0]-scabbardEvents));
            require(scabbardDamage==0 && before==target.getHealth() && scabbardEvents==1 && attackEvents[0]==2,"Attack cancellation bypassed");
            NeoForge.EVENT_BUS.unregister(protect);target.discard();
            int[] slashes={0};Consumer<SlashBladeEvent.DoSlashEvent> count=e->{if(e.getUser()==player)slashes[0]++;};
            NeoForge.EVENT_BUS.addListener(count);
            long now=level.getGameTime();
            InputClock.next(player);now=level.getGameTime();
            try {
                state.setComboSeq(ComboStateRegistry.NONE.getId());
                for(int i=0;i<12;i++)use(player,blade);
            } finally {NeoForge.EVENT_BUS.unregister(count);}
            report.put("audit_same_tick_use",Map.of("input_calls",12,"entry",net.neoforged.fml.ModList.get().isLoaded("worldedit")?"Item.use (WorldEdit startup cache prevents game-mode fixture)":"ServerPlayerGameMode.useItem","slash_events",slashes[0],"elapsed_game_ticks",level.getGameTime()-now,
                    "raw_network_transport_test",false));
            require(slashes[0]==1,"Repeated same-tick input generated extra slashes");
            NeoForge.EVENT_BUS.addListener(count);
            try {
                InputClock.next(player);
                use(player,blade);
                require(slashes[0]==2,"Next-tick attack was incorrectly blocked");
            } finally {NeoForge.EVENT_BUS.unregister(count);}
            var projectile=new LegacySummonedBlade(SummonedBladeMode.BLADE.get(),level);
            projectile.initialize(player,1,0xffffff,LegacyDrive.identity(blade),null);
            var tag=new net.minecraft.nbt.CompoundTag();projectile.addAdditionalSaveData(tag);
            tag.putInt("LegacyFlightAge",Integer.MAX_VALUE);projectile.readAdditionalSaveData(tag);level.addFreshEntity(projectile);
            try {
                projectile.tick();var after=new net.minecraft.nbt.CompoundTag();projectile.addAdditionalSaveData(after);
                report.put("audit_age_overflow",Map.of("input",Integer.MAX_VALUE,"age_after_tick",after.getInt("LegacyFlightAge"),"removed",projectile.isRemoved()));
                require(after.getInt("LegacyFlightAge")==101 && projectile.isRemoved(),"Malformed projectile age escaped lifetime bound");
            } finally {projectile.discard();}
        } finally {
            NeoForge.EVENT_BUS.unregister(protect);target.discard();
            for(var arc:level.getEntitiesOfClass(EntitySlashEffect.class,bounds))arc.discard();
            player.stopUsingItem();player.setItemInHand(InteractionHand.MAIN_HAND,saved);player.setPos(pos);
        }
    }
    private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
    private static void use(ServerPlayer player,net.minecraft.world.item.ItemStack blade) {
        // At ServerStartedEvent WorldEdit's online-id cache is still an immutable empty set.
        // Keep this suite at the item entry; the minimal suite covers the game-mode entry.
        if(net.neoforged.fml.ModList.get().isLoaded("worldedit"))blade.getItem().use(player.level(),player,InteractionHand.MAIN_HAND);
        else player.gameMode.useItem(player,player.serverLevel(),blade,InteractionHand.MAIN_HAND);
    }
}
