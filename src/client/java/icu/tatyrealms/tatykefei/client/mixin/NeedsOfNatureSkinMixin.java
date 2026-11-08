package icu.tatyrealms.tatykefei.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import icu.tatyrealms.tatykefei.client.NatureSkinSource;
import icu.tatyrealms.tatykefei.client.ObserverView;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.nonid.client.NonDestroyedSkinClient", remap = false)
public abstract class NeedsOfNatureSkinMixin {
    @ModifyVariable(method = "resolveBaseTexture", at = @At("HEAD"), argsOnly = true, require = 0)
    private static LivingEntity tsf$observedBody(LivingEntity entity) {
        var target = ObserverView.target();
        return entity == Minecraft.getInstance().player && target != null ? target : entity;
    }

    @ModifyExpressionValue(method = "getOrCreateTexture", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getUUID()Ljava/util/UUID;"), require = 0)
    private static UUID tsf$bodyTextures(UUID original) {
        return NatureSkinSource.texture(original);
    }

    @ModifyArg(method = "getOrCreateTexture", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;getOrDefault(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"), index = 0, require = 0)
    private static Object tsf$bodyMess(Object original, @Local(argsOnly = true) LivingEntity entity) {
        return entity.getUUID();
    }

    @ModifyArg(method = "getOrCreateTexture", at = @At(value = "INVOKE",
            target = "Lcom/nonid/client/NonDestroyedSkinClient;effectiveExternalSkin(Lnet/minecraft/client/Minecraft;Ljava/util/UUID;Lnet/minecraft/resources/Identifier;)Lcom/nonid/client/NonExternalSkinCompat$SkinContext;"), index = 1, require = 0)
    private static UUID tsf$bodyExternalSkin(UUID original, @Local(argsOnly = true) LivingEntity entity) {
        return NatureSkinSource.owner(entity.getUUID());
    }

    @Inject(method = "uploadGeneratedSkinIfPossible", at = @At("HEAD"), cancellable = true, require = 0)
    private static void tsf$keepOriginalUpload(CallbackInfoReturnable<Boolean> callback) {
        var player = Minecraft.getInstance().player;
        if (player != null && NatureSkinSource.borrowed(player.getUUID())) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "rememberEffectiveBaseTexture", at = @At("HEAD"), cancellable = true, require = 0)
    private static void tsf$keepOriginalBase(LivingEntity entity, Identifier texture, CallbackInfo callback) {
        if (NatureSkinSource.borrowed(entity.getUUID())) {
            callback.cancel();
        }
    }
}
