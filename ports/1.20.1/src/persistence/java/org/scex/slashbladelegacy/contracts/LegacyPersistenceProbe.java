package org.scex.slashbladelegacy.contracts;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.UUID;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.SpecialEffectsRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.scex.slashbladelegacy.BladeStateAccess;
import org.scex.slashbladelegacy.LegacyArts;
import org.scex.slashbladelegacy.LegacyRangeAttack;
import org.scex.slashbladelegacy.SummonedBladeMode;

/** Save a real chest, then verify it in a separate server process. */
@Mod("slashblade_legacy_persistence_probe")
public final class LegacyPersistenceProbe {
    private static final BlockPos POS=new BlockPos(7,200,7);
    private static final ResourceLocation LANDING=new ResourceLocation("slashblade_legacy_compat:helm_landing");
    private static final String MARKER="scex_landing_persistence_fixture";
    private static final int RGB=0x3333FF;
    public LegacyPersistenceProbe(){MinecraftForge.EVENT_BUS.addListener(this::started);}

    private void started(ServerStartedEvent event){
        var server=event.getServer();
        var report=new LinkedHashMap<String,Object>();
        var mode=System.getProperty("scex.legacy.persistence","");
        var token=System.getProperty("scex.legacy.persistence.fixture","");
        var output=Path.of(System.getProperty("scex.legacy.persistence.report","persistence-"+mode+".json"));
        long pid=ProcessHandle.current().pid(),start=ManagementFactory.getRuntimeMXBean().getStartTime();
        report.put("mode",mode);report.put("fixture",token);report.put("chest",POS.toShortString());
        report.put("process_id",pid);report.put("process_start_epoch_ms",start);
        try {
            require(mode.equals("write") || mode.equals("read"),"Use persistence mode write or read");
            require(token.matches("[a-zA-Z0-9_-]{8,80}"),"Explicit fixture token required");
            require(Files.readString(Path.of("persistence-1201.flag")).trim().equals(token),"Disposable-world marker does not match fixture");
            require(!ModList.get().isLoaded("slashblade_legacy_contracts"),"Do not install the general contracts mod with this probe");
            var version=ModList.get().getModContainerById("slashblade_legacy_compat").orElseThrow().getModInfo().getVersion().toString();
            report.put("compat_version",version);
            require(version.equals("0.3.0-dev.9"),"Unexpected tested compat version");
            var level=server.overworld();level.getChunkAt(POS);
            require(ComboStateRegistry.REGISTRY.get().containsKey(LANDING),"Landing combo is not registered");
            report.put("landing_registered",true);
            if(mode.equals("write")){
                require(level.isEmptyBlock(POS),"Fixture position is occupied; use a fresh isolated world");
                level.setBlockAndUpdate(POS,Blocks.CHEST.defaultBlockState());
                var chest=(ChestBlockEntity)level.getBlockEntity(POS);
                require(chest!=null,"Fixture chest missing");
                var definition=server.registryAccess().registryOrThrow(SlashBladeDefinition.REGISTRY_KEY).holders()
                        .filter(h->h.key().location().toString().equals("slashblade:sange")).findFirst().orElseThrow();
                var blade=definition.value().getBlade();
                var state=BladeStateAccess.of(blade).orElseThrow();
                state.setComboRoot(ComboStateRegistry.STANDBY.getId());state.setComboSeq(LANDING);
                state.setLastActionTime(level.getGameTime());state.setDamage(7);state.setBroken(false);state.setSealed(false);
                state.setProudSoulCount(12345);state.setKillCount(17);state.setRefine(4);
                state.setColorCode(RGB);state.setEffectColorInverse(true);
                state.setSlashArtsKey(LegacyArts.key(LegacyArts.Art.DIMENSION_SPIRAL));
                state.addSpecialEffect(SpecialEffectsRegistry.WITHER_EDGE.getId());
                blade.enchant(Enchantments.POWER_ARROWS,2);
                blade.getOrCreateTag().putString(MARKER,token);
                blade.getOrCreateTag().putBoolean(SummonedBladeMode.MODE,true);
                // Use the production source-ID entry. Do not synthesize its NBT value.
                UUID source=LegacyRangeAttack.sourceId(blade);
                var saved=blade.save(new CompoundTag());
                var parsed=ItemStack.of(saved);
                checkState(parsed,BladeStateAccess.of(parsed).orElseThrow());
                require(parsed.save(new CompoundTag()).equals(saved),"Item NBT roundtrip changed");
                require(source.equals(parsed.getOrCreateTag().getUUID(SummonedBladeMode.SOURCE)),"Source UUID changed in item NBT roundtrip");
                report.put("source_uuid",source.toString());report.put("last_action_time",state.getLastActionTime());
                report.put("item_nbt_roundtrip",true);report.put("item_before_shutdown_snbt",saved.toString());
                chest.setItem(0,blade);chest.setChanged();level.getChunkAt(POS).setUnsaved(true);
                report.put("fixture_written",true);report.put("cross_restart_verified",false);
                report.put("scope","Fixture placed in a real chest. Normal server shutdown must finish before the read launch.");
            }else{
                var written=Path.of(System.getProperty("scex.legacy.persistence.written","persistence-write.json"));
                var before=JsonParser.parseString(Files.readString(written)).getAsJsonObject();
                require(before.get("success").getAsBoolean() && before.get("mode").getAsString().equals("write")
                        && before.get("fixture").getAsString().equals(token),"Write receipt does not match this fixture");
                require(before.get("process_id").getAsLong()!=pid || before.get("process_start_epoch_ms").getAsLong()!=start,
                        "Read must run in a separate server process");
                require(level.getBlockEntity(POS) instanceof ChestBlockEntity,"Saved fixture chest missing");
                var chest=(ChestBlockEntity)level.getBlockEntity(POS);var blade=chest.getItem(0);
                require(!blade.isEmpty() && blade.getCount()==1,"Saved blade missing or count changed");
                var tag=blade.getTag();require(tag!=null && token.equals(tag.getString(MARKER)),"Wrong fixture token");
                require(tag.hasUUID(SummonedBladeMode.SOURCE) && tag.getUUID(SummonedBladeMode.SOURCE).toString().equals(before.get("source_uuid").getAsString()),
                        "Source UUID changed across restart");
                var state=BladeStateAccess.of(blade).orElseThrow();checkState(blade,state);
                require(state.getLastActionTime()==before.get("last_action_time").getAsLong(),"Action time changed before interaction");
                var loaded=blade.save(new CompoundTag());
                require(loaded.equals(TagParser.parseTag(before.get("item_before_shutdown_snbt").getAsString())),"Chest item NBT changed across restart");
                report.put("raw_loaded_combo",state.getComboSeq().toString());report.put("raw_loaded_last_action_time",state.getLastActionTime());
                report.put("item_loaded_snbt",loaded.toString());report.put("source_uuid",tag.getUUID(SummonedBladeMode.SOURCE).toString());
                report.put("chest_reload_exact_nbt",true);report.put("blade_fields_and_color_sa_se",true);
                var player=FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes(token.getBytes(StandardCharsets.UTF_8)),"PersistContract"));
                player.setPos(7,201,7);player.setOnGround(true);player.setShiftKeyDown(false);player.experienceLevel=0;
                // Test a copy. Keep the loaded chest unchanged for inspection.
                var held=blade.copy();player.setItemInHand(InteractionHand.MAIN_HAND,held);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                var heldState=BladeStateAccess.of(held).orElseThrow();
                report.put("resolved_loaded_combo",BladeStateAccess.peek(heldState,player).getValue().toString());
                require(ItemStack.of(held.save(new CompoundTag())).save(new CompoundTag()).equals(loaded),"Loaded blade cannot be saved again");
                // Advance only this disposable world's clock. This is not an input-latency test.
                server.getWorldData().overworldData().setGameTime(level.getGameTime()+21);
                player.getCapability(CapabilityInputState.INPUT_STATE).orElseThrow(IllegalStateException::new).getCommands().clear();
                held.getItem().use(level,player,InteractionHand.MAIN_HAND);
                var after=BladeStateAccess.of(player.getMainHandItem()).orElseThrow().getComboSeq();
                report.put("right_click_after_reload",after.toString());
                require(after.equals(new ResourceLocation("slashblade_legacy_compat:saya1")),"Reloaded blade cannot start normal right combo");
                player.stopUsingItem();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                report.put("write_receipt",written.toAbsolutePath().toString());report.put("separate_process",true);
                report.put("right_click_clock_advance_ticks",21);report.put("cross_restart_verified",true);
                report.put("scope","Real chunk/chest reload across two launches; complete ItemStack NBT comparison and FakePlayer item.use after a synthetic 21-tick clock advance. No player login, client packets, render or arbitrary-addon serialization claim.");
            }
            report.put("success",true);
        }catch(Throwable failure){report.put("success",false);report.put("error",failure.toString());failure.printStackTrace();}
        finally{
            try{Files.createDirectories(output.toAbsolutePath().getParent());Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report));}
            catch(Exception failure){throw new RuntimeException(failure);}
            finally{server.halt(false);}
        }
    }
    private static void checkState(ItemStack blade,ISlashBladeState state){
        require(state.getComboSeq().equals(LANDING) && state.getComboRoot().equals(ComboStateRegistry.STANDBY.getId()),"Saved combo fields changed");
        require(state.getDamage()==7 && !state.isBroken() && !state.isSealed() && state.getProudSoulCount()==12345
                && state.getKillCount()==17 && state.getRefine()==4,"Persistent blade fields changed");
        require(state.getColorCode()==(0xFF000000|RGB) && state.isEffectColorInverse(),"ARGB color or inverse flag changed");
        require(state.getSlashArtsKey().equals(LegacyArts.key(LegacyArts.Art.DIMENSION_SPIRAL))
                && state.hasSpecialEffect(SpecialEffectsRegistry.WITHER_EDGE.getId()),"Saved SA or SE changed");
        require(blade.hasTag() && blade.getTag().getBoolean(SummonedBladeMode.MODE)
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS,blade)==2,"SB mode or Power enchantment changed");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
