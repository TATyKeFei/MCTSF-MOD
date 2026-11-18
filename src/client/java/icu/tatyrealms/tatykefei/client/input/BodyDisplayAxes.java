package icu.tatyrealms.tatykefei.client.input;

import icu.tatyrealms.tatykefei.block.BodyDisplayBlock;
import icu.tatyrealms.tatykefei.client.render.BodyRigRendering;
import icu.tatyrealms.tatykefei.network.BodyDisplayPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class BodyDisplayAxes {
    private static final double LENGTH = 0.38;
    private static final double PICK_RADIUS = 0.085;
    private static final double REACH = 7;
    private static Drag drag;

    private enum Axis {
        HORIZONTAL(BodyDisplayPayload.MOVE_HORIZONTAL, BodyDisplayBlock.MAX_HORIZONTAL_OFFSET, 0xFFEF6256),
        VERTICAL(BodyDisplayPayload.MOVE_VERTICAL, BodyDisplayBlock.MAX_VERTICAL_OFFSET, 0xFF62ED86),
        DEPTH(BodyDisplayPayload.MOVE_DEPTH, BodyDisplayBlock.MAX_DEPTH_OFFSET, 0xFF589BFF);

        private final int operation;
        private final float limit;
        private final int color;

        Axis(int operation, float limit, int color) {
            this.operation = operation;
            this.limit = limit;
            this.color = color;
        }

        private Vec3 direction(BlockState state) {
            return switch (this) {
                case HORIZONTAL -> BodyDisplayBlock.horizontalAxis(state);
                case VERTICAL -> Vec3.Y_AXIS;
                case DEPTH -> BodyDisplayBlock.depthAxis(state);
            };
        }
    }

    private record Projection(double alongAxis, double distanceSquared) {
    }

    private record Drag(Axis axis, Vec3 center, Vec3 direction, double initialProjection, double initialOffset,
            float lastSent) {
    }

    private BodyDisplayAxes() {
    }

    static void render(Entity body, BlockPos pos, float partialTick) {
        BlockState state = body.level().getBlockState(pos);
        Vec3 center = axisCenter(body, state, partialTick);
        if (center != null) {
            for (Axis axis : Axis.values()) {
                draw(center, axis.direction(state), axis.color, drag != null && drag.axis == axis);
            }
        }
    }

    private static void draw(Vec3 center, Vec3 axis, int color, boolean active) {
        float width = active ? 4 : 2.5F;
        Gizmos.arrow(center, center.add(axis.scale(LENGTH)), color, width).setAlwaysOnTop();
        Gizmos.arrow(center, center.subtract(axis.scale(LENGTH)), color, width).setAlwaysOnTop();
    }

    private static Vec3 axisCenter(Entity body, BlockState state, float partialTick) {
        if (!state.is(BodyDisplayBlock.INSTANCE)) {
            return null;
        }
        var pose = BodyRigRendering.frame(body, partialTick);
        if (pose == null || !pose.mounted) {
            return null;
        }
        Vec3 feet = pose.point(6).add(pose.point(7)).scale(0.5);
        return body.getPosition(partialTick).add(feet);
    }

    static boolean start(Minecraft client, Entity body, BlockPos pos) {
        if (drag != null) {
            return true;
        }
        Camera camera = client.gameRenderer.mainCamera();
        if (!camera.isInitialized() || !ClientPlayNetworking.canSend(BodyDisplayPayload.TYPE)) {
            return false;
        }
        BlockState state = body.level().getBlockState(pos);
        Vec3 center = axisCenter(body, state, client.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        if (center == null) {
            return false;
        }
        Axis selectedAxis = null;
        Vec3 selectedDirection = null;
        Projection nearest = null;
        for (Axis axis : Axis.values()) {
            Vec3 direction = axis.direction(state);
            Projection hit = project(camera, center, direction, true);
            if (hit != null && (nearest == null || hit.distanceSquared < nearest.distanceSquared)) {
                selectedAxis = axis;
                selectedDirection = direction;
                nearest = hit;
            }
        }
        if (selectedAxis == null) {
            return false;
        }
        var target = body.getInterpolation().target();
        Vec3 bodyPosition = target == null ? body.position() : target.position();
        double limit = selectedAxis.limit;
        double offset = Math.clamp(bodyPosition.subtract(BodyDisplayBlock.bodyOrigin(pos, state)).dot(selectedDirection), -limit, limit);
        drag = new Drag(selectedAxis, center, selectedDirection, nearest.alongAxis, offset, (float) offset);
        return true;
    }

    static void tick(Minecraft client, Entity body, BlockPos pos) {
        if (drag == null) {
            return;
        }
        if (!client.options.keyAttack.isDown() || !client.mouseHandler.isMouseGrabbed()
                || !ClientPlayNetworking.canSend(BodyDisplayPayload.TYPE)) {
            clear();
            return;
        }
        Projection projection = project(client.gameRenderer.mainCamera(), drag.center, drag.direction, false);
        if (projection == null) {
            return;
        }
        float limit = drag.axis.limit;
        float offset = (float) Math.clamp(drag.initialOffset + projection.alongAxis - drag.initialProjection, -limit, limit);
        if (offset == drag.lastSent || Math.abs(offset - drag.lastSent) < 0.001 && Math.abs(offset) < limit) {
            return;
        }
        ClientPlayNetworking.send(BodyDisplayPayload.move(pos, drag.axis.operation, body.getId(), offset));
        drag = new Drag(drag.axis, drag.center, drag.direction, drag.initialProjection, drag.initialOffset, offset);
    }

    private static Projection project(Camera camera, Vec3 center, Vec3 axis, boolean picking) {
        Vec3 eye = camera.position();
        Vec3 ray = new Vec3(camera.forwardVector());
        Vec3 relative = eye.subtract(center);
        double alignment = axis.dot(ray);
        double denominator = 1 - alignment * alignment;
        if (denominator < 0.01) {
            return null;
        }
        double along = (relative.dot(axis) - alignment * relative.dot(ray)) / denominator;
        double rayDistance = alignment * along - relative.dot(ray);
        if (picking && Math.abs(along) > LENGTH + PICK_RADIUS) {
            return null;
        }
        double distance = relative.add(ray.scale(rayDistance)).subtract(axis.scale(along)).lengthSqr();
        return rayDistance < 0 || picking && (rayDistance > REACH || distance > PICK_RADIUS * PICK_RADIUS)
                ? null : new Projection(along, distance);
    }

    static boolean active() {
        return drag != null;
    }

    static void clear() {
        drag = null;
    }
}
