package icu.tatyrealms.tatykefei.mixin.compat;

import icu.tatyrealms.tatykefei.compat.needsofnature.NatureLiquidSourceHolder;
import icu.tatyrealms.tatykefei.compat.needsofnature.NatureLiquidSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "com.nonid.NeedsOfNature$LiquidGainProfile", remap = false)
public abstract class NeedsOfNatureGainSourcesMixin implements NatureLiquidSourceHolder {
    @Unique
    private NatureLiquidSources tsf$liquidSources = NatureLiquidSources.UNKNOWN;

    @Override
    public NatureLiquidSources tsf$liquidSources() {
        return tsf$liquidSources;
    }

    @Override
    public void tsf$setLiquidSources(NatureLiquidSources sources) {
        tsf$liquidSources = sources;
    }
}
