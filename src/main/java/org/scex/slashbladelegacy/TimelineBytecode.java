package org.scex.slashbladelegacy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.objectweb.asm.tree.*;

/** Ignore debug metadata, but include operands and branch destinations when matching upstream code. */
public final class TimelineBytecode {
    private TimelineBytecode() {}

    public static String fingerprint(MethodNode method) {
        return fingerprint(method, method.instructions.getFirst());
    }

    /** Match only the loop we replace. Keep upstream checks before getElapsed intact. */
    public static String timelineLoopFingerprint(MethodNode method) {
        AbstractInsnNode start = null;
        for (var instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode call) || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESTATIC
                    || !call.owner.equals("mods/flammpfeil/slashblade/registry/combo/ComboState")
                    || !call.name.equals("getElapsed")
                    || !call.desc.equals("(Lnet/minecraft/world/entity/LivingEntity;)J")) continue;
            if (start != null) return "unsupported";
            start = call.getPrevious();
            while (start != null && start.getOpcode() < 0) start = start.getPrevious();
            if (!(start instanceof VarInsnNode load) || load.getOpcode() != org.objectweb.asm.Opcodes.ALOAD || load.var != 1)
                return "unsupported";
        }
        return start == null ? "unsupported" : fingerprint(method, start);
    }

    private static String fingerprint(MethodNode method, AbstractInsnNode start) {
        if (!method.tryCatchBlocks.isEmpty()) return "unsupported";
        StringBuilder text = new StringBuilder(method.desc).append('\n');
        for (var instruction = start; instruction != null; instruction = instruction.getNext()) {
            if (instruction.getOpcode() < 0) continue;
            text.append(instruction.getOpcode());
            if (instruction instanceof VarInsnNode n) text.append(':').append(n.var);
            else if (instruction instanceof TypeInsnNode n) text.append(':').append(n.desc);
            else if (instruction instanceof IincInsnNode n) text.append(':').append(n.var).append(':').append(n.incr);
            else if (instruction instanceof FieldInsnNode n) text.append(':').append(n.owner).append(':').append(n.name).append(':').append(n.desc);
            else if (instruction instanceof MethodInsnNode n) text.append(':').append(n.owner).append(':').append(n.name).append(':').append(n.desc).append(':').append(n.itf);
            else if (instruction instanceof LdcInsnNode n) text.append(':').append(n.cst.getClass().getName()).append(':').append(n.cst);
            else if (instruction instanceof JumpInsnNode n) {
                int destination = 0;
                for (var preceding = start; preceding != n.label; preceding = preceding.getNext()) {
                    if (preceding == null) return "unsupported";
                    if (preceding.getOpcode() >= 0) destination++;
                }
                text.append(':').append(destination);
            } else if (!(instruction instanceof InsnNode)) return "unsupported";
            text.append('\n');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
