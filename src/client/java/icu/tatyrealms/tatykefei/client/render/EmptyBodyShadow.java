package icu.tatyrealms.tatykefei.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.phys.Vec3;

/** A vanilla shadow projected from the rendered body rather than its entity origin. */
public record EmptyBodyShadow(Vec3 offset, float radius) {
    public static EmptyBodyShadow from(AvatarRenderState state) {
        EmptyBodyRenderState body = (EmptyBodyRenderState) state;
        if (!body.tsf$isEmptyBody() || body.tsf$isFoldedSkin() || state.isInvisible || state.distanceToCameraSq >= 256
                || !Minecraft.getInstance().options.entityShadows().get()) {
            return null;
        }
        if (body.tsf$rig() == null) {
            return new EmptyBodyShadow(Vec3.ZERO, 0.95F * state.scale);
        }
        var bounds = body.tsf$rig().bounds();
        // Keep the projection just above contact so small physics penetrations do not hide it.
        Vec3 offset = new Vec3((bounds.minX + bounds.maxX) * 0.5, bounds.minY + 0.1,
                (bounds.minZ + bounds.maxZ) * 0.5).scale(state.scale);
        float radius = (float) (Math.max(bounds.getXsize(), bounds.getZsize()) * 0.5) * state.scale;
        return new EmptyBodyShadow(offset, radius);
    }
}
