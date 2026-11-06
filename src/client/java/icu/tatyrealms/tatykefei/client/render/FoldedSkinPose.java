package icu.tatyrealms.tatykefei.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.player.PlayerModel;

public final class FoldedSkinPose {
    private FoldedSkinPose() {
    }

    public static void applyWorld(PoseStack poses, float bodyRotation) {
        poses.rotateDegrees(Axis.YP, 180.0F - bodyRotation);
        poses.translate(0.0F, -0.88F, 0.0F);
        poses.scale(0.8F, 0.8F, 0.8F);
    }

    public static void applyModel(PlayerModel model) {
        model.body.xRot = (float) Math.PI / 2;
        model.body.y = 4;
        model.body.z = -6;
        model.body.zScale = 0.35F;
        for (var leg : new net.minecraft.client.model.geom.ModelPart[]{model.leftLeg, model.rightLeg}) {
            leg.xRot = -(float) Math.PI / 2;
            leg.y = 2;
            leg.z = 6;
            leg.zScale = 0.35F;
        }
        model.leftArm.x = 4;
        model.rightArm.x = -4;
        model.leftArm.y = 0;
        model.rightArm.y = 0;
        model.leftArm.zRot = (float) Math.PI / 2;
        model.rightArm.zRot = -(float) Math.PI / 2;
        model.leftArm.xScale = 0.35F;
        model.rightArm.xScale = 0.35F;
        model.head.y = -1.5F;
        model.head.xRot = -(float) Math.PI / 2;
        model.head.yScale = 0.7F;
        model.head.zScale = 0.15F;
    }
}
