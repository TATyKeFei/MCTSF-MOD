package icu.tatyrealms.tatykefei.client.mixin;

import icu.tatyrealms.tatykefei.state.BodyManager;
import icu.tatyrealms.tatykefei.state.Form;
import icu.tatyrealms.tatykefei.client.ObserverView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonRendererMixin {
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void tsf$hideHands(CallbackInfo callback) {
        var player = Minecraft.getInstance().player;
        if (player != null && BodyManager.form(player) != Form.HUMAN && ObserverView.target() == null) {
            callback.cancel();
        }
    }
}
