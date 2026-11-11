package icu.tatyrealms.tatykefei.client.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import icu.tatyrealms.tatykefei.client.render.EmptyBodyRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(WingsLayer.class)
public abstract class WingsLayerMixin {
    @WrapMethod(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V")
    private void tsf$followBody(PoseStack poses, SubmitNodeCollector collector, int light,
                                HumanoidRenderState state, float yRot, float xRot, Operation<Void> original) {
        if (state instanceof EmptyBodyRenderState body && body.tsf$isEmptyBody() && body.tsf$rig() != null
                && ((RenderLayer<?, ?>) (Object) this).getParentModel() instanceof HumanoidModel<?> model) {
            poses.pushPose();
            try {
                model.body.translateAndRotate(poses);
                original.call(poses, collector, light, state, yRot, xRot);
            } finally {
                poses.popPose();
            }
        } else {
            original.call(poses, collector, light, state, yRot, xRot);
        }
    }
}
