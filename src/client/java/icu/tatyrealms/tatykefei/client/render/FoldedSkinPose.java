package icu.tatyrealms.tatykefei.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;

public final class FoldedSkinPose {
    private static final float HALF_PI = (float) Math.PI / 2;
    private static final float WORLD_SCALE = 0.8F;

    private FoldedSkinPose() {
    }

    public static void applyWorld(PoseStack poses, float bodyRotation) {
        poses.rotateDegrees(Axis.YP, 180.0F - bodyRotation);
        // 补偿原版玩家渲染的脚底原点，让最下层贴近地面
        poses.translate(0.0F, -0.92F, 0.0F);
        poses.scale(WORLD_SCALE, WORLD_SCALE, WORLD_SCALE);
    }

    public static void applyModel(PlayerModel model) {
        // 从下往上依次叠放躯干、腿、双袖和头部，保留皮肤外层的间距
        pose(model.body, 0, 3.6F, -6, HALF_PI, 0, 0, 1, 1, 0.18F);
        pose(model.leftLeg, 1.95F, 2.35F, 6, -HALF_PI, -0.025F, 0, 1, 1, 0.18F);
        pose(model.rightLeg, -1.95F, 2.35F, 6, -HALF_PI, 0.025F, 0, 1, 1, 0.18F);
        pose(model.leftArm, 4.4F, 1.1F, -4.8F, HALF_PI, -0.62F, 0, 1, 0.9F, 0.18F);
        pose(model.rightArm, -4.4F, 0.05F, -4.8F, HALF_PI, 0.62F, 0, 1, 0.9F, 0.18F);
        pose(model.head, 0, -1.35F, -5.5F, -HALF_PI, 0, 0, 1, 0.65F, 0.12F);
    }

    private static void pose(ModelPart part, float x, float y, float z, float pitch, float yaw, float roll,
                             float width, float length, float thickness) {
        part.setPos(x, y, z);
        part.setRotation(pitch, yaw, roll);
        part.xScale = width;
        part.yScale = length;
        part.zScale = thickness;
    }
}
