package icu.tatyrealms.tatykefei.mixin.item;

import icu.tatyrealms.tatykefei.item.LatexPotions;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PotionContents.class)
public abstract class LatexPotionTooltipMixin {
    @Inject(method = "getPotionDescription", at = @At("HEAD"), cancellable = true)
    private static void tsf$latexLevel(Holder<MobEffect> effect, int amplifier, CallbackInfoReturnable<MutableComponent> callback) {
        if (effect.equals(LatexPotions.EFFECT)) {
            callback.setReturnValue(LatexPotions.effectName(amplifier).copy());
        }
    }
}
