package org.scex.slashbladelegacy.client;

import net.minecraft.client.Minecraft;
import org.scex.slashbladelegacy.LegacyAvoidPayload;
import org.scex.slashbladelegacy.LegacyMode;
import org.scex.slashbladelegacy.LegacyModePayload;

/** Apply packets on the client thread after the network handler schedules them. */
public final class LegacyPacketHandler {
    private LegacyPacketHandler() {}

    public static void handle(LegacyAvoidPayload packet) {
        var player=Minecraft.getInstance().player;
        if(player!=null)player.addDeltaMovement(new net.minecraft.world.phys.Vec3(packet.x(),0,packet.z()));
    }

    public static void handle(LegacyModePayload.State packet) {
        var player=Minecraft.getInstance().player;
        if(player!=null)LegacyMode.receive(player,packet.player(),packet.modern());
    }
}
