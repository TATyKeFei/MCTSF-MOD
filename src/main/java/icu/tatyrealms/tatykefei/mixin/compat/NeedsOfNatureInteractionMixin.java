package icu.tatyrealms.tatykefei.mixin.compat;

import icu.tatyrealms.tatykefei.compat.needsofnature.NeedsOfNatureHypnosis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.nonid.NonPlayerInteractionSystem", remap = false)
public abstract class NeedsOfNatureInteractionMixin {
    @Inject(method = "resolveMode", at = @At("RETURN"), cancellable = true, require = 0)
    private static void tsf$hypnosisConsent(ServerLevel level, ServerPlayer target, ServerPlayer requester,
                                           CallbackInfoReturnable<Object> callback) {
        callback.setReturnValue(NeedsOfNatureHypnosis.requestMode(target, callback.getReturnValue()));
    }
}
