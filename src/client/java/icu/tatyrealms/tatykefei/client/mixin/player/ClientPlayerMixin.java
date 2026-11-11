package icu.tatyrealms.tatykefei.client.mixin.player;

import icu.tatyrealms.tatykefei.client.TSFClient;
import icu.tatyrealms.tatykefei.client.observer.ObserverView;
import icu.tatyrealms.tatykefei.client.observer.ObserverViewAngles;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class ClientPlayerMixin implements ObserverViewAngles {
    @Unique
    private boolean tsf$hasHandView;
    @Unique
    private float tsf$xBob;
    @Unique
    private float tsf$xBobO;
    @Unique
    private float tsf$yBob;
    @Unique
    private float tsf$yBobO;

    @Inject(method = "tick", at = @At("TAIL"))
    private void tsf$observerHandView(CallbackInfo callback) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        if (!(player instanceof RemotePlayer) || player != ObserverView.target()) {
            tsf$hasHandView = false;
            return;
        }
        if (!tsf$hasHandView) {
            tsf$xBobO = tsf$xBob = player.getViewXRot(1);
            tsf$yBobO = tsf$yBob = player.getViewYRot(1);
            tsf$hasHandView = true;
        } else {
            tsf$xBobO = tsf$xBob;
            tsf$yBobO = tsf$yBob;
            tsf$xBob += (player.getViewXRot(1) - tsf$xBob) * 0.5F;
            tsf$yBob += Mth.wrapDegrees(player.getViewYRot(1) - tsf$yBob) * 0.5F;
        }
    }

    @Override
    public void tsf$extractViewAngles(FirstPersonHandsAndItemsRenderState hands, float partialTick) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        hands.viewXRot = player.getViewXRot(partialTick);
        hands.viewYRot = player.getViewYRot(partialTick);
        hands.xBob = tsf$hasHandView ? Mth.lerp(partialTick, tsf$xBobO, tsf$xBob) : hands.viewXRot;
        hands.yBob = tsf$hasHandView
                ? hands.viewYRot + Mth.wrapDegrees(Mth.lerp(partialTick, tsf$yBobO, tsf$yBob) - hands.viewYRot) : hands.viewYRot;
    }

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
