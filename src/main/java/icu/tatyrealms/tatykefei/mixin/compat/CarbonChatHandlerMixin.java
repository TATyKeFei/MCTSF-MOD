package icu.tatyrealms.tatykefei.mixin.compat;

import icu.tatyrealms.tatykefei.compat.carbonchat.CarbonChatCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.draycia.carbon.fabric.listeners.FabricChatHandler", remap = false)
public abstract class CarbonChatHandlerMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void tsf$registerChatHandler(CallbackInfo callback) {
        CarbonChatCompat.registerChatHandler(this);
    }
}
