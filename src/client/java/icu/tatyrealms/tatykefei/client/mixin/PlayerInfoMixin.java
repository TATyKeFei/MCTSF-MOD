package icu.tatyrealms.tatykefei.client.mixin;

import icu.tatyrealms.tatykefei.client.TSFClient;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void tsf$tabSkin(CallbackInfoReturnable<PlayerSkin> callback) {
        PlayerSkin skin = TSFClient.skin(((PlayerInfo) (Object) this).getProfile().id());
        if (skin != null) {
            callback.setReturnValue(skin);
        }
    }
}
