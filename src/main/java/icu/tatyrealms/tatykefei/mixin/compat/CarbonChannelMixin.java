package icu.tatyrealms.tatykefei.mixin.compat;

import icu.tatyrealms.tatykefei.compat.carbonchat.CarbonChatCompat;
import net.kyori.adventure.text.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.draycia.carbon.common.listeners.ChatListenerInternal", remap = false)
public abstract class CarbonChannelMixin {
    @Inject(method = "prepareAndEmitChatEvent(Lnet/draycia/carbon/api/users/CarbonPlayer;Lnet/kyori/adventure/text/Component;Lnet/kyori/adventure/chat/SignedMessage;Lnet/draycia/carbon/api/channels/ChatChannel;)Lnet/draycia/carbon/common/event/events/CarbonChatEventImpl;",
            at = @At("HEAD"), cancellable = true)
    private void tsf$channelSpeech(@Coerce Object sender, Component message, @Coerce Object signed,
                                   @Coerce Object channel, CallbackInfoReturnable<Object> callback) {
        if (!CarbonChatCompat.allowChannelMessage(sender, message)) {
            callback.setReturnValue(null);
        }
    }
}
