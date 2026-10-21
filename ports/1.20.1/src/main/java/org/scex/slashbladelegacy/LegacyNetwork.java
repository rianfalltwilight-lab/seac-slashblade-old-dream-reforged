package org.scex.slashbladelegacy;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.scex.slashbladelegacy.client.LegacyPacketHandler;

/** Keep mode changes on the server and movement replies on the client. */
public final class LegacyNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LegacyCompat.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);
    private LegacyNetwork() {}
    public static void register() {
        CHANNEL.messageBuilder(LegacyModePayload.Request.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((packet, buffer) -> {})
                .decoder(buffer -> new LegacyModePayload.Request())
                .consumerMainThread((packet, context) -> {
                    var player = context.get().getSender();
                    if (player != null) LegacyMode.request(player);
                }).add();
        CHANNEL.messageBuilder(LegacyModePayload.State.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((packet, buffer) -> { buffer.writeUUID(packet.player()); buffer.writeBoolean(packet.modern()); })
                .decoder(buffer -> new LegacyModePayload.State(buffer.readUUID(), buffer.readBoolean()))
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> LegacyPacketHandler.handle(packet))).add();
        CHANNEL.messageBuilder(LegacyAvoidPayload.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((packet, buffer) -> { buffer.writeDouble(packet.x()); buffer.writeDouble(packet.z()); })
                .decoder(buffer -> new LegacyAvoidPayload(buffer.readDouble(), buffer.readDouble()))
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> LegacyPacketHandler.handle(packet))).add();
    }
    public static void sendToServer(Object packet) { CHANNEL.sendToServer(packet); }
    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    public static void sendToTrackingEntityAndSelf(Entity entity, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), packet);
    }
}
