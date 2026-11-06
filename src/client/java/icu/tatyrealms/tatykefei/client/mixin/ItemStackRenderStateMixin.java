package icu.tatyrealms.tatykefei.client.mixin;

import icu.tatyrealms.tatykefei.client.render.SpiritItemRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateMixin implements SpiritItemRenderState {
    @Shadow
    private int activeLayerCount;
    @Shadow
    private ItemStackRenderState.LayerRenderState[] layers;

    @Override
    public void tsf$applySpirit() {
        for (int layer = 0; layer < activeLayerCount; layer++) {
            ((SpiritItemRenderState) layers[layer]).tsf$applySpirit();
        }
    }
}
