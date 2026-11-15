package icu.tatyrealms.tatykefei.mixin.item;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import icu.tatyrealms.tatykefei.item.LatexPotions;
import icu.tatyrealms.tatykefei.item.TSFItems;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AreaEffectCloud.class)
public abstract class LatexAreaEffectCloudMixin {
    @Unique
    private ItemStack tsf$latexPotion = ItemStack.EMPTY;

    @Inject(method = "applyImplicitComponents", at = @At("TAIL"))
    private void tsf$copySource(DataComponentGetter components, CallbackInfo callback) {
        // 原版药水云只保留效果，这里额外保留供体档案。
        tsf$latexPotion = ItemStack.EMPTY;
        LatexPotions.sourceData(components).ifPresent(source -> {
            tsf$latexPotion = new ItemStack(TSFItems.LATEX_LINGERING_POTION);
            tsf$latexPotion.set(DataComponents.CUSTOM_DATA, components.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY));
            var profile = components.get(DataComponents.PROFILE);
            if (profile != null) {
                tsf$latexPotion.set(DataComponents.PROFILE, profile);
            }
        });
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void tsf$loadSource(ValueInput input, CallbackInfo callback) {
        tsf$latexPotion = input.read("tsf:latex_potion", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void tsf$saveSource(ValueOutput output, CallbackInfo callback) {
        if (!tsf$latexPotion.isEmpty()) {
            output.store("tsf:latex_potion", ItemStack.CODEC, tsf$latexPotion);
        }
    }

    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean tsf$applyLatex(LivingEntity target, MobEffectInstance effect, Entity source, Operation<Boolean> original) {
        return effect.getEffect().equals(LatexPotions.EFFECT) && LatexPotions.source(tsf$latexPotion).isPresent()
                ? LatexPotions.apply(target, tsf$latexPotion, effect) : original.call(target, effect, source);
    }
}
