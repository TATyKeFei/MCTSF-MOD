package icu.tatyrealms.tatykefei.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import icu.tatyrealms.tatykefei.client.render.EmptyBodyRenderState;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @WrapOperation(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitShadow(Lcom/mojang/blaze3d/vertex/PoseStack;FLjava/util/List;)V"))
    private void tsf$bodyShadowOffset(SubmitNodeCollector collector, PoseStack poses, float radius,
            List<EntityRenderState.ShadowPiece> pieces, Operation<Void> original, @Local(argsOnly = true) EntityRenderState state) {
        if (!(state instanceof EmptyBodyRenderState body) || body.tsf$shadow() == null) {
            original.call(collector, poses, radius, pieces);
            return;
        }
        poses.pushPose();
        try {
            poses.translate(body.tsf$shadow().offset());
            original.call(collector, poses, radius, pieces);
        } finally {
            poses.popPose();
        }
    }
}
