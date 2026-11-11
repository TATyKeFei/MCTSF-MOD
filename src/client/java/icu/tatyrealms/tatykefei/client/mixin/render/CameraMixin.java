package icu.tatyrealms.tatykefei.client.mixin.render;

import icu.tatyrealms.tatykefei.client.compat.needsofnature.NeedsOfNatureObserver;
import icu.tatyrealms.tatykefei.client.observer.ObserverView;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void tsf$observerAnimationPosition(float partialTick, CallbackInfo callback) {
        Camera camera = (Camera) (Object) this;
        var player = ObserverView.target();
        if (player != null && camera.entity() == player && !camera.isDetached()) {
            Vec3 position = NeedsOfNatureObserver.cameraPosition(player, partialTick);
            if (position != null) {
                setPosition(position);
            }
        }
    }
}
