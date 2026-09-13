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
        if (!method.tryCatchBlocks.isEmpty()) return "unsupported";
        StringBuilder text = new StringBuilder(method.desc).append('\n');
        for (var instruction : method.instructions) {
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
                for (var preceding = method.instructions.getFirst(); preceding != n.label; preceding = preceding.getNext())
                    if (preceding.getOpcode() >= 0) destination++;
                text.append(':').append(destination);
            } else if (!(instruction instanceof InsnNode)) return "unsupported";
            text.append('\n');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
