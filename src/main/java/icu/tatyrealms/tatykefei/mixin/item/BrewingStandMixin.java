package icu.tatyrealms.tatykefei.mixin.item;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import icu.tatyrealms.tatykefei.item.LatexPotions;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandMixin {
    @Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
    private void tsf$sourceIngredient(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (slot == 3 && stack.is(Items.POTION)) {
            callback.setReturnValue(LatexPotions.reagentSource(stack).isPresent());
        }
    }

    @WrapOperation(method = "doBrew", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/Item;getCraftingRemainder()Lnet/minecraft/world/item/ItemStackTemplate;"))
    private static ItemStackTemplate tsf$returnSourceBottle(Item item, Operation<ItemStackTemplate> original,
            @Local ItemStack ingredient) {
        return LatexPotions.reagentSource(ingredient).isPresent() ? new ItemStackTemplate(Items.GLASS_BOTTLE) : original.call(item);
    }
}
