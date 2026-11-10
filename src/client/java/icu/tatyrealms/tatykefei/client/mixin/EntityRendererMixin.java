package icu.tatyrealms.tatykefei.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import icu.tatyrealms.tatykefei.client.render.EmptyBodyRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @WrapMethod(method = "extractShadow")
    private void tsf$bodyShadowOrigin(EntityRenderState state, Minecraft minecraft, Level level, Operation<Void> original) {
        if (!(state instanceof EmptyBodyRenderState body) || body.tsf$shadow() == null) {
            original.call(state, minecraft, level);
            return;
        }
        double x = state.x;
        double y = state.y;
        double z = state.z;
        var offset = body.tsf$shadow().offset();
        state.x += offset.x;
        state.y += offset.y;
        state.z += offset.z;
        try {
            original.call(state, minecraft, level);
        } finally {
            state.x = x;
            state.y = y;
            state.z = z;
        }
    }

    @ModifyExpressionValue(method = "extractShadow", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;getShadowRadius(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)F"))
    private float tsf$bodyShadowRadius(float radius, EntityRenderState state) {
        return state instanceof EmptyBodyRenderState body && body.tsf$shadow() != null ? body.tsf$shadow().radius() : radius;
    }
}
