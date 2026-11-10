package icu.tatyrealms.tatykefei.client.mixin;

import icu.tatyrealms.tatykefei.client.BodyDragging;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class BodyDragInputMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void tsf$grabBody(CallbackInfo callback) {
        if (BodyDragging.start((Minecraft) (Object) this)) {
            callback.cancel();
        }
    }
}
