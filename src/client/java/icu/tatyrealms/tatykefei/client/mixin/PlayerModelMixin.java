package icu.tatyrealms.tatykefei.client.mixin;

import icu.tatyrealms.tatykefei.client.render.AnimatedAvatarRenderState;
import icu.tatyrealms.tatykefei.client.render.BodyAnimationPose;
import icu.tatyrealms.tatykefei.client.render.BodyRigRendering;
import icu.tatyrealms.tatykefei.client.render.EmptyBodyPose;
import icu.tatyrealms.tatykefei.client.render.EmptyBodyRenderState;
import icu.tatyrealms.tatykefei.client.render.FoldedSkinPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void tsf$pose(AvatarRenderState state, CallbackInfo callback) {
        PlayerModel model = (PlayerModel) (Object) this;
        if (((EmptyBodyRenderState) state).tsf$isEmptyBody()) {
            if (((EmptyBodyRenderState) state).tsf$isFoldedSkin()) {
                FoldedSkinPose.applyModel(model);
            } else if (((EmptyBodyRenderState) state).tsf$rig() != null) {
                BodyRigRendering.applyModel(model, ((EmptyBodyRenderState) state).tsf$rig());
            } else {
                EmptyBodyPose.applyModel(model);
            }
        } else {
            BodyAnimationPose.Frame frame = ((AnimatedAvatarRenderState) state).tsf$animation();
            if (frame != null) {
                BodyAnimationPose.apply(model, frame);
            }
        }
    }
}
