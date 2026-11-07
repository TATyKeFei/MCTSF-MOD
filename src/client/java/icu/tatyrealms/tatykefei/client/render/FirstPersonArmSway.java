package icu.tatyrealms.tatykefei.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;

public final class FirstPersonArmSway {
    private FirstPersonArmSway() {
    }

    public static void apply(PoseStack poses, AvatarRenderState player, InteractionHand hand, boolean usingItem) {
        if (player == null || player.deathTime > 0 || !player.hasPose(Pose.STANDING) && !player.hasPose(Pose.CROUCHING)
                || ((AnimatedAvatarRenderState) player).tsf$animation() != null) {
            return;
        }
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.mainArm : player.mainArm.getOpposite();
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float movement = Mth.clamp(player.walkAnimationSpeed, 0, 1) * (player.isCrouching ? 0.5F : 1);
        float strength = (usingItem ? 0.2F : 1) * (1 - Mth.clamp(player.swingAnimation, 0, 1) * 0.85F);
        float stride = Mth.sin(player.walkAnimationPos * 0.6662F);
        float breath = Mth.sin(player.ageInTicks * 0.09F);
        poses.translate(side * (stride * movement * 0.035F + breath * 0.003F) * strength,
                (Mth.cos(player.walkAnimationPos * 1.3324F) * movement * 0.025F + breath * 0.004F) * strength,
                0);
        poses.rotateDegrees(Axis.ZP, side * (stride * movement * 2 + breath * 0.35F) * strength);
        poses.rotateDegrees(Axis.XP, stride * movement * strength);
    }
}
