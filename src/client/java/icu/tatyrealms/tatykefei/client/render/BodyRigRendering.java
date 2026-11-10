package icu.tatyrealms.tatykefei.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import icu.tatyrealms.tatykefei.state.BodyRigPose;
import icu.tatyrealms.tatykefei.state.EmptyBodyHolder;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

public final class BodyRigRendering {
    private static final Map<Entity, Sample> SAMPLES = new WeakHashMap<>();

    private static final class Sample {
        String encoded;
        BodyRigPose previous;
        BodyRigPose current;
        float changed;
    }

    private BodyRigRendering() {
    }

    public static BodyRigPose frame(Entity entity, float partialTick) {
        if (!(entity instanceof EmptyBodyHolder holder) || holder.tsf$rigPose().isEmpty()) {
            SAMPLES.remove(entity);
            return null;
        }
        Sample sample = SAMPLES.computeIfAbsent(entity, ignored -> new Sample());
        float now = entity.tickCount + partialTick;
        if (!holder.tsf$rigPose().equals(sample.encoded)) {
            BodyRigPose next = BodyRigPose.decode(holder.tsf$rigPose());
            sample.previous = sample.current == null ? next : interpolate(sample, now);
            sample.current = next;
            sample.changed = now;
            sample.encoded = holder.tsf$rigPose();
        }
        return interpolate(sample, now);
    }

    private static BodyRigPose interpolate(Sample sample, float now) {
        if (sample.current == null || sample.previous == null) {
            return sample.current;
        }
        double amount = Math.clamp((now - sample.changed) / 2.0, 0, 1);
        BodyRigPose frame = sample.current.copy();
        for (int index = 0; index < frame.points.length; index++) {
            frame.points[index] = sample.previous.points[index]
                    + (sample.current.points[index] - sample.previous.points[index]) * amount;
        }
        return frame;
    }

    public static void applyWorld(PoseStack poses) {
        // Cancel the vanilla avatar flip and foot offset; the rig stores world-space joints.
        poses.translate(0, 1.501F * BodyRigPose.SCALE, 0);
        poses.scale(-1, -1, 1);
    }

    public static void applyModel(PlayerModel model, BodyRigPose pose) {
        ModelPart[] parts = {model.head, model.body, model.leftArm, model.rightArm, model.leftLeg, model.rightLeg};
        for (int index = 0; index < parts.length; index++) {
            BodyRigPose.Part bone = pose.part(index);
            ModelPart part = parts[index];
            part.x = (float) (bone.anchor().x * 16 / BodyRigPose.SCALE);
            part.y = (float) (bone.anchor().y * 16 / BodyRigPose.SCALE);
            part.z = (float) (bone.anchor().z * 16 / BodyRigPose.SCALE);
            Vector3f angles = bone.rotation().getEulerAnglesZYX(new Vector3f());
            part.xRot = angles.x;
            part.yRot = angles.y;
            part.zRot = angles.z;
        }
    }
}
