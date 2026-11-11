package icu.tatyrealms.tatykefei.compat.needsofnature;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.LoggerFactory;

public final class NeedsOfNatureTanks {
    private static final Map<String, Method> METHODS = new HashMap<>();
    private static Class<?> holder;
    private static Method encodeVariant;
    private static Method decodeVariant;
    private static Method encodeDonors;
    private static Method decodeDonors;
    private static Method synchronize;

    private NeedsOfNatureTanks() {
    }

    public static void initialize() {
        if (!FabricLoader.getInstance().isModLoaded("needsofnature")) {
            return;
        }
        try {
            Class<?> liquid = Class.forName("com.nonid.LiquidHolder");
            for (Method method : liquid.getMethods()) {
                METHODS.put(method.getName(), method);
            }
            for (String name : List.of("getLiquidStored", "getLiquidComposition", "getLiquidEntityTypeId",
                    "getLiquidDonorVariantData", "getFertileNectarHoneyBaseEntityTypeId", "getRecentLiquidDonors",
                    "getLastLiquidIncreaseTick", "setLiquidStored", "clearLiquidComposition", "setRecentLiquidDonors",
                    "setLiquidCompositionEntity", "setLiquidCompositionHoney", "setLiquidCompositionFertileNectar",
                    "setLiquidCompositionMixed", "setLiquidDonorVariantData", "setFertileNectarHoneyBaseEntityTypeId",
                    "setLastLiquidIncreaseTick")) {
                if (!METHODS.containsKey(name)) {
                    throw new NoSuchMethodException(name);
                }
            }
            Class<?> needs = Class.forName("com.nonid.NeedsOfNature");
            Class<?> variant = Class.forName("com.nonid.NeedsOfNature$PregnancyVariantData");
            Class<?> history = Class.forName("com.nonid.LiquidDonorHistory");
            encodeVariant = needs.getMethod("encodePregnancyVariantData", variant);
            decodeVariant = needs.getMethod("decodePregnancyVariantData", String.class);
            encodeDonors = history.getMethod("encode", Collection.class);
            decodeDonors = history.getMethod("decode", String.class);
            synchronize = needs.getMethod("syncLiquidState", ServerPlayer.class);
            holder = liquid;
            LoggerFactory.getLogger("TSF").info("Enabled independent NeedsOfNature body tanks");
        } catch (ReflectiveOperationException | LinkageError exception) {
            disable(exception);
        }
    }

    private static Object invoke(ServerPlayer player, String method, Object... arguments) throws ReflectiveOperationException {
        return METHODS.get(method).invoke(player, arguments);
    }

    public static NatureTank capture(ServerPlayer player) {
        if (holder == null || !holder.isInstance(player)) {
            return null;
        }
        try {
            NatureTank tank = new NatureTank();
            tank.stored = (int) invoke(player, "getLiquidStored");
            tank.composition = invoke(player, "getLiquidComposition").toString();
            Identifier entity = (Identifier) invoke(player, "getLiquidEntityTypeId");
            tank.entityType = entity == null ? null : entity.toString();
            tank.donorVariant = (String) encodeVariant.invoke(null, invoke(player, "getLiquidDonorVariantData"));
            Identifier honey = (Identifier) invoke(player, "getFertileNectarHoneyBaseEntityTypeId");
            tank.honeyBase = honey == null ? null : honey.toString();
            tank.donors = (String) encodeDonors.invoke(null, invoke(player, "getRecentLiquidDonors"));
            tank.lastIncrease = (long) invoke(player, "getLastLiquidIncreaseTick");
            return tank;
        } catch (ReflectiveOperationException | LinkageError exception) {
            disable(exception);
            return null;
        }
    }

    public static void restore(ServerPlayer player, NatureTank tank) {
        if (holder == null || !holder.isInstance(player)) {
            return;
        }
        try {
            invoke(player, "setLiquidStored", 0);
            invoke(player, "clearLiquidComposition");
            invoke(player, "setRecentLiquidDonors", List.of());
            if (tank != null && tank.stored > 0) {
                invoke(player, "setLiquidStored", tank.stored);
                Identifier entity = tank.entityType == null ? null : Identifier.tryParse(tank.entityType);
                switch (tank.composition) {
                    case "ENTITY" -> invoke(player, "setLiquidCompositionEntity", entity);
                    case "HONEY" -> invoke(player, "setLiquidCompositionHoney", entity);
                    case "FERTILE_NECTAR" -> invoke(player, "setLiquidCompositionFertileNectar", entity);
                    default -> invoke(player, "setLiquidCompositionMixed");
                }
                invoke(player, "setLiquidDonorVariantData", decodeVariant.invoke(null, tank.donorVariant == null ? "" : tank.donorVariant));
                invoke(player, "setFertileNectarHoneyBaseEntityTypeId", tank.honeyBase == null ? null : Identifier.tryParse(tank.honeyBase));
                invoke(player, "setRecentLiquidDonors", decodeDonors.invoke(null, tank.donors == null ? "" : tank.donors));
                invoke(player, "setLastLiquidIncreaseTick", tank.lastIncrease);
            }
            synchronize.invoke(null, player);
        } catch (ReflectiveOperationException | LinkageError exception) {
            disable(exception);
        }
    }

    private static void disable(Throwable exception) {
        holder = null;
        LoggerFactory.getLogger("TSF").warn("NeedsOfNature body tank integration unavailable", exception);
    }
}
