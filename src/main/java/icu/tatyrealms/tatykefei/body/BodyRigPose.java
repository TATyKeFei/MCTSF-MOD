package icu.tatyrealms.tatykefei.body;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A small articulated skeleton, in world axes relative to the body's entity position. */
public final class BodyRigPose {
    public static final float SCALE = 0.9375F;
    public static final double TORSO = 0.75 * SCALE;
    public static final double HEAD = 0.5 * SCALE;
    public static final double ARM = 0.625 * SCALE;
    public static final double LEG = 0.75 * SCALE;
    public static final int COUNT = 8;
    public static final int HEAD_PART = 0;
    public static final int TORSO_PART = 1;
    public boolean mounted;
    public double[] points = new double[COUNT * 3];

    public record Basis(Vec3 right, Vec3 down, Vec3 forward) {
        public Quaternionf rotation() {
            Matrix3f matrix = new Matrix3f();
            matrix.setColumn(0, vector(right));
            matrix.setColumn(1, vector(down));
            matrix.setColumn(2, vector(forward));
            return new Quaternionf().setFromNormalized(matrix);
        }
    }

    public record Part(Vec3 anchor, Vec3 end, Quaternionf rotation, Vec3 min, Vec3 max) {
        public Vec3 center() {
            return anchor.add(end).scale(0.5);
        }

        public double hit(Vec3 rayStart, Vec3 rayEnd) {
            Quaternionf inverse = new Quaternionf(rotation).conjugate();
            Vec3 start = transform(inverse, rayStart.subtract(anchor));
            Vec3 endLocal = transform(inverse, rayEnd.subtract(anchor));
            AABB box = new AABB(min, max).inflate(0.025);
            if (box.contains(start)) {
                return 0;
            }
            return box.clip(start, endLocal).map(point -> point.distanceTo(start)).orElse(Double.POSITIVE_INFINITY);
        }
    }

