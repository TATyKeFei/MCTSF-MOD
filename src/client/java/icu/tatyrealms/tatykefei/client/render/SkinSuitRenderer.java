package icu.tatyrealms.tatykefei.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import java.util.function.Consumer;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.joml.Vector3fc;

public final class SkinSuitRenderer implements SpecialModelRenderer<PlayerSkinRenderCache.RenderInfo> {
    private final PlayerSkinRenderCache skins;
    private final PlayerModel wide;
    private final PlayerModel slim;
    private final boolean folded;

    private SkinSuitRenderer(BakingContext context, boolean folded) {
        skins = context.playerSkinRenderCache();
        this.folded = folded;
        wide = folded ? new FoldedPlayerModel(context, false) : new PlayerModel(context.entityModelSet().bakeLayer(ModelLayers.PLAYER), false);
        slim = folded ? new FoldedPlayerModel(context, true) : new PlayerModel(context.entityModelSet().bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    private void transform(PoseStack poses) {
        poses.translate(0.5F, folded ? 0.52F : 0.8F, 0.5F);
        float scale = folded ? 0.9F : 0.6F;
        poses.scale(scale, -scale, -scale);
    }

    @Override
    public void submit(PlayerSkinRenderCache.RenderInfo argument, PoseStack poses, SubmitNodeCollector collector,
                       int light, int overlay, boolean foil, int outline) {
        if (argument == null) {
            return;
        }
        AvatarRenderState state = new AvatarRenderState();
        state.skin = argument.playerSkin();
        PlayerModel model = state.skin.model() == PlayerModelType.SLIM ? slim : wide;
        poses.pushPose();
        transform(poses);
        collector.submitModel(model, state, poses, state.skin.body().texturePath(), light, overlay, outline);
        poses.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        PoseStack poses = new PoseStack();
        transform(poses);
        wide.setupAnim(new AvatarRenderState());
        wide.root().getExtentsForGui(poses, output);
    }

    @Override
    public PlayerSkinRenderCache.RenderInfo extractArgument(ItemStack stack) {
        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        return profile == null ? null : skins.getOrDefault(profile);
    }

    private static final class FoldedPlayerModel extends PlayerModel {
        private FoldedPlayerModel(BakingContext context, boolean slim) {
            super(context.entityModelSet().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
        }

        @Override
        public void setupAnim(AvatarRenderState state) {
            super.setupAnim(state);
            FoldedSkinPose.applyModel(this);
        }
    }

    public record Unbaked(boolean folded) implements SpecialModelRenderer.Unbaked<PlayerSkinRenderCache.RenderInfo> {
        public static final MapCodec<Unbaked> CODEC = Codec.BOOL.optionalFieldOf("folded", false).xmap(Unbaked::new, Unbaked::folded);

        @Override
        public SpecialModelRenderer<PlayerSkinRenderCache.RenderInfo> bake(BakingContext context) {
            return new SkinSuitRenderer(context, folded);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return CODEC;
        }
    }
}
