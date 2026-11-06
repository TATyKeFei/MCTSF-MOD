package icu.tatyrealms.tatykefei.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import icu.tatyrealms.tatykefei.network.BodyAnimation;
import net.minecraft.client.model.player.PlayerModel;

public final class BodyAnimationPose {
    private BodyAnimationPose() {
    }

    public static void apply(PlayerModel model, Frame frame) {
        float pulse = (float) Math.sin(Math.PI * frame.progress());
        float settle = 1.0F - frame.progress();
        if (frame.animation() == BodyAnimation.WEAR_SKIN) {
            float reach = (float) Math.sin(Math.PI * Math.min(1.0F, frame.progress() * 1.35F));
            model.leftArm.xRot = -2.8F * reach;
            model.rightArm.xRot = -2.8F * reach;
            model.leftArm.zRot = -0.3F * reach;
            model.rightArm.zRot = 0.3F * reach;
            model.head.xRot = 0.35F * pulse;
            model.body.xRot = 0.12F * pulse;
        } else if (frame.animation() == BodyAnimation.ENTER_BODY) {
            model.body.xRot = 1.2F * settle;
            model.head.xRot = -0.5F * settle;
            model.leftArm.xRot = -1.4F * pulse;
            model.rightArm.xRot = -1.4F * pulse;
            model.leftLeg.xRot = -0.8F * settle;
            model.rightLeg.xRot = -0.8F * settle;
        } else {
            model.leftArm.zRot = -1.1F * pulse;
            model.rightArm.zRot = 1.1F * pulse;
            model.leftArm.xRot = -0.25F * pulse;
            model.rightArm.xRot = -0.25F * pulse;
            model.head.xRot = 0.65F * settle;
            model.body.xRot = 0.18F * pulse;
            if (frame.animation() == BodyAnimation.TAKEOVER) {
                model.head.zRot = (float) Math.sin(frame.progress() * Math.PI * 12) * 0.18F * pulse;
                model.body.zRot = -model.head.zRot * 0.35F;
            }
        }
    }

    public static void transform(PoseStack poses, Frame frame) {
        if (frame.animation() == BodyAnimation.ENTER_BODY) {
            float progress = frame.progress();
            float grow = progress * progress * (3.0F - 2.0F * progress);
            poses.scale(1.15F - 0.15F * grow, 0.18F + 0.82F * grow, 1.15F - 0.15F * grow);
        } else if (frame.animation() != BodyAnimation.WEAR_SKIN) {
            float progress = Math.min(1.0F, frame.progress() * 2.5F);
            float grow = progress * progress * (3.0F - 2.0F * progress);
            poses.scale(0.8F + 0.2F * grow, 0.45F + 0.55F * grow, 0.8F + 0.2F * grow);
        }
    }

    public record Frame(BodyAnimation animation, float progress) {
    }
}
