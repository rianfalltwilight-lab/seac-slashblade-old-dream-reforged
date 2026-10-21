package org.scex.slashbladelegacy.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;
import org.scex.slashbladelegacy.LegacyCompat;

/** r87 MoveImputHandler: physical sneak OR the separately configurable Lock-on key. */
@EventBusSubscriber(modid=LegacyCompat.MOD_ID,value=Dist.CLIENT)
public final class LegacyKeyMappings {
    public static final KeyMapping TOGGLE_MODE=new KeyMapping("key.slashblade_legacy_compat.toggle_mode",KeyConflictContext.IN_GAME,
            KeyModifier.NONE,InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F8,"key.category.slashblade");
    public static final KeyMapping LOCK_ON=new KeyMapping("key.slashblade_legacy_compat.lock_on",KeyConflictContext.IN_GAME,
            KeyModifier.NONE,InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_LEFT_SHIFT,"key.category.slashblade");
    private LegacyKeyMappings() {}
    private static boolean toggleHeld;
    @SubscribeEvent public static void toggle(net.minecraftforge.event.TickEvent.ClientTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        boolean clicked=false;while(TOGGLE_MODE.consumeClick())clicked=true;
        if(clicked && !toggleHeld && mc.player!=null && mc.screen==null && mc.isWindowActive() && !mc.isPaused())
            org.scex.slashbladelegacy.LegacyNetwork.sendToServer(new org.scex.slashbladelegacy.LegacyModePayload.Request());
        toggleHeld=TOGGLE_MODE.isDown();
    }
    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event){toggleHeld=false;org.scex.slashbladelegacy.LegacyMode.clearClient();}
    @SubscribeEvent
    public static void charge(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();var player=mc.player;
        if(player==null || mc.screen!=null || !mc.isWindowActive() || mc.isPaused()
                || !org.scex.slashbladelegacy.LegacyMode.legacy(Minecraft.getInstance().player)
                || !mods.flammpfeil.slashblade.client.SlashBladeKeyMappings.KEY_SPECIAL_MOVE.isDown()
                || !org.scex.slashbladelegacy.LegacySuperArts.eligible(player.getMainHandItem()))return;
        var random=player.getRandom();
        player.level().addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL,
                player.getX()+(random.nextDouble()-.5)*player.getBbWidth(),player.getY()+random.nextDouble()*player.getBbHeight()-.25,
                player.getZ()+(random.nextDouble()-.5)*player.getBbWidth(),(random.nextDouble()-.5)*2,-random.nextDouble(),(random.nextDouble()-.5)*2);
    }
    @EventBusSubscriber(modid=LegacyCompat.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event){event.register(LOCK_ON);event.register(TOGGLE_MODE);}
    }
}
