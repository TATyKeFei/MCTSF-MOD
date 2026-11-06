package icu.tatyrealms.tatykefei.state;

import java.util.UUID;

public final class PlayerState {
    public Form form = Form.HUMAN;
    public boolean normalRespawn;
    public Integer slimeColor;
    public UUID body;
    public UUID homeBody;
    public UUID observedBody;
    public UUID observerFallback;
    public UUID zipperReturnBody;
    public Form zipperReturnForm;
    public UUID expulsionBody;
    public long expulsionAt;
    public String savedGameMode;
    public UUID hypnotist;
    public long hypnosisUntil;
    public String anchorDimension;
    public double anchorX;
    public double anchorY;
    public double anchorZ;
    public boolean savedMayFly;
    public boolean savedFlying;
    public boolean savedInvulnerable;
    public boolean savedNoGravity;
}
