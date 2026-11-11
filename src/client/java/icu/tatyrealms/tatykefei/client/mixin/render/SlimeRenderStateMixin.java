package icu.tatyrealms.tatykefei.client.mixin.render;

import icu.tatyrealms.tatykefei.client.render.SlimeColorRenderState;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SlimeRenderState.class)
public abstract class SlimeRenderStateMixin implements SlimeColorRenderState {
    @Unique
    private Integer tsf$color;

    @Override
    public Integer tsf$color() {
        return tsf$color;
    }

    @Override
    public void tsf$setColor(Integer color) {
        tsf$color = color;
    }
}
