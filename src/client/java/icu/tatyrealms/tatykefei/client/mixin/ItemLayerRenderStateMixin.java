package icu.tatyrealms.tatykefei.client.mixin;

import icu.tatyrealms.tatykefei.client.render.SpiritItemRendering;
import icu.tatyrealms.tatykefei.client.render.SpiritItemRenderState;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public abstract class ItemLayerRenderStateMixin implements SpiritItemRenderState {
    @Shadow
    private ItemQuads quads;
    @Shadow
    private IntList tintLayers;

    @Override
    public void tsf$applySpirit() {
        if (quads.isEmpty()) {
            return;
        }
        SpiritItemRendering.Geometry spirit = SpiritItemRendering.geometry(quads);
        IntList tints = new IntArrayList();
        tints.add(SpiritItemRendering.color(-1));
        if (tintLayers != null) {
            for (int tint : tintLayers) {
                tints.add(SpiritItemRendering.color(tint));
            }
        }
        while (tints.size() < spirit.tintCount()) {
            tints.add(SpiritItemRendering.color(-1));
        }
        quads = spirit.quads();
        tintLayers = tints;
    }
}
