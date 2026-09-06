package org.scex.slashbladelegacy;

import org.joml.Matrix4f;
import static org.scex.slashbladelegacy.LegacyMove.*;

/** Direct matrix translation of 1.12.2 LayerSlashBlade, blade/sheath transforms; no invented keyframes. */
public final class LegacyBladePose {
    public static boolean handlesCarry(String carry,LegacyMove move) {
        return carry.equals("DEFAULT") || carry.equals("KATANA") || carry.equals("PSO2") && move!=NONE;
    }
    private static float radians(float degrees){return (float)Math.toRadians(degrees);}
    public static float progress(LegacyMove move,float swing) {
        float value=Math.min(1,Math.max(0,swing)*1.2f);
        return switch(move){case IAI,S_IAI -> 1-Math.abs(value-.5f)*2;case STINGER,HIRA_TUKI -> 1;default -> 1-(1-value)*(1-value);};
    }
    public static Matrix4f matrix(LegacyMove move,float progress,boolean sheath) {
        Matrix4f pose=new Matrix4f().translate(.25f,.4f,-.5f).scale(.075f)
                .rotateX(radians(60)).rotateZ(radians(-20)).rotateY(radians(90));
        if(move!=NONE && (!sheath || move.scabbard)) {
            float value=move.amplitude<0?1-progress:progress;
            if(!sheath && (move==STINGER || move==HIRA_TUKI))pose.translate(0,0,-26);
            if(!sheath && move==KIRIOROSI) {
                pose.rotateX(radians(-20)).rotateZ(radians(30)).translate(0,0,-8)
                        .rotateY(radians(-(90-move.direction))).rotateZ(radians((1-value)*90))
                        .translate((1-value)*10,(1-value)*-5,0).translate(-10,-8,0)
                        .rotateZ(radians(-Math.abs(move.amplitude))).translate(10,8,0).rotateY(radians(180));
            } else if(!sheath && move.direction<0) {
                pose.rotateX(radians(-20)).rotateZ(radians(30)).translate(0,0,-12)
                        .rotateY(radians(-(90+move.direction))).rotateZ(radians((1-value)*240))
                        .translate(-10,-8,0).rotateZ(radians(-value*Math.abs(move.amplitude))).translate(10,8,0);
            } else {
                pose.rotateX(radians(-value*20)).rotateZ(radians(value*30))
                        .rotateY(radians(-value*(90-move.direction))).translate(-10,-8,0)
                        .rotateZ(radians(-value*Math.abs(move.amplitude))).translate(10,8,0);
            }
        }
        return pose.scale(.095f).rotateZ(radians(-90));
    }
}
