package icu.tatyrealms.tatykefei.client.render;

import icu.tatyrealms.tatykefei.state.BodyRigPose;

public interface EmptyBodyRenderState {
    boolean tsf$isEmptyBody();

    void tsf$setEmptyBody(boolean emptyBody);

    boolean tsf$isFoldedSkin();

    void tsf$setFoldedSkin(boolean foldedSkin);

    BodyRigPose tsf$rig();

    void tsf$setRig(BodyRigPose pose);
}
