package icu.tatyrealms.tatykefei.mixin.item;

import icu.tatyrealms.tatykefei.item.LatexPotions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.inventory.BrewingStandMenu$IngredientsSlot")
public abstract class BrewingIngredientSlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void tsf$sourceIngredient(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (stack.is(Items.POTION)) {
            // 普通药水仍进入下方瓶槽，只有单一玩家来源瓶进入材料槽。
            callback.setReturnValue(LatexPotions.reagentSource(stack).isPresent());
        }
    }
}
