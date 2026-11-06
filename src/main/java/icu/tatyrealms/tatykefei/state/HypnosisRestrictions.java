package icu.tatyrealms.tatykefei.state;

public final class HypnosisRestrictions {
    public static final int MOVEMENT = 1;
    public static final int INTERACTION = 2;
    public static final int LOOK = 4;
    public static final int SPEECH = 8;
    public static final int ALL = MOVEMENT | INTERACTION | LOOK | SPEECH;

    private HypnosisRestrictions() {
    }
}
