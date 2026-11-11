package icu.tatyrealms.tatykefei.client.mixin.render;

import icu.tatyrealms.tatykefei.client.TSFClient;
import icu.tatyrealms.tatykefei.client.render.SlimeColorRenderState;
import icu.tatyrealms.tatykefei.network.VisualState;
import icu.tatyrealms.tatykefei.state.Form;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void tsf$slimeColor(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo callback) {
        if (state instanceof SlimeColorRenderState colored) {
            VisualState visual = entity instanceof Slime ? TSFClient.visual(entity.getUUID()) : null;
            colored.tsf$setColor(visual != null && visual.form() == Form.SLIME ? visual.slimeColor() : null);
        }
    }

    @Inject(method = "getModelTint", at = @At("HEAD"), cancellable = true)
    private void tsf$slimeTint(LivingEntityRenderState state, CallbackInfoReturnable<Integer> callback) {
        if (state instanceof SlimeColorRenderState colored && colored.tsf$color() != null) {
            callback.setReturnValue(0xFF000000 | colored.tsf$color());
        }
    }
}
