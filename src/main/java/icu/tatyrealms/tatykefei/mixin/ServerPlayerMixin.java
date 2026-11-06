package icu.tatyrealms.tatykefei.mixin;

import icu.tatyrealms.tatykefei.state.BodyManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
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
