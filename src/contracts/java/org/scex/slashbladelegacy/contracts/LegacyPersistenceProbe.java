package org.scex.slashbladelegacy.contracts;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Separate test mod: no references to dev11 Java symbols, so the same probe can run with dev10. */
@Mod("slashblade_legacy_persistence_probe")
public final class LegacyPersistenceProbe {
    private static final BlockPos POS=new BlockPos(7,200,7);
    private static final ResourceLocation LANDING=ResourceLocation.parse("slashblade_legacy_compat:helm_landing");
    private static final String MARKER="scex_landing_persistence_fixture";
    public LegacyPersistenceProbe(){NeoForge.EVENT_BUS.addListener(this::started);}

    private void started(ServerStartedEvent event){
        var server=event.getServer();
        var report=new LinkedHashMap<String,Object>();
        var mode=System.getProperty("scex.legacy.persistence","");
        var token=System.getProperty("scex.legacy.persistence.fixture","");
        var output=Path.of(System.getProperty("scex.legacy.persistence.report","landing-persistence-"+mode+".json"));
        report.put("mode",mode);report.put("fixture",token);report.put("chest",POS.toShortString());
        try {
            require(mode.equals("write") || mode.equals("read-dev11") || mode.equals("read-dev10"),"Explicit persistence mode required");
            require(token.matches("[a-zA-Z0-9_-]{8,80}"),"Explicit fixture token required");
            require(!ModList.get().isLoaded("slashblade_legacy_contracts"),"Do not install the general contracts mod with this probe");
            var version=ModList.get().getModContainerById("slashblade_legacy_compat").orElseThrow().getModInfo().getVersion().toString();
            report.put("compat_version",version);
            require(version.equals(mode.equals("read-dev10")?"0.1.0-dev.10":"0.1.0-dev.11"),"Unexpected tested compat version");
            var level=server.overworld();level.getChunkAt(POS);
            var registered=ComboStateRegistry.REGISTRY.containsKey(LANDING);
            report.put("landing_registered",registered);
            require(registered!=mode.equals("read-dev10"),"Unexpected landing registry presence");
            ChestBlockEntity chest;
            ItemStack blade;
            if(mode.equals("write")){
                require(level.isEmptyBlock(POS),"Fixture position is occupied; use a fresh isolated world");
                level.setBlockAndUpdate(POS,Blocks.CHEST.defaultBlockState());
                chest=(ChestBlockEntity)level.getBlockEntity(POS);
                require(chest!=null,"Fixture chest missing");
                var definition=server.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements()
                        .filter(h->h.key().location().toString().equals("slashblade:sange")).findFirst().orElseThrow();
                blade=definition.value().getBlade(server.registryAccess());
                var state=BladeStateAccess.of(blade).orElseThrow();
                state.setComboRoot(ComboStateRegistry.STANDBY.getId());state.setComboSeq(LANDING);
                state.setLastActionTime(level.getGameTime());state.setDamage(7);state.setBroken(false);
                state.setProudSoulCount(12345);state.setKillCount(17);state.setRefine(4);
                var custom=blade.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
                custom.putString(MARKER,token);blade.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));
                var saved=blade.save(server.registryAccess());
                var parsed=ItemStack.parse(server.registryAccess(),saved).orElseThrow();
                require(BladeStateAccess.of(parsed).orElseThrow().getComboSeq().equals(LANDING),"Landing lost in item codec");
                report.put("item_codec_roundtrip",true);report.put("item_before_shutdown_snbt",saved.toString());
                chest.setItem(0,blade);chest.setChanged();level.getChunkAt(POS).setUnsaved(true);
                report.put("fixture_written",true);
                report.put("cross_restart_verified",false);
            }else{
                require(level.getBlockEntity(POS) instanceof ChestBlockEntity,"Saved fixture chest missing");
                chest=(ChestBlockEntity)level.getBlockEntity(POS);blade=chest.getItem(0);
                require(!blade.isEmpty() && blade.getCount()==1,"Saved blade missing or count changed");
                require(token.equals(blade.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString(MARKER)),"Wrong fixture token");
                var state=BladeStateAccess.of(blade).orElseThrow();
                report.put("raw_loaded_combo",state.getComboSeq().toString());
                report.put("raw_loaded_last_action_time",state.getLastActionTime());
                report.put("item_loaded_snbt",blade.save(server.registryAccess()).toString());
                require(state.getComboSeq().equals(LANDING),"Landing changed before any probe interaction");
                require(state.getDamage()==7 && state.getProudSoulCount()==12345 && state.getKillCount()==17 && state.getRefine()==4,"Persistent blade fields changed");
                report.put("chest_reload_and_core_fields",true);
                var player=FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)),"LandingContract"));
                player.setPos(7,201,7);player.setOnGround(true);player.setShiftKeyDown(false);player.experienceLevel=0;
                // Exercise a copy of the actually reloaded item, retaining the chest fixture for a second independent rollback read.
                var held=blade.copy();player.setItemInHand(InteractionHand.MAIN_HAND,held);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                var heldState=BladeStateAccess.of(held).orElseThrow();
                var resolved=heldState.peekCurrentComboStateTicks(player).getValue();
                report.put("resolved_loaded_combo",resolved.toString());
                if(mode.equals("read-dev10"))require(resolved.equals(ComboStateRegistry.NONE.getId()),"Missing combo did not resolve to NONE");
                require(ItemStack.parse(server.registryAccess(),held.save(server.registryAccess())).isPresent(),"Loaded blade cannot be saved again");
                // Synthetic elapsed time, explicitly separate from a real-time gameplay claim.
                server.getWorldData().overworldData().setGameTime(level.getGameTime()+21);
                player.getData(CapabilityInputState.INPUT_STATE).getCommands().clear();
                held.getItem().use(level,player,InteractionHand.MAIN_HAND);
                var after=BladeStateAccess.of(player.getMainHandItem()).orElseThrow().getComboSeq();
                report.put("right_click_after_reload",after.toString());
                require(after.equals(ResourceLocation.parse("slashblade_legacy_compat:saya1")),"Reloaded blade cannot start normal right combo");
                player.stopUsingItem();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                report.put("right_click_clock_advance_ticks",21);report.put("cross_restart_verified",true);
                report.put("scope","Real chunk/chest reload across launches; FakePlayer item.use after synthetic 21 tick elapsed time. No player login, packets, client render, or arbitrary-addon serialization claim.");
            }
            report.put("success",true);
        }catch(Throwable failure){report.put("success",false);report.put("error",failure.toString());failure.printStackTrace();}
        finally{
            try{var parent=output.toAbsolutePath().getParent();Files.createDirectories(parent);Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report));}
            catch(Exception failure){throw new RuntimeException(failure);}
            finally{server.halt(false);}
        }
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
