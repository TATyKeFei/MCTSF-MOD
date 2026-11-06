package icu.tatyrealms.tatykefei.client.mixin;

import icu.tatyrealms.tatykefei.client.render.SlimeColorRenderState;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SlimeRenderer.class)
public abstract class SlimeRendererMixin {
    @Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/SlimeRenderState;)Lnet/minecraft/resources/Identifier;", at = @At("HEAD"), cancellable = true)
    private void tsf$neutralTexture(SlimeRenderState state, CallbackInfoReturnable<Identifier> callback) {
        if (((SlimeColorRenderState) state).tsf$color() != null) {
            callback.setReturnValue(SlimeColorRenderState.TEXTURE);
        }
    }
}
