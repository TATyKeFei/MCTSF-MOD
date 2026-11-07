package icu.tatyrealms.tatykefei.client.mixin;

import icu.tatyrealms.tatykefei.client.TSFClient;
import icu.tatyrealms.tatykefei.client.ObserverView;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class ClientPlayerMixin {
    @Inject(method = "updateBob", at = @At("HEAD"))
    private void tsf$observerWalkDistance(CallbackInfo callback) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        if (player instanceof RemotePlayer && player == ObserverView.target() && !player.isPassenger()) {
            float deltaX = (float) (player.getX() - player.xo);
            float deltaZ = (float) (player.getZ() - player.zo);
            player.avatarState().addWalkDistance(Mth.length(deltaX, deltaZ) * 0.6F);
        }
    }

    @Redirect(method = "updateBob", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/AbstractClientPlayer;getDeltaMovement()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 tsf$observerMovement(AbstractClientPlayer player) {
        return player instanceof RemotePlayer && player == ObserverView.target()
                ? player.position().subtract(player.xo, player.yo, player.zo) : player.getDeltaMovement();
    }

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void tsf$appearance(CallbackInfoReturnable<PlayerSkin> callback) {
        PlayerSkin skin = TSFClient.skin(((AbstractClientPlayer) (Object) this).getUUID());
        if (skin != null) {
            callback.setReturnValue(skin);
        }
    }
}
