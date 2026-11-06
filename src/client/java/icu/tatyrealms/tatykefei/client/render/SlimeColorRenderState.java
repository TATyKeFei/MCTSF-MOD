package icu.tatyrealms.tatykefei.client.render;

import icu.tatyrealms.tatykefei.TSF;
import net.minecraft.resources.Identifier;

public interface SlimeColorRenderState {
    Identifier TEXTURE = TSF.id("textures/entity/slime.png");

    Integer tsf$color();

    void tsf$setColor(Integer color);
}
