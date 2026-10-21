package org.scex.slashbladelegacy;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Preserve the typed motion getter used by existing client addons. */
public final class BladeMotionApiPatch {
    public static final String PARENT = "mods/flammpfeil/slashblade/client/renderer/model/AbstractBladeMotionManager";
    private static final String OWNER = "mods/flammpfeil/slashblade/client/renderer/model/BladeMotionManager";
    private static final String MOTION = "jp/nyatla/nymmd/MmdVmdMotionMc";
    private static final String TYPED_GETTER = "(Lnet/minecraft/resources/ResourceLocation;)L" + MOTION + ";";
    private static final String GENERIC_GETTER = "(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/Object;";
    private BladeMotionApiPatch() {}

    public static boolean apply(ClassNode node, ClassNode parent) {
        if (!node.name.equals(OWNER) || !PARENT.equals(node.superName) || !parent.name.equals(PARENT)
                || node.methods.stream().anyMatch(method -> method.name.equals("getMotion")
                && (method.desc.equals(TYPED_GETTER) || method.desc.equals(GENERIC_GETTER)))
                || parent.methods.stream().noneMatch(method -> method.name.equals("getMotion")
                && method.desc.equals(GENERIC_GETTER)
                && (method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == Opcodes.ACC_PUBLIC)) return false;
        var bridge = new MethodNode(Opcodes.ACC_PUBLIC, "getMotion", TYPED_GETTER, null, null);
        bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, PARENT, "getMotion", GENERIC_GETTER, false));
        bridge.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, MOTION));
        bridge.instructions.add(new InsnNode(Opcodes.ARETURN));
        bridge.maxStack = 2;
        bridge.maxLocals = 2;
        node.methods.add(bridge);
        return true;
    }
}
