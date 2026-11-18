package icu.tatyrealms.tatykefei.client.mixin.input;

import icu.tatyrealms.tatykefei.client.input.BodyDragging;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class BodyDragInputMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void tsf$grabDisplayAxis(CallbackInfoReturnable<Boolean> callback) {
        if (BodyDragging.startOffset((Minecraft) (Object) this)) {
            callback.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "continueAttack", at = @At("HEAD"), argsOnly = true)
    private boolean tsf$preventMiningWhileMovingDisplay(boolean down) {
        return down && !BodyDragging.movingDisplay();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void tsf$grabBody(CallbackInfo callback) {
        if (BodyDragging.start((Minecraft) (Object) this)) {
            callback.cancel();
        }
    }

    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void tsf$finishBodyEditing(boolean suppressPauseMenu, CallbackInfo callback) {
        if (BodyDragging.finishEditing()) {
            callback.cancel();
        }
    }
}
