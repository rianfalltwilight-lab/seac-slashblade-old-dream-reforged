package org.scex.slashbladelegacy.client;

import com.mojang.blaze3d.platform.InputConstants;
import mods.flammpfeil.slashblade.event.client.MoveInputEvent;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;
import org.scex.slashbladelegacy.LegacyCompat;

/** r87 MoveImputHandler: physical sneak OR the separately configurable Lock-on key. */
@EventBusSubscriber(modid=LegacyCompat.MOD_ID,value=Dist.CLIENT)
public final class LegacyKeyMappings {
    public static final KeyMapping LOCK_ON=new KeyMapping("key.slashblade_legacy_compat.lock_on",KeyConflictContext.IN_GAME,
            KeyModifier.NONE,InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_LEFT_SHIFT,"key.category.slashblade");
    private LegacyKeyMappings() {}
    @SubscribeEvent
    public static void charge(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();var player=mc.player;
        if(player==null || mc.screen!=null || !mc.isWindowActive() || mc.isPaused()
                || !LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT)
                || !mods.flammpfeil.slashblade.client.SlashBladeKeyMappings.KEY_SPECIAL_MOVE.isDown()
                || !org.scex.slashbladelegacy.LegacySuperArts.eligible(player.getMainHandItem()))return;
        var random=player.getRandom();
        player.level().addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL,
                player.getX()+(random.nextDouble()-.5)*player.getBbWidth(),player.getY()+random.nextDouble()*player.getBbHeight()-.25,
                player.getZ()+(random.nextDouble()-.5)*player.getBbWidth(),(random.nextDouble()-.5)*2,-random.nextDouble(),(random.nextDouble()-.5)*2);
    }
    @SubscribeEvent
    public static void input(MoveInputEvent event) {
        if(LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) && Minecraft.getInstance().screen==null && LOCK_ON.isDown())
            event.getNewCommands().add(InputCommand.SNEAK);
    }
    @EventBusSubscriber(modid=LegacyCompat.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event){event.register(LOCK_ON);}
    }
}
