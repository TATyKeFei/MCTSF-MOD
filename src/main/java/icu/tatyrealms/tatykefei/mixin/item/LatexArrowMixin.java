package icu.tatyrealms.tatykefei.mixin.item;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import icu.tatyrealms.tatykefei.item.LatexPotions;
import java.util.function.Consumer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Arrow.class)
public abstract class LatexArrowMixin {
    @WrapOperation(method = "doPostHurtEffects", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/alchemy/PotionContents;forEachEffect(Ljava/util/function/Consumer;F)V"))
    private void tsf$applyLatex(PotionContents contents, Consumer<MobEffectInstance> consumer, float scale, Operation<Void> original,
            @Local(argsOnly = true) LivingEntity target) {
        ItemStack arrow = ((Arrow) (Object) this).getPickupItemStackOrigin();
        original.call(contents, (Consumer<MobEffectInstance>) effect -> {
            if (effect.getEffect().equals(LatexPotions.EFFECT) && LatexPotions.source(arrow).isPresent()) {
                LatexPotions.apply(target, arrow, effect);
            } else {
                consumer.accept(effect);
            }
        }, scale);
    }
}
