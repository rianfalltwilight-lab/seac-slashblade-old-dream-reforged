package org.scex.slashbladelegacy;

/** Names, angles and reset windows from 1.12.2 ba1ef860 ItemSlashBlade.ComboSequence. */
public enum LegacyMove {
    NONE(true,0,0,0), SAYA1(true,200,5,20), SAYA2(true,-200,5,20),
    BATTOU(false,240,0,12), NOUTOU(false,-210,10,5), KIRIAGE(false,260,70,20),
    KIRIOROSI(false,-260,90,12), IAI(false,240,0,20), HIRA_TUKI(false,180,180,20),
    SLASH_EDGE(false,240,20,12), RETURN_EDGE(false,250,-160,12), S_IAI(false,240,0,12),
    S_SLASH_EDGE(false,240,20,25), S_RETURN_EDGE(false,250,-160,25), S_SLASH_BLADE(false,200,-315,25),
    A_SLASH_EDGE(false,240,20,25), A_KIRIOROSI(false,200,-240,25),
    A_KIRIAGE(false,240,-70,12), A_KIRIOROSI_FINISH(false,200,-270,25),
    RAPID_SLASH(false,600,-380,12), RAPID_SLASH_END(false,240,20,12), RISING_STAR(false,250,-160,12),
    HELM_BRAKER(false,200,-270,25), CALIBUR(false,600,-380,25),
    FORCE1(false,300,-230,25), FORCE2(false,250,-30,25), FORCE3(true,200,5,20),
    FORCE4(true,-200,5,20), FORCE5(false,240,0,12), FORCE6(false,200,-270,25),
    STINGER(false,180,180,20);

    public final boolean scabbard;
    public final float amplitude,direction;
    public final int resetTicks;
    LegacyMove(boolean scabbard,float amplitude,float direction,int resetTicks) {
        this.scabbard=scabbard;this.amplitude=amplitude;this.direction=direction;this.resetTicks=resetTicks;
    }
    public boolean aerial() {return name().startsWith("A_") || this==HELM_BRAKER || this==CALIBUR;}
}
