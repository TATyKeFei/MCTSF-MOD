package icu.tatyrealms.tatykefei.mixin.compat;

import icu.tatyrealms.tatykefei.compat.carbonchat.CarbonChatCompat;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.draycia.carbon.common.users.WrappedCarbonPlayer", remap = false)
public abstract class CarbonPlayerMixin {
    @Shadow
    public abstract UUID uuid();

    @Inject(method = "displayName", at = @At("HEAD"), cancellable = true)
    private void tsf$bodyName(CallbackInfoReturnable<Component> callback) {
        Component name = CarbonChatCompat.assumedName(uuid());
        if (name != null) {
            callback.setReturnValue(name);
        }
    }
}
