package org.scex.slashbladelegacy;

import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Check all three timing hooks before changing optional SJAP behavior. */
public final class LegacyAddonCompatibility {
    public static final String GALE_DESCRIPTOR = "(Lnet/minecraft/world/entity/LivingEntity;FZDLmods/flammpfeil/slashblade/capability/slashblade/ISlashBladeState;)V";
    public static final String LIGHTNING_DESCRIPTOR = "(Lnet/minecraft/world/entity/LivingEntity;FZIDLmods/flammpfeil/slashblade/capability/slashblade/ISlashBladeState;)V";
    private static final String ENTITY = "cn/mmf/slashblade_addon/entity/";

    private LegacyAddonCompatibility() {}

    public static boolean supportsTiming(ClassNode blistering, ClassNode gale, ClassNode lightning) {
        var ride = declared(blistering, "()V", false, Set.of("rideTick", "m_6083_"));
        var galeAction = declared(gale, GALE_DESCRIPTOR, true, Set.of());
        var lightningAction = declared(lightning, LIGHTNING_DESCRIPTOR, true, Set.of());
        return calls(ride, ENTITY + "BlisteringSwordsEntity", "getDelay", "()I") == 2
                && calls(galeAction, ENTITY + "GaleSwordsEntity", "setDelay", "(I)V") == 1
                && calls(lightningAction, ENTITY + "BlisteringSwordsEntity", "setDelay", "(I)V") == 1
                && calls(lightningAction, ENTITY + "LightingSwordEntity", "setDelay", "(I)V") == 1;
    }

    private static MethodNode declared(ClassNode node, String descriptor, boolean isStatic, Set<String> names) {
        MethodNode result = null;
        for (var method : node.methods) {
            if (!method.desc.equals(descriptor) || (!names.isEmpty() && !names.contains(method.name))) continue;
            if (result != null || ((method.access & Opcodes.ACC_STATIC) != 0) != isStatic) return null;
            result = method;
        }
        return result;
    }

    private static int calls(MethodNode method, String owner, String name, String descriptor) {
        if (method == null) return 0;
        int count = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor)) count++;
        }
        return count;
    }
}
