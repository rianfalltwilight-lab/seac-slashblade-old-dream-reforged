package org.scex.slashbladelegacy;

import java.util.function.Consumer;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;

/** Keep client pose types out of CarryType's server initialization. */
public final class ServerCarryTypePatch {
    public static final Consumer<Object> NO_RENDER = ignored -> {};
    private static final String OWNER = "mods/flammpfeil/slashblade/client/renderer/CarryType";
    private static final String POSE_ACTION = "(Lcom/mojang/blaze3d/vertex/PoseStack;)V";
    private static final String OBJECT_ACTION = "(Ljava/lang/Object;)V";
    private ServerCarryTypePatch() {}

    public static int apply(ClassNode node) {
        if (!node.name.equals(OWNER)) return 0;
        int replaced = 0;
        for (var method : node.methods) {
            if (!method.name.equals("<init>") && !method.name.equals("<clinit>")) continue;
            for (var instruction = method.instructions.getFirst(); instruction != null;) {
                var next = instruction.getNext();
                if (instruction instanceof InvokeDynamicInsnNode factory && isPoseFactory(node, factory)) {
                    method.instructions.set(factory, new FieldInsnNode(Opcodes.GETSTATIC,
                            "org/scex/slashbladelegacy/ServerCarryTypePatch", "NO_RENDER", "Ljava/util/function/Consumer;"));
                    replaced++;
                }
                instruction = next;
            }
        }
        return replaced;
    }

    private static boolean isPoseFactory(ClassNode node, InvokeDynamicInsnNode factory) {
        if (!factory.name.equals("accept") || !factory.desc.equals("()Ljava/util/function/Consumer;")
                || factory.bsm.getTag() != Opcodes.H_INVOKESTATIC
                || !factory.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                || !factory.bsm.getName().equals("metafactory")
                || factory.bsm.isInterface()
                || !factory.bsm.getDesc().equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")
                || factory.bsmArgs.length != 3
                || !Type.getMethodType(OBJECT_ACTION).equals(factory.bsmArgs[0])
                || !Type.getMethodType(POSE_ACTION).equals(factory.bsmArgs[2])
                || !(factory.bsmArgs[1] instanceof Handle implementation)
                || implementation.getTag() != Opcodes.H_INVOKESTATIC || implementation.isInterface()
                || !implementation.getOwner().equals(OWNER) || !implementation.getDesc().equals(POSE_ACTION)) return false;
        int flags = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;
        return node.methods.stream().anyMatch(method -> method.name.equals(implementation.getName())
                && method.name.startsWith("lambda$") && method.desc.equals(POSE_ACTION) && (method.access & flags) == flags);
    }
}
