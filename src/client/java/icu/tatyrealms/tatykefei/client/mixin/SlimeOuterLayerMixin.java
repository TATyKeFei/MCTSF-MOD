package icu.tatyrealms.tatykefei.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import icu.tatyrealms.tatykefei.client.render.SlimeColorRenderState;
import net.minecraft.client.model.monster.slime.SlimeModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SlimeOuterLayer.class)
public abstract class SlimeOuterLayerMixin {
    @Shadow
    @Final
    private SlimeModel model;

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/SlimeRenderState;FF)V", at = @At("HEAD"), cancellable = true)
    private void tsf$coloredShell(PoseStack poses, SubmitNodeCollector collector, int light, SlimeRenderState state,
                                  float yaw, float pitch, CallbackInfo callback) {
        Integer color = ((SlimeColorRenderState) state).tsf$color();
        if (color == null) {
            return;
        }
        boolean outline = state.appearsGlowing() && state.isInvisible;
        if (!state.isInvisible || outline) {
            collector.order(1).submitModel(model, state, poses, outline ? RenderTypes.outline(SlimeColorRenderState.TEXTURE)
                    : RenderTypes.entityTranslucent(SlimeColorRenderState.TEXTURE), light,
                    LivingEntityRenderer.getOverlayCoords(state, 0), 0xFF000000 | color, null, state.outlineColor);
        }
        callback.cancel();
    }
}
