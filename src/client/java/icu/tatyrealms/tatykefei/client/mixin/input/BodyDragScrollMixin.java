package icu.tatyrealms.tatykefei.client.mixin.input;

import icu.tatyrealms.tatykefei.client.input.BodyDragging;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class BodyDragScrollMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void tsf$dragDistance(long window, double horizontal, double vertical, CallbackInfo callback) {
        if (BodyDragging.scroll(vertical)) {
            callback.cancel();
        }
    }
}
