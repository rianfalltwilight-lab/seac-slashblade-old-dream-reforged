package org.scex.slashbladelegacy;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class LegacyModePayload {
    private LegacyModePayload(){}
    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(LegacyCompat.MOD_ID,"mode_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->{},b->new Request());
        @Override public Type<Request> type(){return TYPE;}
    }
    public record State(UUID player,boolean modern) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(LegacyCompat.MOD_ID,"mode_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.player);b.writeBoolean(p.modern);},b->new State(b.readUUID(),b.readBoolean()));
        @Override public Type<State> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("1");
        registrar.playToServer(Request.TYPE,Request.CODEC,(p,c)->{if(c.player() instanceof ServerPlayer player)LegacyMode.request(player);});
        registrar.playToClient(State.TYPE,State.CODEC,(p,c)->LegacyMode.receive(c.player(),p.player,p.modern));
    }
}
