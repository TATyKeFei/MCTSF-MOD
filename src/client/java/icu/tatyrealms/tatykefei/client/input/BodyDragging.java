package icu.tatyrealms.tatykefei.client.input;

import icu.tatyrealms.tatykefei.client.render.BodyRigRendering;
import icu.tatyrealms.tatykefei.network.BodyDragPayload;
import icu.tatyrealms.tatykefei.network.BodyDisplayPayload;
import icu.tatyrealms.tatykefei.state.EmptyBodyHolder;
import icu.tatyrealms.tatykefei.body.BodyManager;
import icu.tatyrealms.tatykefei.body.BodyRigPose;
import icu.tatyrealms.tatykefei.state.Form;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class BodyDragging {
    private static Entity grabbed;
    private static Entity editing;
    private static BodyDisplayPayload display;
    private static float distance;

    private BodyDragging() {
    }

    private static boolean allowed(Minecraft client) {
        return client.player != null && client.level != null && client.gui.screen() == null
                && editing != null && editing instanceof EmptyBodyHolder holder && holder.tsf$poseable()
                && holder.tsf$isMounted() && client.player.isAlive() && !client.player.isSpectator()
                && client.player.getMainHandItem().isEmpty() && BodyManager.form(client.player) == Form.HUMAN
                && !BodyManager.hypnotized(client.player) && ClientPlayNetworking.canSend(BodyDragPayload.TYPE);
    }

    public static void edit(Minecraft client, BodyDisplayPayload payload) {
        clear();
        editing = client.level == null ? null : client.level.getEntity(payload.entity());
        display = payload;
        if (client.player != null && editing != null) {
            client.gui.hud.setOverlayMessage(Component.translatable("message.tsf.body_display_editing"), false);
        }
    }

    public static boolean finishEditing() {
        if (editing == null) {
            return false;
        }
        stop();
        if (ClientPlayNetworking.canSend(BodyDisplayPayload.TYPE)) {
            ClientPlayNetworking.send(new BodyDisplayPayload(display.pos(), BodyDisplayPayload.STOP_EDIT,
                    false, false, true, false, -1));
        }
        clear();
        return true;
    }

    public static void clear() {
        grabbed = null;
        editing = null;
        display = null;
    }

    public static boolean start(Minecraft client) {
        if (grabbed != null) {
            return true;
        }
        if (!allowed(client)) {
            return editing != null;
        }
        Vec3 eye = client.player.getEyePosition();
        Vec3 end = eye.add(client.player.getLookAngle().scale(5));
        HitResult obstruction = client.level.clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
        double nearest = obstruction.getType() == HitResult.Type.MISS ? 5 : eye.distanceTo(obstruction.getLocation());
        Entity selected = null;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity != editing || !BodyManager.emptyBody(entity) || BodyManager.foldedSkin(entity) || entity.distanceToSqr(eye) > 64) {
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
            return true;
        }
        grabbed = selected;
        distance = (float) Math.clamp(nearest, 1, 5);
        send(BodyDragPayload.START);
        return true;
    }

    public static void tick(Minecraft client) {
        if (editing != null && (!allowed(client) || editing.isRemoved() || editing.level() != client.level
                || client.player.distanceToSqr(editing) > 49)) {
            finishEditing();
        }
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
