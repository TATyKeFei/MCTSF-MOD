package icu.tatyrealms.tatykefei.client.mixin;

import icu.tatyrealms.tatykefei.client.render.EmptyBodyRenderState;
import icu.tatyrealms.tatykefei.client.render.AnimatedAvatarRenderState;
import icu.tatyrealms.tatykefei.client.render.BodyAnimationPose;
import icu.tatyrealms.tatykefei.state.BodyRigPose;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements EmptyBodyRenderState, AnimatedAvatarRenderState {
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
