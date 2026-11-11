package icu.tatyrealms.tatykefei.compat.needsofnature;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import org.slf4j.LoggerFactory;

public final class NeedsOfNatureBodies {
    private static final Map<String, Method> METHODS = new HashMap<>();
    private static Class<?> holder;
    private static Method encodeVariant;
    private static Method decodeVariant;
    private static Method cacheDamage;
    private static Method syncDamage;
    private static Method syncMess;
    private static Method syncPregnancy;
    private static Method syncCycle;
    private static Method clearPregnancyRuntime;
    private static Method refreshPregnancy;
    private static Holder<MobEffect> pregnantEffect;

    private NeedsOfNatureBodies() {
    }

    public static void initialize() {
        if (!FabricLoader.getInstance().isModLoaded("needsofnature")) {
            return;
        }
        try {
            for (String name : List.of("DestroyedSkinHolder", "MessHolder", "GenderHolder", "PregnancyHolder", "PregnancyCycleHolder")) {
                for (Method method : Class.forName("com.nonid." + name).getMethods()) {
                    METHODS.put(method.getName() + method.getParameterCount(), method);
                }
            }
            Class<?> needs = Class.forName("com.nonid.NeedsOfNature");
            Class<?> variant = Class.forName("com.nonid.NeedsOfNature$PregnancyVariantData");
            encodeVariant = needs.getMethod("encodePregnancyVariantData", variant);
            decodeVariant = needs.getMethod("decodePregnancyVariantData", String.class);
            cacheDamage = method("NonDestroyedSkinSystem", "cacheDestroyedSkinStage", ServerPlayer.class);
            syncDamage = method("NonDestroyedSkinSystem", "broadcastDestroyedSkinStage", ServerPlayer.class);
            syncMess = method("NonMessSystem", "broadcastMessStateWithoutEvent", ServerPlayer.class);
            syncPregnancy = method("NeedsOfNature", "syncPregnancyState", ServerPlayer.class);
            syncCycle = method("NonPregnancyConceptionSystem", "syncState", ServerPlayer.class);
            clearPregnancyRuntime = method("NeedsOfNature", "clearPregnancyRuntime", UUID.class);
            refreshPregnancy = method("NeedsOfNature", "refreshPregnancyAfterDeathCopy", ServerPlayer.class);
            MobEffect effect = (MobEffect) Class.forName("com.nonid.effect.NonStatusEffects").getField("PREGNANT").get(null);
            pregnantEffect = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
            holder = Class.forName("com.nonid.DestroyedSkinHolder");
            LoggerFactory.getLogger("TSF").info("Enabled independent NeedsOfNature body appearance and pregnancy");
        } catch (ReflectiveOperationException | LinkageError exception) {
            disable(exception);
        }
    }

