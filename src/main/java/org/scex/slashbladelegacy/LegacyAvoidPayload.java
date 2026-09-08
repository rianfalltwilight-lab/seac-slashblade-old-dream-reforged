package org.scex.slashbladelegacy;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** One server-approved impulse per accepted V press; no per-tick movement traffic. */
public record LegacyAvoidPayload(double x,double z) implements CustomPacketPayload {
    public static final Type<LegacyAvoidPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(LegacyCompat.MOD_ID,"avoid_impulse"));
    public static final StreamCodec<RegistryFriendlyByteBuf,LegacyAvoidPayload> CODEC=StreamCodec.of(
            (buf,p)->{buf.writeDouble(p.x);buf.writeDouble(p.z);},buf->new LegacyAvoidPayload(buf.readDouble(),buf.readDouble()));
    @Override public Type<LegacyAvoidPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE,CODEC,(payload,context)->{
            var player=context.player();
            player.setDeltaMovement(player.getDeltaMovement().add(payload.x,0,payload.z));
        });
    }
}
