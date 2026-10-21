package org.scex.slashbladelegacy.contracts;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Decode the actual 1.20.1 item NBT, including Forge capabilities. */
final class ContractItems {
    private ContractItems() {}
    /** Match the nonempty capability used by registered blade definitions. */
    static ItemStack blade() {
        var stack=new ItemStack(org.scex.slashbladelegacy.BladeApi.SLASHBLADE.get());
        var state=org.scex.slashbladelegacy.BladeStateAccess.of(stack).orElseThrow();
        try{state.getClass().getMethod("setNonEmpty").invoke(state);}
        catch(NoSuchMethodException oldApi){}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot initialize the blade fixture",failure);}
        return stack;
    }
    static void attribute(net.minecraft.world.entity.LivingEntity entity,
            net.minecraft.world.entity.ai.attributes.Attribute type,
            net.minecraft.world.entity.ai.attributes.AttributeModifier modifier) {
        var instance=entity.getAttribute(type);
        instance.removeModifier(modifier.getId());
        instance.addTransientModifier(modifier);
    }
    static Optional<ItemStack> parse(CompoundTag tag) {
        var stack=ItemStack.of(tag);
        return stack.isEmpty()?Optional.empty():Optional.of(stack);
    }
    static void clearSpawnProtection(net.minecraft.server.level.ServerPlayer player) {
        // 1.20.1 mappings: ServerPlayer.spawnInvulnerableTime = f_8921_.
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(
                net.minecraft.server.level.ServerPlayer.class,player,0,"f_8921_");
    }
    /** Keep real teleport handling. Discard only outbound packets. */
    static final class PacketSink extends net.minecraft.server.network.ServerGamePacketListenerImpl {
        PacketSink(net.minecraft.server.MinecraftServer server,net.minecraft.server.level.ServerPlayer player) {
            super(server,new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player);
        }
        @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener) {}
    }
}
