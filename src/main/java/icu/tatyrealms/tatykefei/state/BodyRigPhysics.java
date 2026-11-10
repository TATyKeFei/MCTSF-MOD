package icu.tatyrealms.tatykefei.state;

import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Fixed-step Verlet integration with a pinned pelvis, joint limits and block collision. */
public final class BodyRigPhysics {
    private final Vec3[] previous = new Vec3[BodyRigPose.COUNT];
    private Vec3 lastPosition;

    public BodyRigPhysics copy() {
        BodyRigPhysics copy = new BodyRigPhysics();
        System.arraycopy(previous, 0, copy.previous, 0, previous.length);
        copy.lastPosition = lastPosition;
        return copy;
    }

    public void reset(BodyRigPose pose, Vec3 position) {
        for (int node = 0; node < previous.length; node++) {
            previous[node] = pose.point(node);
        }
        lastPosition = position;
    }

    public void step(Vec3 position, List<AABB> collisions, BodyRigPose pose, int grabbedPart, Vec3 target, boolean simulate) {
        if (lastPosition == null) {
            reset(pose, position);
        }
        Vec3 movement = position.subtract(lastPosition);
        if (movement.lengthSqr() > 4) {
            reset(pose, position);
            movement = Vec3.ZERO;
        }
        lastPosition = position;
        Vec3 pelvis = pose.point(0);
        if (simulate) {
            for (int node = 1; node < previous.length; node++) {
                Vec3 current = pose.point(node);
                Vec3 velocity = current.subtract(previous[node]).scale(0.84).subtract(movement.scale(0.25));
                if (velocity.length() > 0.18) {
                    velocity = velocity.normalize().scale(0.18);
                }
                previous[node] = current;
                pose.set(node, current.add(velocity).add(0, -0.024, 0));
            }
        }
        int node = grabbedPart < 0 ? -1 : BodyRigPose.node(grabbedPart);
        for (int iteration = 0; iteration < 10; iteration++) {
            pose.set(0, pelvis);
            if (node >= 0) {
                Vec3 old = pose.point(node);
                Vec3 delta = target.subtract(old);
                pose.set(node, old.add(delta.scale(0.65)));
            }
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            constrain(pose);
            if (simulate || node >= 0) {
                collide(position, collisions, pose);
            }
        }
        // Collision projections must never leave stretched limbs in the rendered pose.
        constrain(pose);
        if (!simulate) {
            reset(pose, position);
        }
    }

    public static void constrain(BodyRigPose pose) {
        length(pose, 1, pose.point(0), BodyRigPose.TORSO);
        Vec3 neck = pose.point(1);
        var basis = pose.basis();
        pose.set(2, neck.add(basis.right().scale(0.3125 * BodyRigPose.SCALE))
                .add(basis.down().scale(0.125 * BodyRigPose.SCALE)));
        limited(pose, 3, neck, basis.down().reverse(), BodyRigPose.HEAD, 0.15);
        limited(pose, 4, pose.shoulder(false), basis.down(), BodyRigPose.ARM, -0.9);
        limited(pose, 5, pose.shoulder(true), basis.down(), BodyRigPose.ARM, -0.9);
        limited(pose, 6, pose.hip(false), basis.down(), BodyRigPose.LEG, -0.25);
        limited(pose, 7, pose.hip(true), basis.down(), BodyRigPose.LEG, -0.25);
    }

    private static void length(BodyRigPose pose, int node, Vec3 joint, double length) {
        Vec3 direction = pose.point(node).subtract(joint);
        if (direction.lengthSqr() < 1.0E-8) {
            direction = new Vec3(0, 1, 0);
        }
        pose.set(node, joint.add(direction.normalize().scale(length)));
    }

    private static void limited(BodyRigPose pose, int node, Vec3 joint, Vec3 rest, double length, double minimumDot) {
        Vec3 direction = pose.point(node).subtract(joint).normalize();
        if (direction.lengthSqr() < 0.5) {
            direction = rest;
        }
        double dot = direction.dot(rest);
        if (dot < minimumDot) {
            Vec3 sideways = direction.subtract(rest.scale(dot)).normalize();
            if (sideways.lengthSqr() < 0.5) {
                sideways = pose.basis().forward();
            }
            direction = rest.scale(minimumDot).add(sideways.scale(Math.sqrt(1 - minimumDot * minimumDot)));
        }
        pose.set(node, joint.add(direction.scale(length)));
    }

    private void collide(Vec3 origin, List<AABB> collisions, BodyRigPose pose) {
        // Resolve probes along each solid limb, including the head's centre and torso.
        for (int part = 0; part < 6; part++) {
            Vec3 anchor = pose.joint(part);
            Vec3 end = pose.point(part == BodyRigPose.TORSO_PART ? 0 : BodyRigPose.node(part));
            int node = BodyRigPose.node(part);
            double radius = part == BodyRigPose.HEAD_PART ? 0.225
                    : part == BodyRigPose.TORSO_PART ? 0.14 : 0.115;
            Vec3 correction = Vec3.ZERO;
            int samples = part == BodyRigPose.HEAD_PART ? 1 : 4;
            for (int sample = 0; sample < samples; sample++) {
                double amount = (sample + 0.5) / samples;
                Vec3 center = origin.add(anchor.lerp(end, amount)).add(correction);
                correction = correction.add(resolve(collisions, center, radius));
            }
            if (correction.lengthSqr() > 1.0E-10) {
                if (correction.length() > 0.2) {
                    correction = correction.normalize().scale(0.2);
                }
                pose.set(node, pose.point(node).add(correction));
                previous[node] = pose.point(node);
            }
        }
    }

    private static Vec3 resolve(List<AABB> collisions, Vec3 point, double radius) {
        AABB bounds = new AABB(point, point).inflate(radius);
        Vec3 resolved = Vec3.ZERO;
        for (AABB box : collisions) {
            AABB probe = bounds.move(resolved);
            if (!box.intersects(probe)) {
                continue;
            }
            double x = nearer(box.minX - probe.maxX, box.maxX - probe.minX);
            double y = nearer(box.minY - probe.maxY, box.maxY - probe.minY);
            double z = nearer(box.minZ - probe.maxZ, box.maxZ - probe.minZ);
            if (Math.abs(x) < Math.abs(y) && Math.abs(x) < Math.abs(z)) {
                resolved = resolved.add(x + Math.copySign(0.0001, x), 0, 0);
            } else if (Math.abs(y) <= Math.abs(z)) {
                resolved = resolved.add(0, y + Math.copySign(0.0001, y), 0);
            } else {
                resolved = resolved.add(0, 0, z + Math.copySign(0.0001, z));
            }
        }
        return resolved;
    }

    private static double nearer(double negative, double positive) {
        return Math.abs(negative) < Math.abs(positive) ? negative : positive;
    }
}
