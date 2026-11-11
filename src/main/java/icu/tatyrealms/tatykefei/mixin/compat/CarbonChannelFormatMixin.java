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

@Pseudo
@Mixin(targets = "net.draycia.carbon.common.channels.ConfigChatChannel", remap = false)
public abstract class CarbonChannelFormatMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/draycia/carbon/api/users/CarbonPlayer;username()Ljava/lang/String;"))
    private String tsf$bodyUsername(@Coerce Object sender, Operation<String> original,
                                    @Coerce Object source, Audience recipient, Component message, Component originalMessage) {
        // Substitute only the formatted name; Carbon still uses the real account for permissions and routing.
        return CarbonChatCompat.formattedUsername(sender, recipient, original.call(sender));
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/draycia/carbon/api/users/CarbonPlayer;displayName()Lnet/kyori/adventure/text/Component;"))
    private Component tsf$consoleDisplayName(@Coerce Object sender, Operation<Component> original,
                                            @Coerce Object source, Audience recipient, Component message, Component originalMessage) {
        return CarbonChatCompat.formattedDisplayName(sender, recipient, original.call(sender));
    }
}