    public static BodyRigPose create(boolean mounted, boolean upsideDown, float yaw) {
        BodyRigPose pose = new BodyRigPose();
        pose.mounted = mounted;
        Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians((mounted ? 180 : 0) - yaw));
        if (mounted) {
            if (!upsideDown) {
                rotation.rotateZ((float) Math.PI);
            }
        } else {
            rotation.rotateX((float) (Math.PI / 2)).rotateZ((float) Math.PI);
        }
        Vec3 down = transform(rotation, new Vec3(0, 1, 0));
        Vec3 right = transform(rotation, new Vec3(1, 0, 0));
        Vec3 pelvis = mounted ? new Vec3(0, 0.75, 0) : new Vec3(0, 0.21, -0.234375);
        Vec3 neck = pelvis.subtract(down.scale(TORSO));
        pose.set(0, pelvis);
        pose.set(1, neck);
        pose.set(2, neck.add(right.scale(0.3125 * SCALE)).add(down.scale(0.125 * SCALE)));
        pose.set(3, neck.subtract(down.scale(HEAD)));
        pose.set(4, pose.shoulder(false).add(down.scale(ARM)));
        pose.set(5, pose.shoulder(true).add(down.scale(ARM)));
        pose.set(6, pose.hip(false).add(down.scale(LEG)));
        pose.set(7, pose.hip(true).add(down.scale(LEG)));
        return pose;
    }

    public boolean valid() {
        if (points == null || points.length != COUNT * 3) {
            return false;
        }
        for (double value : points) {
            if (!Double.isFinite(value) || Math.abs(value) > 4) {
                return false;
            }
        }
        return true;
    }

    public Vec3 point(int node) {
        return new Vec3(points[node * 3], points[node * 3 + 1], points[node * 3 + 2]);
    }

    public void set(int node, Vec3 value) {
        points[node * 3] = value.x;
        points[node * 3 + 1] = value.y;
        points[node * 3 + 2] = value.z;
    }

    public BodyRigPose copy() {
        BodyRigPose copy = new BodyRigPose();
        copy.mounted = mounted;
        copy.points = points.clone();
        return copy;
    }

    public Basis basis() {
        Vec3 down = point(0).subtract(point(1)).normalize();
        if (down.lengthSqr() < 0.5) {
            down = new Vec3(0, -1, 0);
        }
        Vec3 side = point(2).subtract(point(1));
        Vec3 right = side.subtract(down.scale(side.dot(down))).normalize();
        if (right.lengthSqr() < 0.5) {
            right = (Math.abs(down.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).cross(down).normalize();
        }
        return new Basis(right, down, right.cross(down).normalize());
    }

    public Vec3 shoulder(boolean right) {
        Basis basis = basis();
        return point(1).add(basis.right.scale((right ? -1 : 1) * 0.3125 * SCALE)).add(basis.down.scale(0.125 * SCALE));
    }

    public Vec3 hip(boolean right) {
        return point(0).add(basis().right.scale((right ? -1 : 1) * 0.125 * SCALE));
    }

    public Vec3 joint(int part) {
        return switch (part) {
            case 0, 1 -> point(1);
            case 2, 3 -> shoulder(part == 3);
            default -> hip(part == 5);
        };
    }

    public static int node(int part) {
        return part == TORSO_PART ? 1 : part == HEAD_PART ? 3 : part + 2;
    }

    public Part part(int part) {
        Basis basis = basis();
        Vec3 anchor = joint(part);
        Vec3 end = part == TORSO_PART ? point(0) : point(node(part));
        Vec3 down = end.subtract(anchor).normalize();
        if (part == HEAD_PART) {
            down = down.reverse();
        }
        Quaternionf rotation = part == TORSO_PART ? basis.rotation()
                : new Quaternionf().rotationTo(vector(basis.down), vector(down)).mul(basis.rotation());
        double unit = SCALE / 16.0;
        Vec3 min = switch (part) {
            case 0 -> new Vec3(-4, -8, -4);
            case 1 -> new Vec3(-4, 0, -2);
            case 2 -> new Vec3(-1, -2, -2);
            case 3 -> new Vec3(-3, -2, -2);
            default -> new Vec3(-2, 0, -2);
        };
        Vec3 max = switch (part) {
            case 0 -> new Vec3(4, 0, 4);
            case 1 -> new Vec3(4, 12, 2);
            case 2 -> new Vec3(3, 10, 2);
            case 3 -> new Vec3(1, 10, 2);
            default -> new Vec3(2, 12, 2);
        };
        return new Part(anchor, end, rotation, min.scale(unit), max.scale(unit));
    }

    public String encode() {
        StringBuilder result = new StringBuilder(mounted ? "m" : "f");
        for (double value : points) {
            result.append(',').append(Math.round(value * 1000));
        }
        return result.toString();
    }

    public AABB bounds() {
        AABB bounds = new AABB(point(0), point(0));
        for (int index = 0; index < 6; index++) {
            Part part = part(index);
            for (int corner = 0; corner < 8; corner++) {
                Vec3 local = new Vec3((corner & 1) == 0 ? part.min.x : part.max.x,
                        (corner & 2) == 0 ? part.min.y : part.max.y,
                        (corner & 4) == 0 ? part.min.z : part.max.z);
                Vec3 vertex = part.anchor.add(transform(part.rotation, local));
                bounds = bounds.minmax(new AABB(vertex, vertex));
            }
        }
        return bounds.inflate(0.04);
    }

    public static BodyRigPose decode(String encoded) {
        if (encoded == null || encoded.length() > 256 || encoded.isEmpty()) {
            return null;
        }
        String[] parts = encoded.split(",");
        if (parts.length != COUNT * 3 + 1 || !parts[0].equals("m") && !parts[0].equals("f")) {
            return null;
        }
        try {
            BodyRigPose pose = new BodyRigPose();
            pose.mounted = parts[0].equals("m");
            for (int index = 0; index < pose.points.length; index++) {
                pose.points[index] = Integer.parseInt(parts[index + 1]) / 1000.0;
            }
            return pose.valid() ? pose : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static Vector3f vector(Vec3 point) {
        return new Vector3f((float) point.x, (float) point.y, (float) point.z);
    }

    public static Vec3 transform(Quaternionf rotation, Vec3 point) {
        Vector3f vector = rotation.transform(vector(point));
        return new Vec3(vector.x, vector.y, vector.z);
    }
}
