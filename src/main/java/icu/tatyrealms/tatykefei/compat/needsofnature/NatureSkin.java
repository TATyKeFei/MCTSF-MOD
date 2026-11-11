package icu.tatyrealms.tatykefei.compat.needsofnature;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class NatureSkin {
    public UUID id = UUID.randomUUID();
    public int revision;
    public String texture;
    public Map<Integer, String> masks = new HashMap<>();
    public Map<Integer, Stage> stages = new HashMap<>();

    public record Stage(boolean includeFinal, String texture) {
    }
}
