package icu.tatyrealms.tatykefei.client.compat.needsofnature;

import icu.tatyrealms.tatykefei.client.TSFClient;
import icu.tatyrealms.tatykefei.network.VisualState;
import java.util.UUID;

public final class NatureSkinSource {
    private NatureSkinSource() {
    }

    public static UUID texture(UUID player) {
        VisualState visual = TSFClient.visual(player);
        if (visual != null && visual.latexSource() != null) {
            return visual.latexSource();
        }
        return visual != null && visual.natureSkin() != null ? visual.natureSkin() : owner(player);
    }

    public static UUID owner(UUID player) {
        VisualState visual = TSFClient.visual(player);
        if (visual != null && visual.latexSource() != null) {
            return visual.latexSource();
        }
        return visual != null && visual.appearanceOwner() != null ? visual.appearanceOwner() : player;
    }

    public static boolean borrowed(UUID player) {
        return !player.equals(owner(player));
    }
}
