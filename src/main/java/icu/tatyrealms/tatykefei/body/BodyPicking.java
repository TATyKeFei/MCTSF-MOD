package icu.tatyrealms.tatykefei.body;

import icu.tatyrealms.tatykefei.config.TSFServerConfig;
import icu.tatyrealms.tatykefei.state.EmptyBodyHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class BodyPicking {
    private BodyPicking() {
    }

    public static boolean hasPose(Entity entity) {
        return entity instanceof EmptyBodyHolder holder && holder.tsf$isEmptyBody()
                && !holder.tsf$isFoldedSkin() && holder.tsf$rigPose().startsWith("f,");
    }

    public static BodyRigPose serverPose(Entity entity) {
        return TSFServerConfig.get().bodyPosePicking && hasPose(entity)
                ? BodyRigPose.decode(((EmptyBodyHolder) entity).tsf$rigPose()) : null;
    }

    public static Vec3 hit(BodyRigPose pose, Vec3 position, Vec3 from, Vec3 to) {
        Vec3 start = from.subtract(position);
        Vec3 end = to.subtract(position);
        double nearest = Double.POSITIVE_INFINITY;
        for (int part = 0; part < 6; part++) {
            nearest = Math.min(nearest, pose.part(part).hit(start, end));
        }
        return Double.isFinite(nearest) ? from.add(to.subtract(from).normalize().scale(nearest)) : null;
    }

    public static boolean canInteract(ServerPlayer player, Entity entity, BodyRigPose pose) {
        Vec3 eye = player.getEyePosition();
        for (int index = 0; index < 6; index++) {
            BodyRigPose.Part part = pose.part(index);
            Vec3 local = BodyRigPose.transform(new Quaternionf(part.rotation()).conjugate(),
                    eye.subtract(entity.position()).subtract(part.anchor()));
            Vec3 closest = new Vec3(Math.clamp(local.x, part.min().x, part.max().x),
                    Math.clamp(local.y, part.min().y, part.max().y),
                    Math.clamp(local.z, part.min().z, part.max().z));
            Vec3 point = entity.position().add(part.anchor()).add(BodyRigPose.transform(part.rotation(), closest));
            if (eye.distanceToSqr(point) <= 25 && (visible(player, eye, point)
                    || visible(player, eye, entity.position().add(part.center())))) {
                return true;
            }
        }
        return false;
    }

    private static boolean visible(ServerPlayer player, Vec3 from, Vec3 to) {
        return player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }
}
