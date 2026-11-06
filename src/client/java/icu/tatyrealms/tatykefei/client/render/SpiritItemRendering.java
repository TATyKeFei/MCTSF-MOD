package icu.tatyrealms.tatykefei.client.render;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

public final class SpiritItemRendering {
    private static final int COLOR = 0x26FFFFFF;
    private static final Map<ItemQuads, Geometry> GEOMETRY = new WeakHashMap<>();

    private SpiritItemRendering() {
    }

    public static Geometry geometry(ItemQuads original) {
        return GEOMETRY.computeIfAbsent(original, quads -> {
            List<BakedQuad> translucent = quads.all().stream().map(quad -> {
                BakedQuad.MaterialInfo material = quad.materialInfo();
                Identifier atlas = material.sprite().atlasLocation();
                BakedQuad.MaterialInfo spirit = new BakedQuad.MaterialInfo(material.sprite(), ChunkSectionLayer.TRANSLUCENT,
                        RenderTypes.itemTranslucent(atlas), RenderTypes.itemTranslucentGlint(atlas),
                        RenderTypes.itemTranslucentGlintSpecial(atlas), Math.max(0, material.tintIndex() + 1),
                        material.shadeDirectionOverride(), material.lightEmission());
                return new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
                        quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(), quad.direction(), spirit);
            }).toList();
            int tintCount = translucent.stream().mapToInt(quad -> quad.materialInfo().tintIndex()).max().orElse(0) + 1;
            return new Geometry(new ItemQuads(translucent, List.of(), translucent), tintCount);
        });
    }

    public static int color(int original) {
        return ARGB.multiply(original, COLOR);
    }

    public record Geometry(ItemQuads quads, int tintCount) {
    }
}
