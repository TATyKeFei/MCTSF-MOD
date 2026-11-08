package icu.tatyrealms.tatykefei.client;

import icu.tatyrealms.tatykefei.TSF;
import icu.tatyrealms.tatykefei.compat.NeedsOfNatureCompat;
import icu.tatyrealms.tatykefei.network.BodyAnimation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;

public final class NeedsOfNatureClientCompat {
    private static Constructor<?> roleSelection;
    private NeedsOfNatureClientCompat() {
    }

    public static void initialize() {
        if (!FabricLoader.getInstance().isModLoaded("needsofnature")) {
            return;
        }
        NeedsOfNatureObserver.initialize();
        try {
            Class<?> selection = Class.forName("com.nonid.client.NonPlayerInteractionClient$AnimationSelectionScreen");
            Class<?> group = Class.forName("com.nonid.client.NonPlayerInteractionClient$CandidateGroup");
            roleSelection = Class.forName("com.nonid.client.NonPlayerInteractionClient$RoleSelectionScreen")
                    .getDeclaredConstructor(selection, UUID.class, group);
            roleSelection.setAccessible(true);
        } catch (ReflectiveOperationException | LinkageError exception) {
            NeedsOfNatureCompat.warn(exception);
        }
        try {
            Method animation = Class.forName("com.nonid.internal.animation.client.runtime.ClientAnimationRuntime")
                    .getMethod("findLatestActiveAnimationIdContaining", UUID.class);
            Constructor<?> override = Class.forName("com.nonid.api.client.NonAnimationRenderEvents$ModelOverride")
                    .getConstructor(Identifier.class, Identifier.class);
            NeedsOfNatureCompat.registerListener("com.nonid.api.client.NonAnimationRenderEvents", "RESOLVE",
                    "com.nonid.api.client.NonAnimationRenderEvents$ModelResolver", arguments -> {
                        if (arguments[0] instanceof AbstractClientPlayer player) {
                            try {
                                Identifier current = (Identifier) animation.invoke(null, player.getUUID());
                                if (current != null && Arrays.stream(BodyAnimation.values())
                                        .anyMatch(candidate -> current.getNamespace().equals(TSF.MOD_ID)
                                                && (current.getPath().equals(candidate.id().getPath())
                                                || current.getPath().equals(candidate.id().getPath() + ".p1")))) {
                                    var skin = player.getSkin();
                                    String model = skin.model() == PlayerModelType.SLIM ? "transition_player_slim" : "transition_player";
                                    return override.newInstance(TSF.id("entity/" + model), skin.body().texturePath());
                                }
                            } catch (ReflectiveOperationException exception) {
                                NeedsOfNatureCompat.warn(exception);
                            }
                        }
                        return null;
                    });
        } catch (ReflectiveOperationException | LinkageError exception) {
            NeedsOfNatureCompat.warn(exception);
        }
    }

    public static Screen roleSelection(Screen parent, UUID request, Object group) {
        if (roleSelection == null) {
            return null;
        }
        try {
            return (Screen) roleSelection.newInstance(parent, request, group);
        } catch (ReflectiveOperationException | LinkageError exception) {
            roleSelection = null;
            NeedsOfNatureCompat.warn(exception);
            return null;
        }
    }
}
