package icu.tatyrealms.tatykefei.client.mixin;

import icu.tatyrealms.tatykefei.client.TSFClient;
import icu.tatyrealms.tatykefei.client.BodyAnimations;
import icu.tatyrealms.tatykefei.client.render.BodyRigRendering;
import icu.tatyrealms.tatykefei.client.render.AnimatedAvatarRenderState;
import icu.tatyrealms.tatykefei.client.render.BodyAnimationPose;
import com.mojang.blaze3d.vertex.PoseStack;
import icu.tatyrealms.tatykefei.client.render.EmptyBodyPose;
import icu.tatyrealms.tatykefei.client.render.EmptyBodyRenderState;
import icu.tatyrealms.tatykefei.client.render.EmptyBodyShadow;
import icu.tatyrealms.tatykefei.client.render.FoldedSkinPose;
import icu.tatyrealms.tatykefei.network.VisualState;
import icu.tatyrealms.tatykefei.state.BodyManager;
import icu.tatyrealms.tatykefei.state.Form;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("HEAD"), cancellable = true)
    private void tsf$lyingPose(AvatarRenderState state, PoseStack poses, float bodyRotation, float scale, CallbackInfo callback) {
        if (((EmptyBodyRenderState) state).tsf$isEmptyBody()) {
            if (((EmptyBodyRenderState) state).tsf$isFoldedSkin()) {
                FoldedSkinPose.applyWorld(poses, bodyRotation);
            } else if (((EmptyBodyRenderState) state).tsf$rig() != null) {
                BodyRigRendering.applyWorld(poses);
            } else {
                EmptyBodyPose.apply(poses, bodyRotation);
            }
            callback.cancel();
        }
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
    private void tsf$transition(AvatarRenderState state, PoseStack poses, float bodyRotation, float scale, CallbackInfo callback) {
        BodyAnimationPose.Frame frame = ((AnimatedAvatarRenderState) state).tsf$animation();
        if (frame != null && !((EmptyBodyRenderState) state).tsf$isEmptyBody()) {
            BodyAnimationPose.transform(poses, frame);
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void tsf$spirit(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo callback) {
        boolean emptyBody = BodyManager.emptyBody(avatar);
        ((EmptyBodyRenderState) state).tsf$setEmptyBody(emptyBody);
        ((EmptyBodyRenderState) state).tsf$setFoldedSkin(BodyManager.foldedSkin(avatar));
        ((EmptyBodyRenderState) state).tsf$setRig(emptyBody ? BodyRigRendering.frame(avatar, partialTick) : null);
        ((AnimatedAvatarRenderState) state).tsf$setAnimation(avatar instanceof Player player
                && BodyManager.form(player) == Form.HUMAN ? BodyAnimations.frame(player.getUUID(), partialTick) : null);
        if (emptyBody) {
            if (!(Minecraft.getInstance().hitResult instanceof EntityHitResult hit) || hit.getEntity() != avatar) {
                state.nameTag = null;
            }
            state.bedOrientation = null;
            state.walkAnimationSpeed = 0;
            state.walkAnimationPos = 0;
            state.xRot = 0;
            state.yRot = 0;
            state.isCrouching = false;
            state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
            state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
            state.isUsingItem = false;
        }
        ((EmptyBodyRenderState) state).tsf$setShadow(EmptyBodyShadow.from(state));
        if (avatar instanceof Player player) {
            if (BodyManager.form(player) == Form.SPIRIT) {
                state.nameTag = null;
                state.scoreText = null;
                state.isInvisible = true;
                state.isInvisibleToPlayer = false;
            }
            VisualState visual = TSFClient.visual(player.getUUID());
            if (visual != null && state.nameTag != null && BodyManager.form(player) == Form.HUMAN) {
                state.nameTag = Component.literal(visual.bodyName());
            }
        }
    }
}
