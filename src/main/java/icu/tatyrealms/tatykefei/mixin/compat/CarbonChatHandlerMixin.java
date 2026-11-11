package icu.tatyrealms.tatykefei.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import icu.tatyrealms.tatykefei.compat.carbonchat.CarbonChatCompat;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.draycia.carbon.fabric.listeners.FabricChatHandler", remap = false)
public abstract class CarbonChatHandlerMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void tsf$registerChatHandler(CallbackInfo callback) {
        CarbonChatCompat.registerChatHandler(this);
    }

    @WrapOperation(method = "allowChatMessage", at = @At(value = "INVOKE",
            target = "Lnet/draycia/carbon/common/event/events/CarbonChatEventImpl;renderFor(Lnet/kyori/adventure/audience/Audience;)Lnet/kyori/adventure/text/Component;"))
    private Component tsf$sendConsole(@Coerce Object event, Audience recipient, Operation<Component> original) {
        Component message = original.call(event, recipient);
        CarbonChatCompat.deliverConsoleMessage(recipient, message);
        return message;
    }
}
