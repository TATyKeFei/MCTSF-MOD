package icu.tatyrealms.tatykefei.client.mixin;

import icu.tatyrealms.tatykefei.state.BodyManager;
import icu.tatyrealms.tatykefei.state.Form;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void tsf$spiritFlight(CallbackInfo callback) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (BodyManager.form(player) == Form.SPIRIT) {
            player.noPhysics = true;
            player.getAbilities().flying = true;
        }
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isSpectator()Z"))
    private boolean tsf$keepSpiritFlying(MultiPlayerGameMode gameMode) {
        return BodyManager.form((LocalPlayer) (Object) this) == Form.SPIRIT || gameMode.isSpectator();
    }
}
