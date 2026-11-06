package icu.tatyrealms.tatykefei.network;

import icu.tatyrealms.tatykefei.TSF;
import net.minecraft.resources.Identifier;

public enum BodyAnimation {
    POSSESS("possession", 32),
    TAKEOVER("takeover", 40),
    WEAR_SKIN("wear_skin", 40),
    ENTER_BODY("enter_body", 36);

    private final String path;
    private final int duration;

    BodyAnimation(String path, int duration) {
        this.path = path;
        this.duration = duration;
    }

    public Identifier id() {
        return TSF.id(path);
    }

    public int duration() {
        return duration;
    }
}
