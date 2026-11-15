package icu.tatyrealms.tatykefei.client.mixin.render;

import icu.tatyrealms.tatykefei.item.LatexPotions;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EffectsInInventory.class)
public abstract class LatexEffectNameMixin {
    @Inject(method = "getEffectName", at = @At("HEAD"), cancellable = true)
    private void tsf$latexLevel(MobEffectInstance effect, CallbackInfoReturnable<Component> callback) {
        if (effect.getEffect().equals(LatexPotions.EFFECT)) {
            callback.setReturnValue(LatexPotions.effectName(effect.getAmplifier()));
        }
    }
}