    private static Method method(String owner, String name, Class<?>... parameters) throws ReflectiveOperationException {
        Method method = Class.forName("com.nonid." + owner).getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static Object invoke(Player player, String method, Object... arguments) throws ReflectiveOperationException {
        Method target = METHODS.get(method + arguments.length);
        if (target == null) {
            throw new NoSuchMethodException(method);
        }
        return target.invoke(player, arguments);
    }

    public static boolean available() {
        return holder != null;
    }

    public static NatureAppearance capture(ServerPlayer player) {
        return capture(player, player.level().getServer().overworld().getGameTime());
    }

    public static NatureAppearance capture(Player player, long now) {
        if (holder == null || !holder.isInstance(player)) {
            return null;
        }
        try {
            NatureAppearance state = new NatureAppearance();
            state.damage = (int) invoke(player, "getDestroyedSkinDamage");
            state.stage = (int) invoke(player, "getDestroyedSkinStage");
            state.gender = (int) invoke(player, "getGenderMask");
            state.vMess = (int) invoke(player, "getVMess");
            state.aMess = (int) invoke(player, "getAMess");
            state.mMess = (int) invoke(player, "getMMess");
            state.vTint = (int) invoke(player, "getVMessTintRgb");
            state.aTint = (int) invoke(player, "getAMessTintRgb");
            state.mTint = (int) invoke(player, "getMMessTintRgb");
            Identifier entity = (Identifier) invoke(player, "getPregnantEntityTypeId");
            if (entity != null) {
                state.pregnantEntity = entity.toString();
                state.pregnancyRemaining = Math.max(0, (long) invoke(player, "getPregnancyEndTick") - now);
                state.pregnancyDuration = (long) invoke(player, "getPregnancyTotalDurationTicks");
                state.pregnancyPending = (boolean) invoke(player, "isPregnancyPendingHatch");
                state.offspring = (int) invoke(player, "getPregnancyOffspringCount");
                state.pregnancyVariant = (String) encodeVariant.invoke(null, invoke(player, "getPregnancyVariantData"));
            }
            state.cycleInitialized = (boolean) invoke(player, "isPregnancyCycleInitialized");
            state.cycleProgress = (long) invoke(player, "getPregnancyCycleProgressTicks");
            return state;
        } catch (ReflectiveOperationException | LinkageError exception) {
            disable(exception);
            return null;
        }
    }

    public static void apply(Player player, NatureAppearance saved, long now, long timeOfDay) {
        if (holder == null || !holder.isInstance(player)) {
            return;
        }
        try {
            NatureAppearance state = saved == null ? new NatureAppearance() : saved;
            invoke(player, "setGenderMask", state.gender);
            player.removeTag("gender.male");
            player.removeTag("gender.female");
            if ((state.gender & 1) != 0) {
                player.addTag("gender.male");
            }
            if ((state.gender & 2) != 0) {
                player.addTag("gender.female");
            }
            invoke(player, "setDestroyedSkinStage", state.stage);
            invoke(player, "setDestroyedSkinDamage", state.damage);
            invoke(player, "setVMess", state.vMess);
            invoke(player, "setAMess", state.aMess);
            invoke(player, "setMMess", state.mMess);
            invoke(player, "setVMessTintRgb", state.vTint);
            invoke(player, "setAMessTintRgb", state.aTint);
            invoke(player, "setMMessTintRgb", state.mTint);
            invoke(player, "clearPregnancyState");
            if (state.pregnantEntity != null) {
                Identifier entity = Identifier.tryParse(state.pregnantEntity);
                if (entity != null) {
                    invoke(player, "setPregnancyState", entity, now + state.pregnancyRemaining, state.pregnancyPending,
                            decodeVariant.invoke(null, state.pregnancyVariant == null ? "" : state.pregnancyVariant));
                    invoke(player, "setPregnancyTotalDurationTicks", state.pregnancyDuration);
                    invoke(player, "setPregnancyOffspringCount", state.offspring);
                }
            }
            if (state.cycleInitialized) {
                invoke(player, "setPregnancyCycleState", state.cycleProgress, now, timeOfDay);
            } else {
                invoke(player, "clearPregnancyCycleState");
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            disable(exception);
        }
    }

    public static void restore(ServerPlayer player, NatureAppearance state) {
        if (holder == null || !holder.isInstance(player)) {
            return;
        }
        try {
            clearPregnancyRuntime.invoke(null, player.getUUID());
            player.removeEffect(pregnantEffect);
            apply(player, state, player.level().getServer().overworld().getGameTime(),
                    player.level().getServer().overworld().getOverworldClockTime());
            cacheDamage.invoke(null, player);
            syncDamage.invoke(null, player);
            syncMess.invoke(null, player);
            refreshPregnancy.invoke(null, player);
            syncPregnancy.invoke(null, player);
            syncCycle.invoke(null, player);
        } catch (ReflectiveOperationException | LinkageError exception) {
            disable(exception);
        }
    }

    private static void disable(Throwable exception) {
        holder = null;
        LoggerFactory.getLogger("TSF").warn("NeedsOfNature body appearance integration unavailable", exception);
    }
}
