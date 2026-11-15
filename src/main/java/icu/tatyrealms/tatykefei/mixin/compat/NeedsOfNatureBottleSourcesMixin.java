package icu.tatyrealms.tatykefei.mixin.compat;

import icu.tatyrealms.tatykefei.compat.needsofnature.NeedsOfNatureSources;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.nonid.NonItemSystem", remap = false)
public abstract class NeedsOfNatureBottleSourcesMixin {
    @Inject(method = "createBottleStackForTank", at = @At("RETURN"), require = 0)
    private static void tsf$bottleSources(@Coerce Object tank, CallbackInfoReturnable<ItemStack> callback) {
        NeedsOfNatureSources.labelBottle(tank, callback.getReturnValue());
    }
}
