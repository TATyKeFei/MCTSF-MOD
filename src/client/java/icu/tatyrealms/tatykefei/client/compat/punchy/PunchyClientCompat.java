package icu.tatyrealms.tatykefei.client.compat.punchy;

import icu.tatyrealms.tatykefei.body.BodyManager;
import icu.tatyrealms.tatykefei.client.observer.ObserverView;
import icu.tatyrealms.tatykefei.state.Form;
import java.lang.reflect.Proxy;
import java.util.function.BooleanSupplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.slf4j.LoggerFactory;

public final class PunchyClientCompat {
    // 与 SpiritItemRendering 的半透明手持物保持一致
    private static final int SPIRIT_TINT = 0x26FFFFFF;

    private PunchyClientCompat() {
    }

    public static void initialize() {
        if (!FabricLoader.getInstance().isModLoaded("punchy")) {
            return;
        }
        Class<?> api;
        try {
            api = Class.forName("punchy.api.PunchyApi");
        } catch (ClassNotFoundException | LinkageError exception) {
            warn("Punchy integration unavailable", exception);
            return;
        }
        if (registerSpiritArmTexture(api)) {
            registerSpiritArmor(api);
        } else {
            registerSpiritArms(api);
        }
        registerSlimeArms(api);
    }

    private static boolean registerSpiritArmTexture(Class<?> api) {
        try {
            Class<?> lookType = Class.forName("punchy.api.PunchyApi$ArmTextureLook");
            Class<?> textureType = Class.forName("punchy.api.PunchyApi$ArmTexture");
            Object ghost = textureType.getMethod("tint", int.class)
                    .invoke(textureType.getMethod("skin").invoke(null), SPIRIT_TINT);
            Object look = Proxy.newProxyInstance(lookType.getClassLoader(), new Class<?>[]{lookType},
                    (proxy, method, args) -> {
                        if (method.getName().equals("look") && args != null && args.length == 2) {
                            return args[0] instanceof Player player && spirit(player) ? ghost : null;
                        }
                        if (method.getName().equals("equals")) {
                            return proxy == args[0];
                        }
                        if (method.getName().equals("hashCode")) {
                            return System.identityHashCode(proxy);
                        }
                        if (method.getName().equals("toString")) {
                            return "TSF spirit arm texture";
                        }
                        return null;
                    });
            api.getMethod("armTexture", String.class, lookType).invoke(null, "tsf/spirit", look);
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            warn("Punchy spirit arm texture unavailable", exception);
            return false;
        }
    }

    private static void registerSpiritArms(Class<?> api) {
        try {
            api.getMethod("hideArms", String.class, BooleanSupplier.class)
                    .invoke(null, "tsf/spirit", (BooleanSupplier) PunchyClientCompat::spirit);
        } catch (ReflectiveOperationException | LinkageError exception) {
            warn("Punchy spirit arm hiding unavailable", exception);
        }
    }

    private static void registerSpiritArmor(Class<?> api) {
        try {
            api.getMethod("hideArmorOnArms", String.class, BooleanSupplier.class)
                    .invoke(null, "tsf/spirit", (BooleanSupplier) PunchyClientCompat::spirit);
        } catch (ReflectiveOperationException | LinkageError exception) {
            warn("Punchy spirit armor hiding unavailable", exception);
        }
    }

    private static void registerSlimeArms(Class<?> api) {
        try {
            api.getMethod("hideArms", String.class, BooleanSupplier.class)
                    .invoke(null, "tsf/slime", (BooleanSupplier) PunchyClientCompat::slime);
        } catch (ReflectiveOperationException | LinkageError exception) {
            warn("Punchy slime arm hiding unavailable", exception);
        }
    }

    private static boolean spirit(Player player) {
        return player == Minecraft.getInstance().player && ObserverView.target() == null
                && BodyManager.form(player) == Form.SPIRIT;
    }

    private static boolean spirit() {
        var client = Minecraft.getInstance();
        return client.player != null && spirit(client.player);
    }

    private static boolean slime() {
        var client = Minecraft.getInstance();
        return client.player != null && ObserverView.target() == null
                && BodyManager.form(client.player) == Form.SLIME;
    }

    private static void warn(String message, Throwable exception) {
        LoggerFactory.getLogger("TSF").warn(message, exception);
    }
}
