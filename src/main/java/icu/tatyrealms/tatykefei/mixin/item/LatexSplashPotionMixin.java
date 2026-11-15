package icu.tatyrealms.tatykefei.mixin.item;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import icu.tatyrealms.tatykefei.item.LatexPotions;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ThrownSplashPotion.class)
public abstract class LatexSplashPotionMixin {
    @WrapOperation(method = "onHitAsPotion", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean tsf$applyLatex(LivingEntity target, MobEffectInstance effect, Entity source, Operation<Boolean> original,
            @Local(argsOnly = true) ItemStack potion) {
        return effect.getEffect().equals(LatexPotions.EFFECT) && LatexPotions.source(potion).isPresent()
                ? LatexPotions.apply(target, potion, effect) : original.call(target, effect, source);
    }
}
