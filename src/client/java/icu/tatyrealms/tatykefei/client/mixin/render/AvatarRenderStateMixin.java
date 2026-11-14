package icu.tatyrealms.tatykefei.client.mixin.render;

import icu.tatyrealms.tatykefei.client.render.EmptyBodyRenderState;
import icu.tatyrealms.tatykefei.client.render.EmptyBodyShadow;
import icu.tatyrealms.tatykefei.client.render.AnimatedAvatarRenderState;
import icu.tatyrealms.tatykefei.client.render.BodyAnimationPose;
import icu.tatyrealms.tatykefei.client.render.FaceRenderState;
import icu.tatyrealms.tatykefei.client.render.PreviewRenderState;
import icu.tatyrealms.tatykefei.body.BodyRigPose;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements EmptyBodyRenderState, AnimatedAvatarRenderState, FaceRenderState,
        PreviewRenderState {
    @Unique
    private float tsf$previewYaw;

    @Override
    public float tsf$previewYaw() {
        return tsf$previewYaw;
    }

    @Override
    public void tsf$setPreviewYaw(float yaw) {
        tsf$previewYaw = yaw;
    }

    @Unique
    private String tsf$face;

    @Override
    public String tsf$face() {
        return tsf$face;
    }

    @Override
    public void tsf$setFace(String face) {
        tsf$face = face;
    }

    @Unique
    private EmptyBodyShadow tsf$shadow;

    @Override
    public EmptyBodyShadow tsf$shadow() {
        return tsf$shadow;
    }

    @Override
    public void tsf$setShadow(EmptyBodyShadow shadow) {
        tsf$shadow = shadow;
    }

    @Unique
    private BodyRigPose tsf$rig;

    @Override
    public BodyRigPose tsf$rig() {
        return tsf$rig;
    }

    @Override
    public void tsf$setRig(BodyRigPose pose) {
        tsf$rig = pose;
    }

    @Unique
    private BodyAnimationPose.Frame tsf$animation;

    @Override
    public BodyAnimationPose.Frame tsf$animation() {
        return tsf$animation;
    }

    @Override
    public void tsf$setAnimation(BodyAnimationPose.Frame animation) {
        tsf$animation = animation;
    }

    @Unique
    private boolean tsf$emptyBody;
    @Unique
    private boolean tsf$foldedSkin;

    @Override
    public boolean tsf$isFoldedSkin() {
        return tsf$foldedSkin;
    }

    @Override
    public void tsf$setFoldedSkin(boolean foldedSkin) {
        tsf$foldedSkin = foldedSkin;
    }

    @Override
    public boolean tsf$isEmptyBody() {
        return tsf$emptyBody;
    }

    @Override
    public void tsf$setEmptyBody(boolean emptyBody) {
        tsf$emptyBody = emptyBody;
    }
}
