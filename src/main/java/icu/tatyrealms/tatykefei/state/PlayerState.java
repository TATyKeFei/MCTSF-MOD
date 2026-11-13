package icu.tatyrealms.tatykefei.state;

import java.util.UUID;

public final class PlayerState {
    public String storageServer;
    public Form form = Form.HUMAN;
    public boolean normalRespawn;
    public Integer slimeColor;
    public UUID slimeBottleToken;
    public String slimeBottleDimension;
    public double slimeBottleX;
    public double slimeBottleY;
    public double slimeBottleZ;
    public UUID body;
    public UUID homeBody;
    public UUID observedBody;
    public UUID observerFallback;
    public UUID spiritHost;
    public boolean observerChatPrivate;
    public UUID zipperReturnBody;
    public Form zipperReturnForm;
    public UUID expulsionBody;
    public long expulsionAt;
    public String savedGameMode;
    public UUID hypnotist;
    public long hypnosisUntil;
    public int hypnosisRestrictions = HypnosisRestrictions.ALL;
    public float anchorYaw;
    public float anchorPitch;
    public String anchorDimension;
    public double anchorX;
    public double anchorY;
    public double anchorZ;
    public boolean savedMayFly;
    public boolean savedFlying;
    public boolean savedInvulnerable;
    public boolean savedNoGravity;
}
