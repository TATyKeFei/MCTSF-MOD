package icu.tatyrealms.tatykefei.compat.needsofnature;

import icu.tatyrealms.tatykefei.body.Body;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.LoggerFactory;

public final class NeedsOfNatureSkins {
    private static Map<UUID, byte[]> textures;
    private static Map<UUID, byte[][]> masks;
    private static Map<UUID, Map<Integer, Object>> stages;
    private static Method stageBytes;
    private static Method stageFinal;
    private static Method sendTexture;
    private static Method sendMask;
    private static Method sendStage;
    private static Constructor<?> stagePayload;
    private final Map<UUID, Source> sources = new HashMap<>();
    private final Map<UUID, Map<UUID, Sent>> sent = new HashMap<>();

    public static void initialize() {
        if (!FabricLoader.getInstance().isModLoaded("needsofnature")) {
            return;
        }
        try {
            Class<?> system = Class.forName("com.nonid.NonDestroyedSkinSystem");
            Class<?> stageSystem = Class.forName("com.nonid.NonDestroyedSkinStageSystem");
            Class<?> payload = Class.forName("com.nonid.network.DestroyedSkinStageUploadC2SPayload");
            masks = map(system, "DESTROYED_SKIN_MASKS_BY_PLAYER");
            stages = map(stageSystem, "STAGES");
            stageBytes = payload.getMethod("pngBytes");
            stageFinal = payload.getMethod("includeFinal");
            stagePayload = payload.getConstructor(int.class, boolean.class, byte[].class);
            sendTexture = system.getDeclaredMethod("sendDestroyedSkin", ServerPlayer.class, UUID.class, int.class, byte[].class);
            sendMask = system.getDeclaredMethod("sendDestroyedSkinMask", ServerPlayer.class, UUID.class, int.class, byte[].class);
            sendStage = stageSystem.getDeclaredMethod("send", ServerPlayer.class, UUID.class, payload);
            sendTexture.setAccessible(true);
            sendMask.setAccessible(true);
            sendStage.setAccessible(true);
            textures = map(system, "DESTROYED_SKINS_BY_PLAYER");
        } catch (ReflectiveOperationException | LinkageError exception) {
            disable(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <Value> Map<UUID, Value> map(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<UUID, Value>) field.get(null);
    }

    public boolean capture(Body body) {
        if (textures == null || body == null) {
            return false;
        }
        byte[] texture = textures.get(body.owner);
        byte[][] ownerMasks = masks.get(body.owner);
        Map<Integer, Object> ownerStages = stages.get(body.owner);
        if (texture == null && ownerMasks == null && ownerStages == null) {
            return false;
        }
        List<byte[]> maskList = new ArrayList<>();
        for (int stage = 1; stage <= 3; stage++) {
            maskList.add(ownerMasks != null && stage < ownerMasks.length ? ownerMasks[stage] : null);
        }
        Source source = new Source(texture, maskList, ownerStages == null ? Map.of() : Map.copyOf(ownerStages));
        if (source.equals(sources.get(body.id))) {
            return false;
        }
        try {
            NatureSkin skin = body.natureSkin == null ? new NatureSkin() : body.natureSkin;
            skin.texture = encode(texture);
            skin.masks.clear();
            for (int stage = 1; stage <= 3; stage++) {
                String mask = encode(maskList.get(stage - 1));
                if (mask != null) {
                    skin.masks.put(stage, mask);
                }
            }
            skin.stages.clear();
            for (var entry : source.stages.entrySet()) {
                Object payload = entry.getValue();
                skin.stages.put(entry.getKey(), new NatureSkin.Stage((boolean) stageFinal.invoke(payload),
                        encode((byte[]) stageBytes.invoke(payload))));
            }
            skin.revision++;
            body.natureSkin = skin;
            sources.put(body.id, source);
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            disable(exception);
            return false;
        }
    }

    public void synchronize(ServerPlayer viewer, Body body) {
        if (textures == null || body == null || body.natureSkin == null) {
            return;
        }
        NatureSkin skin = body.natureSkin;
        Map<UUID, Sent> revisions = sent.computeIfAbsent(viewer.getUUID(), ignored -> new HashMap<>());
        Sent previous = revisions.get(skin.id);
        if (previous != null && previous.revision == skin.revision) {
            return;
        }
        try {
            sendTexture.invoke(null, viewer, skin.id, 0, decode(skin.texture));
            for (int stage = 1; stage <= 3; stage++) {
                sendMask.invoke(null, viewer, skin.id, stage, decode(skin.masks.get(stage)));
            }
            if (previous != null) {
                for (int stage : previous.stages) {
                    if (!skin.stages.containsKey(stage)) {
                        sendStage.invoke(null, viewer, skin.id, stagePayload.newInstance(stage, false, new byte[0]));
                    }
                }
            }
            for (var entry : skin.stages.entrySet()) {
                NatureSkin.Stage stage = entry.getValue();
                sendStage.invoke(null, viewer, skin.id, stagePayload.newInstance(entry.getKey(), stage.includeFinal(), decode(stage.texture())));
            }
            revisions.put(skin.id, new Sent(skin.revision, Set.copyOf(skin.stages.keySet())));
        } catch (ReflectiveOperationException | LinkageError exception) {
            disable(exception);
        }
    }

    public void forget(ServerPlayer player) {
        sent.remove(player.getUUID());
    }

    private static String encode(byte[] bytes) {
        return bytes == null || bytes.length == 0 ? null : Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] decode(String encoded) {
        return encoded == null ? new byte[0] : Base64.getDecoder().decode(encoded);
    }

    private static void disable(Throwable exception) {
        textures = null;
        LoggerFactory.getLogger("TSF").warn("NeedsOfNature body skin integration unavailable", exception);
    }

    private record Source(byte[] texture, List<byte[]> masks, Map<Integer, Object> stages) {
    }

    private record Sent(int revision, Set<Integer> stages) {
    }
}
