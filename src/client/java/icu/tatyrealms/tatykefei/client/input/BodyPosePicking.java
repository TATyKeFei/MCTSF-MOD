package icu.tatyrealms.tatykefei.client.input;

import icu.tatyrealms.tatykefei.body.BodyPicking;
import icu.tatyrealms.tatykefei.body.BodyRigPose;
import icu.tatyrealms.tatykefei.client.render.BodyRigRendering;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class BodyPosePicking {
    private static boolean enabled;

    private BodyPosePicking() {
    }

    public static void configure(boolean value) {
        enabled = value;
    }

    public static boolean applies(Entity entity) {
        return enabled && BodyPicking.hasPose(entity);
    }

    public static Predicate<Entity> vanillaTargets(Predicate<Entity> predicate) {
        return enabled ? predicate.and(entity -> !applies(entity)) : predicate;
    }

    public static EntityHitResult nearest(Entity camera, Vec3 from, Vec3 to, float partialTick,
            Predicate<Entity> predicate, EntityHitResult original) {
        if (!enabled || Minecraft.getInstance().level == null) {
            return original;
        }
        double nearest = original == null ? from.distanceToSqr(to) : from.distanceToSqr(original.getLocation());
        double searchRange = Math.sqrt(nearest) + 8;
        EntityHitResult result = original;
        for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
            if (entity == camera || !applies(entity) || !predicate.test(entity)
                    || entity.getRootVehicle() == camera.getRootVehicle()
                    || entity.distanceToSqr(from) > searchRange * searchRange) {
                continue;
            }
            BodyRigPose pose = BodyRigRendering.frame(entity, partialTick);
            if (pose == null) {
                continue;
            }
            Vec3 hit = BodyPicking.hit(pose, entity.getPosition(partialTick), from, to);
            if (hit != null && (hit.distanceToSqr(from) > 1.0E-8 || entity.canBePickedFromInside())
                    && hit.distanceToSqr(from) < nearest) {
                nearest = hit.distanceToSqr(from);
                result = new EntityHitResult(entity, hit);
            }
        }
        return result;
    }

    public static HitResult withAttackRange(Entity camera, float partialTick, AttackRange range,
            Predicate<Entity> predicate, HitResult original) {
        if (!enabled) {
            return original;
        }
        Vec3 eye = camera.getEyePosition(partialTick);
        Vec3 look = camera.getViewVector(partialTick);
        double maxRange = range.effectiveMaxRange(camera) + Math.max(0, camera.getKnownMovement().dot(look));
        HitResult block = camera.pick(maxRange, partialTick, false);
        Vec3 from = eye.add(look.scale(range.effectiveMinRange(camera)));
        if (eye.distanceToSqr(block.getLocation()) < eye.distanceToSqr(from)) {
            return original;
        }
        EntityHitResult hit = nearest(camera, from, block.getLocation(), partialTick, predicate,
                original instanceof EntityHitResult entityHit ? entityHit : null);
        return hit == null ? original : hit;
    }
}
