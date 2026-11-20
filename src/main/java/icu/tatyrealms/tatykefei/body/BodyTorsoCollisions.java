package icu.tatyrealms.tatykefei.body;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class BodyTorsoCollisions {
    private static final double CONTACT_MARGIN = 0.001;

    private BodyTorsoCollisions() {
    }

    static Set<UUID> resolve(Map<UUID, Body> bodies, Map<UUID, Entity> entities) {
        if (entities.size() < 2) {
            return Set.of();
        }
        Map<ServerLevel, List<Torso>> levels = new HashMap<>();
        for (var entry : entities.entrySet()) {
            Body body = bodies.get(entry.getKey());
            Entity entity = entry.getValue();
            if (body == null || body.displayMounted || body.skinPlaced || entity.isRemoved() || entity.noPhysics
                    || !entity.getUUID().equals(body.entityId) || !(entity.level() instanceof ServerLevel level)
                    || body.rigPose == null || body.rigPose.mounted || !body.rigPose.valid()) {
                continue;
            }
            Vec3 position = entity.position();
            Box box = Box.of(body.rigPose, position);
            double fall = Math.max(0, entity.oldPosition().y - entity.getY());
            levels.computeIfAbsent(level, ignored -> new ArrayList<>())
                    .add(new Torso(body.id, entity, box, position, fall, box.bounds.expandTowards(0, fall, 0)));
        }
        Set<UUID> supported = new HashSet<>();
        for (List<Torso> torsos : levels.values()) {
            torsos.sort(Comparator.<Torso>comparingDouble(torso -> torso.box.bounds.minX).thenComparing(Torso::id));
            for (int index = 0; index < torsos.size(); index++) {
                Torso first = torsos.get(index);
                AABB sweep = first.sweep.inflate(CONTACT_MARGIN);
                for (int next = index + 1; next < torsos.size(); next++) {
                    Torso second = torsos.get(next);
                    if (second.box.bounds.minX >= sweep.maxX) {
                        break;
                    }
                    if (sweep.intersects(second.sweep)) {
                        resolve(first, second, supported);
                    }
                }
            }
        }
        return supported;
    }

    private static void resolve(Torso first, Torso second, Set<UUID> supported) {
        Vec3 between = second.center().subtract(first.center());
        double fall = second.fall - first.fall;
        if (Math.abs(fall) > CONTACT_MARGIN) {
            Torso upper = fall > 0 ? second : first;
            Torso lower = fall > 0 ? first : second;
            double lift = lower.box.sweptLift(upper.box, fall > 0 ? between : between.reverse(), Math.abs(fall));
            if (lift > CONTACT_MARGIN && support(upper, lift)) {
                supported.add(upper.id);
                return;
            }
            between = second.center().subtract(first.center());
        }
        Contact contact = first.box.contact(second.box, between);
        if (contact == null) {
            return;
        }
        if (Math.abs(contact.axis.y) > 0.65 && contact.axis.y * between.y > 0 && Math.abs(between.y) > 0.04) {
            Torso upper = between.y > 0 ? second : first;
            double lift = Math.max(0, contact.depth + CONTACT_MARGIN * 0.25) / Math.abs(contact.axis.y);
            if (support(upper, lift)) {
                supported.add(upper.id);
            } else if (contact.side != null) {
                separate(first, second, contact.side, contact.sideDepth + CONTACT_MARGIN);
            }
        } else if (contact.depth > 0 && contact.side != null) {
            separate(first, second, contact.side, contact.sideDepth + CONTACT_MARGIN);
        }
    }

    private static boolean support(Torso upper, double lift) {
        if (move(upper.entity, Vec3.Y_AXIS, lift) + CONTACT_MARGIN < lift) {
            return false;
        }
        Vec3 velocity = upper.entity.getDeltaMovement();
        if (velocity.y < 0) {
            upper.entity.setDeltaMovement(new Vec3(velocity.x, 0, velocity.z));
        }
        upper.entity.resetFallDistance();
        if (!upper.entity.onGround()) {
            upper.entity.setOnGround(true);
        }
        return true;
    }

    private static void separate(Torso first, Torso second, Vec3 axis, double depth) {
        Vec3 opposite = axis.reverse();
        double moved = move(first.entity, opposite, depth * 0.5);
        moved += move(second.entity, axis, depth - moved);
        move(first.entity, opposite, depth - moved);
    }

    private static double move(Entity entity, Vec3 axis, double distance) {
        if (distance <= 0 || entity.isRemoved()) {
            return 0;
        }
        Vec3 delta = axis.scale(distance);
        if (!entity.level().getWorldBorder().isWithinBounds(entity.getBoundingBox().move(delta))) {
            return 0;
        }
        Vec3 before = entity.position();
        entity.move(MoverType.SELF, delta);
        return Math.clamp(entity.position().subtract(before).dot(axis), 0, distance);
    }

    private record Torso(UUID id, Entity entity, Box box, Vec3 origin, double fall, AABB sweep) {
        Vec3 center() {
            return box.center.add(entity.position().subtract(origin));
        }
    }

    private static final class Contact {
        Vec3 axis;
        double depth = Double.POSITIVE_INFINITY;
        Vec3 side;
        double sideDepth = Double.POSITIVE_INFINITY;

        boolean test(Box first, Box second, Vec3 between, Vec3 direction) {
            if (direction.lengthSqr() < 1.0E-10) {
                return true;
            }
            Vec3 normal = direction.normalize();
            double distance = between.dot(normal);
            double overlap = first.radius(normal) + second.radius(normal) - Math.abs(distance);
            if (overlap < -CONTACT_MARGIN) {
                return false;
            }
            if (distance < 0) {
                normal = normal.reverse();
            }
            if (overlap < depth) {
                axis = normal;
                depth = overlap;
            }
            Vec3 flat = new Vec3(normal.x, 0, normal.z);
            double horizontal = flat.length();
            if (horizontal > 1.0E-6 && overlap / horizontal < sideDepth) {
                side = flat.scale(1 / horizontal);
                sideDepth = overlap / horizontal;
            }
            return true;
        }
    }

    private static final class VerticalSweep {
        double entry = Double.NEGATIVE_INFINITY;
        double exit = Double.POSITIVE_INFINITY;

        boolean clip(Box lower, Box upper, Vec3 between, Vec3 axis) {
            if (axis.lengthSqr() < 1.0E-10) {
                return true;
            }
            double radius = lower.radius(axis) + upper.radius(axis);
            double offset = between.dot(axis);
            if (Math.abs(axis.y) < 1.0E-8) {
                return Math.abs(offset) < radius;
            }
            double first = (-radius - offset) / axis.y;
            double second = (radius - offset) / axis.y;
            entry = Math.max(entry, Math.min(first, second));
            exit = Math.min(exit, Math.max(first, second));
            return exit > entry;
        }
    }

    private record Box(Vec3 center, Vec3[] axes, Vec3 halfSize, AABB bounds) {
        static Box of(BodyRigPose pose, Vec3 origin) {
            BodyRigPose.Part torso = pose.part(BodyRigPose.TORSO_PART);
            BodyRigPose.Basis basis = pose.basis();
            Vec3[] axes = {basis.right(), basis.down(), basis.forward()};
            Vec3 center = torso.center().add(origin);
            Vec3 halfSize = torso.max().subtract(torso.min()).scale(0.5);
            double width = radius(axes, halfSize, Vec3.X_AXIS);
            double height = radius(axes, halfSize, Vec3.Y_AXIS);
            double depth = radius(axes, halfSize, Vec3.Z_AXIS);
            return new Box(center, axes, halfSize, new AABB(center, center).inflate(width, height, depth));
        }

        private static double radius(Vec3[] axes, Vec3 halfSize, Vec3 direction) {
            return halfSize.x * Math.abs(axes[0].dot(direction)) + halfSize.y * Math.abs(axes[1].dot(direction))
                    + halfSize.z * Math.abs(axes[2].dot(direction));
        }

        double radius(Vec3 direction) {
            return radius(axes, halfSize, direction);
        }

        double sweptLift(Box other, Vec3 between, double fall) {
            VerticalSweep sweep = new VerticalSweep();
            for (Vec3 axis : axes) {
                if (!sweep.clip(this, other, between, axis)) {
                    return 0;
                }
                for (Vec3 otherAxis : other.axes) {
                    if (!sweep.clip(this, other, between, axis.cross(otherAxis))) {
                        return 0;
                    }
                }
            }
            for (Vec3 axis : other.axes) {
                if (!sweep.clip(this, other, between, axis)) {
                    return 0;
                }
            }
            return sweep.exit > 0 && sweep.exit <= fall + CONTACT_MARGIN ? sweep.exit + CONTACT_MARGIN * 0.25 : 0;
        }

        Contact contact(Box other, Vec3 between) {
            Contact result = new Contact();
            for (Vec3 axis : axes) {
                if (!result.test(this, other, between, axis)) {
                    return null;
                }
            }
            for (Vec3 axis : other.axes) {
                if (!result.test(this, other, between, axis)) {
                    return null;
                }
            }
            for (Vec3 axis : axes) {
                for (Vec3 otherAxis : other.axes) {
                    if (!result.test(this, other, between, axis.cross(otherAxis))) {
                        return null;
                    }
                }
            }
            return result;
        }
    }
}
