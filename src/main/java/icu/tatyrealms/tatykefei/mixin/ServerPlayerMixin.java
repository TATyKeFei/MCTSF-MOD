package icu.tatyrealms.tatykefei.mixin;

import icu.tatyrealms.tatykefei.state.BodyManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void tsf$discardSpiritSpear(ItemStack stack, boolean thrownFromHand, Prediction prediction,
                                       CallbackInfoReturnable<ItemEntity> callback) {
        if (BodyManager.spiritSpear(stack)) {
            callback.setReturnValue(null);
        }
    }

    @Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
    private void tsf$tabIdentity(CallbackInfoReturnable<Component> callback) {
        if (BodyManager.get() != null) {
            Component name = BodyManager.get().assumedName((ServerPlayer) (Object) this);
            if (name != null) {
                callback.setReturnValue(name);
            }
        }
    }
}
