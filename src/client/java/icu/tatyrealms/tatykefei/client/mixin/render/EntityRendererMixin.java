package icu.tatyrealms.tatykefei.client.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import icu.tatyrealms.tatykefei.client.render.EmptyBodyRenderState;
import icu.tatyrealms.tatykefei.client.render.BodyRigRendering;
import icu.tatyrealms.tatykefei.body.BodyRigPose;
import icu.tatyrealms.tatykefei.body.BodyManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Leashable;getLeashOffset(F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 tsf$bodyLeashAnchor(Vec3 original, Entity entity, EntityRenderState state, float partialTick) {
        if (!BodyManager.emptyBody(entity) || BodyManager.foldedSkin(entity)) {
            return original;
        }
        var rig = BodyRigRendering.frame(entity, partialTick);
        float rotation = (float) Math.toRadians(entity.getPreciseBodyRotation(partialTick));
        Vec3 offset = rig == null
                ? new Vec3(0, 0.2296875, 0.118125).yRot(-rotation)
                : rig.part(BodyRigPose.TORSO_PART).center()
                        .subtract(rig.basis().forward().scale(0.125 * BodyRigPose.SCALE));
        // Rig joints already use world axes; vanilla rotates leash offsets by body yaw once more.
        float scale = entity instanceof LivingEntity living ? living.getScale() : 1;
        return offset.scale(scale).yRot(rotation);
    }

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
