package icu.tatyrealms.tatykefei.client.render;

import icu.tatyrealms.tatykefei.body.BodyRigPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class BodyPreview {
    private BodyPreview() {
    }

    public static void extractForColumn(GuiGraphicsExtractor graphics, int entityId, int width, int height,
            int columnLeft, float mouseX, float mouseY) {
        int previewWidth = Math.min(360, width / 3);
        int right = columnLeft - 32;
        int x0 = Math.max(8, right - previewWidth);
        int x1 = Math.max(x0 + 80, right);
        extract(graphics, entityId, x0, height / 2 - 90, x1, height / 2 + 90, mouseX, mouseY);
    }

    public static void extract(GuiGraphicsExtractor graphics, int entityId, int x0, int y0, int x1, int y1,
            float mouseX, float mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || entityId < 0 || !(client.level.getEntity(entityId) instanceof LivingEntity entity)) {
            return;
        }
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        EntityRenderer<? super LivingEntity, ?> renderer = dispatcher.getRenderer(entity);
        EntityRenderState renderState = renderer.createRenderState(entity, 1.0F);
        renderState.shadowPieces.clear();
        renderState.outlineColor = 0;
        renderState.nameTag = null;
        renderState.scoreText = null;
        float centerX = (x0 + x1) / 2.0F;
        float centerY = (y0 + y1) / 2.0F;
        float xAngle = (float) Math.atan((centerX - mouseX) / 40.0F);
        float yAngle = (float) Math.atan((centerY - mouseY) / 40.0F);
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf xRotation = new Quaternionf().rotateX(yAngle * 20.0F * (float) (Math.PI / 180.0));
        rotation.mul(xRotation);
        if (renderState instanceof LivingEntityRenderState living) {
            living.bodyRot = 180.0F + xAngle * 20.0F;
            living.yRot = xAngle * 20.0F;
            living.xRot = living.pose == Pose.FALL_FLYING ? 0.0F : -yAngle * 20.0F;
            living.boundingBoxWidth /= living.scale;
            living.boundingBoxHeight /= living.scale;
            living.scale = 1.0F;
        }
        if (renderState instanceof PreviewRenderState preview) {
            // 骨架坐标已是世界朝向，按骨架背向补偿，保证预览始终展示肉体正面
            float baseYaw = 180.0F - entity.getYRot();
            if (renderState instanceof EmptyBodyRenderState body && body.tsf$rig() instanceof BodyRigPose pose) {
                Vec3 back = pose.basis().forward();
                baseYaw = (float) Math.toDegrees(Math.atan2(back.x, back.z));
            }
            preview.tsf$setPreviewYaw(baseYaw + xAngle * 20.0F);
        }
        int size = Math.max(20, (y1 - y0) * 3 / 7);
        // 展示架肉体使用 SLEEPING 姿态，包围盒高度只有 0.2，需要按站立高度居中
        float previewHeight = Math.max(renderState.boundingBoxHeight, 1.8F);
        Vector3f translation = new Vector3f(0.0F, previewHeight / 2.0F + 0.0625F, 0.0F);
        graphics.entity(renderState, size, translation, rotation, xRotation, x0, y0, x1, y1);
    }
}
