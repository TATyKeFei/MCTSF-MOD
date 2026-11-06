package icu.tatyrealms.tatykefei.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.player.PlayerModel;

public final class EmptyBodyPose {
    private EmptyBodyPose() {
    }

    public static void apply(PoseStack poses, float bodyRotation) {
        poses.rotateDegrees(Axis.YP, -bodyRotation);
        poses.translate(0.0F, 0.1125F, -0.9375F);
        poses.rotateDegrees(Axis.XP, 90.0F);
    }

    public static void applyModel(PlayerModel model) {
        model.head.z = 0;
    }
}
