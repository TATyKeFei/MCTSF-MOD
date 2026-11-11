package icu.tatyrealms.tatykefei.mixin.compat;

import icu.tatyrealms.tatykefei.compat.carbonchat.CarbonChatCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.draycia.carbon.common.command.commands.WhisperCommand$WhisperHandler", remap = false)
public abstract class CarbonWhisperMixin {
    @Inject(method = "whisper(Lnet/draycia/carbon/api/users/CarbonPlayer;Lnet/draycia/carbon/api/users/CarbonPlayer;Lorg/incendo/cloud/minecraft/signed/SignedString;Ljava/lang/String;)V",
            at = @At("HEAD"), cancellable = true)
    private void tsf$privateSpeech(@Coerce Object sender, @Coerce Object recipient, @Coerce Object message,
                                   String targetName, CallbackInfo callback) {
        if (!CarbonChatCompat.allowPrivateMessage(sender, recipient)) {
            callback.cancel();
        }
    }
}
