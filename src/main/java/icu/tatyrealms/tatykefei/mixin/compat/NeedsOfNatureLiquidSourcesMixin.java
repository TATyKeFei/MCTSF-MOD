package icu.tatyrealms.tatykefei.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import icu.tatyrealms.tatykefei.compat.needsofnature.NatureLiquidSources;
import icu.tatyrealms.tatykefei.compat.needsofnature.NeedsOfNatureSources;
import icu.tatyrealms.tatykefei.compat.needsofnature.NeedsOfNatureTanks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.nonid.NeedsOfNature", remap = false)
public abstract class NeedsOfNatureLiquidSourcesMixin {
    @WrapOperation(method = "resolveLiquidGainProfile", at = @At(value = "INVOKE",
            target = "Lcom/nonid/NeedsOfNature;applyLiquidGainMultiplier(ID)I"), require = 0)
    private static int tsf$producedLiquid(int amount, double multiplier, Operation<Integer> original,
            @Local Entity entity, @Share("tsf$sources") LocalRef<NatureLiquidSources> sources) {
        int result = original.call(amount, multiplier);
        if (result > 0) {
            NatureLiquidSources previous = sources.get() == null ? NatureLiquidSources.EMPTY : sources.get();
            sources.set(previous.merge(NeedsOfNatureSources.producedBy(entity)));
        }
        return result;
    }

    @Inject(method = "resolveLiquidGainProfile", at = @At("RETURN"), require = 0)
    private static void tsf$gainSources(CallbackInfoReturnable<Object> callback,
            @Share("tsf$sources") LocalRef<NatureLiquidSources> sources) {
        NeedsOfNatureSources.set(callback.getReturnValue(), sources.get());
    }

    @Inject(method = "scaleLiquidGainProfile", at = @At("RETURN"), require = 0)
    private static void tsf$scaledSources(@Coerce Object gain, double scale, CallbackInfoReturnable<Object> callback) {
        NeedsOfNatureSources.set(callback.getReturnValue(), NeedsOfNatureSources.get(gain));
    }

    @Inject(method = "applyAccessoryLiquidGainMultiplier", at = @At("RETURN"), require = 0)
    private static void tsf$accessorySources(ServerPlayer player, @Coerce Object gain, CallbackInfoReturnable<Object> callback) {
        NeedsOfNatureSources.set(callback.getReturnValue(), NeedsOfNatureSources.get(gain));
    }

    @Inject(method = "mergeLiquidGainProfiles", at = @At("RETURN"), require = 0)
    private static void tsf$mergedSources(@Coerce Object first, @Coerce Object second, CallbackInfoReturnable<Object> callback) {
        Object result = callback.getReturnValue();
        if (result != first && result != second) {
            NeedsOfNatureSources.set(result, NeedsOfNatureSources.get(first).merge(NeedsOfNatureSources.get(second)));
        }
    }

    @Inject(method = "addLiquidWithComposition", at = @At("HEAD"), require = 0)
    private static void tsf$previousSources(@Coerce Object tank, @Coerce Object gain, CallbackInfoReturnable<Integer> callback,
            @Share("tsf$previousSources") LocalRef<NatureLiquidSources> previous) {
        previous.set(NeedsOfNatureTanks.stored(tank) <= 0 ? NatureLiquidSources.EMPTY : NeedsOfNatureSources.get(tank));
    }

    @Inject(method = "addLiquidWithComposition", at = @At("RETURN"), require = 0)
    private static void tsf$receivedSources(@Coerce Object tank, @Coerce Object gain, CallbackInfoReturnable<Integer> callback,
            @Share("tsf$previousSources") LocalRef<NatureLiquidSources> previous) {
        if (callback.getReturnValue() > 0) {
            NeedsOfNatureSources.set(tank, previous.get().merge(NeedsOfNatureSources.get(gain)));
        }
    }

    @Inject(method = "copyDeathPersistentPlayerState", at = @At("RETURN"), require = 0)
    private static void tsf$respawnSources(ServerPlayer oldPlayer, ServerPlayer newPlayer, CallbackInfo callback) {
        if (NeedsOfNatureTanks.stored(newPlayer) > 0) {
            NeedsOfNatureSources.set(newPlayer, NeedsOfNatureSources.get(oldPlayer));
        }
    }
}
