package icu.tatyrealms.tatykefei.client.mixin;

import icu.tatyrealms.tatykefei.client.TSFClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class ClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void tsf$appearance(CallbackInfoReturnable<PlayerSkin> callback) {
        PlayerSkin skin = TSFClient.skin(((AbstractClientPlayer) (Object) this).getUUID());
        if (skin != null) {
            callback.setReturnValue(skin);
        }
    }
}
