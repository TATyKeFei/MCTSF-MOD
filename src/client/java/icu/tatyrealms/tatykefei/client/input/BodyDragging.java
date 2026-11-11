package icu.tatyrealms.tatykefei.client.input;

import icu.tatyrealms.tatykefei.client.render.BodyRigRendering;
import icu.tatyrealms.tatykefei.network.BodyDragPayload;
import icu.tatyrealms.tatykefei.body.BodyManager;
import icu.tatyrealms.tatykefei.body.BodyRigPose;
import icu.tatyrealms.tatykefei.state.Form;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class BodyDragging {
    private static Entity grabbed;
    private static float distance;

    private BodyDragging() {
    }

    private static boolean allowed(Minecraft client) {
        return client.player != null && client.level != null && client.gui.screen() == null
                && client.player.isAlive() && !client.player.isSpectator() && client.player.isShiftKeyDown()
                && client.player.getMainHandItem().isEmpty() && BodyManager.form(client.player) == Form.HUMAN
                && !BodyManager.hypnotized(client.player) && ClientPlayNetworking.canSend(BodyDragPayload.TYPE);
    }

    public static boolean start(Minecraft client) {
        if (grabbed != null) {
            return true;
        }
        if (!allowed(client)) {
            return false;
        }
        Vec3 eye = client.player.getEyePosition();
        Vec3 end = eye.add(client.player.getLookAngle().scale(5));
        HitResult obstruction = client.level.clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
        double nearest = obstruction.getType() == HitResult.Type.MISS ? 5 : eye.distanceTo(obstruction.getLocation());
        Entity selected = null;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!BodyManager.emptyBody(entity) || BodyManager.foldedSkin(entity) || entity.distanceToSqr(eye) > 64) {
                continue;
            }
            BodyRigPose pose = BodyRigRendering.frame(entity, 1);
            if (pose == null || !pose.mounted) {
                continue;
            }
            for (int part = 0; part < 6; part++) {
                double hit = pose.part(part).hit(eye.subtract(entity.position()), end.subtract(entity.position()));
                if (hit < nearest) {
                    nearest = hit;
                    selected = entity;
                }
            }
        }
        if (selected == null) {
            return false;
        }
        grabbed = selected;
        distance = (float) Math.clamp(nearest, 1, 5);
        send(BodyDragPayload.START);
        return true;
    }

    public static void tick(Minecraft client) {
        if (grabbed == null) {
            return;
        }
        if (!allowed(client) || !client.options.keyUse.isDown() || grabbed.isRemoved()
                || grabbed.level() != client.level || client.player.distanceToSqr(grabbed) > 49) {
            stop();
        } else {
            send(BodyDragPayload.UPDATE);
        }
    }

    public static boolean scroll(double amount) {
        if (grabbed == null) {
            return false;
        }
        distance = (float) Math.clamp(distance + amount * 0.15, 1, 5);
        send(BodyDragPayload.UPDATE);
        return true;
    }

    private static void send(int operation) {
        if (grabbed != null && ClientPlayNetworking.canSend(BodyDragPayload.TYPE)) {
            ClientPlayNetworking.send(new BodyDragPayload(grabbed.getId(), operation, distance));
        }
    }

    public static void stop() {
        send(BodyDragPayload.STOP);
        grabbed = null;
    }
}
