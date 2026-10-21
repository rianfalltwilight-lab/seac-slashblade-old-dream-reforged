package org.scex.slashbladelegacy;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Let existing addons call the old record constructor with the new fields unset. */
public final class BladeStateConstructorPatch {
    private static final String OWNER = "mods/flammpfeil/slashblade/capability/slashblade/BladeStateData";
    private static final String PREFIX = "(Ljava/lang/String;FIIIZZLnet/minecraft/resources/ResourceLocation;Z";
    private static final String SUFFIX = "Lnet/minecraft/resources/ResourceLocation;Lmods/flammpfeil/slashblade/client/renderer/CarryType;IZLnet/minecraft/world/phys/Vec3;Ljava/util/Optional;Ljava/util/Optional;Ljava/util/List;)V";
    private static final String OLD_CONSTRUCTOR = PREFIX + SUFFIX;
    private static final String NEW_CONSTRUCTOR = PREFIX + "Ljava/util/Optional;Z" + SUFFIX;
    private static final String[] COMPONENTS = {"translationKey", "baseAttackModifier", "proudSoul", "killCount",
            "refine", "broken", "sealed", "slashArtsKey", "defaultBewitched", "defaultSwordTypes", "destructable",
            "comboRoot", "carryType", "effectColor", "effectColorInverse", "adjust", "texture", "model", "specialEffects"};
    private BladeStateConstructorPatch() {}

    public static boolean apply(ClassNode node) {
        if (!node.name.equals(OWNER) || node.methods.stream().anyMatch(method -> method.name.equals("<init>")
                && method.desc.equals(OLD_CONSTRUCTOR))) return false;
        if ((node.access & Opcodes.ACC_RECORD) == 0 || !node.superName.equals("java/lang/Record")
                || node.recordComponents == null || node.recordComponents.size() != COMPONENTS.length
                || node.methods.stream().noneMatch(method -> method.name.equals("<init>")
                && method.desc.equals(NEW_CONSTRUCTOR) && (method.access & Opcodes.ACC_PUBLIC) != 0)) return false;
        var newTypes = Type.getArgumentTypes(NEW_CONSTRUCTOR);
        for (int i = 0; i < COMPONENTS.length; i++) {
            var component = node.recordComponents.get(i);
            if (!component.name.equals(COMPONENTS[i]) || !component.descriptor.equals(newTypes[i].getDescriptor())) return false;
        }
        var bridge = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", OLD_CONSTRUCTOR, null, null);
        bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        var oldTypes = Type.getArgumentTypes(OLD_CONSTRUCTOR);
        int slot = 1;
        for (int i = 0; i < oldTypes.length; i++) {
            bridge.instructions.add(new VarInsnNode(oldTypes[i].getOpcode(Opcodes.ILOAD), slot));
            slot += oldTypes[i].getSize();
            if (i == 8) {
                // Match the new record's defaults after defaultBewitched.
                bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Optional", "empty", "()Ljava/util/Optional;", false));
                bridge.instructions.add(new InsnNode(Opcodes.ICONST_0));
            }
        }
        bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, OWNER, "<init>", NEW_CONSTRUCTOR, false));
        bridge.instructions.add(new InsnNode(Opcodes.RETURN));
        bridge.maxStack = 20;
        bridge.maxLocals = slot;
        node.methods.add(bridge);
        return true;
    }
}
