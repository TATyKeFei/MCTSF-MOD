package icu.tatyrealms.tatykefei.client.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import icu.tatyrealms.tatykefei.client.compat.needsofnature.NeedsOfNatureObserver;
import icu.tatyrealms.tatykefei.client.TSFClient;
import icu.tatyrealms.tatykefei.client.observer.ObserverView;
import icu.tatyrealms.tatykefei.body.BodyManager;
import icu.tatyrealms.tatykefei.state.Form;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.PlayerRenderState;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    @ModifyExpressionValue(method = "extractVisibleEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;entity()Lnet/minecraft/world/entity/Entity;", ordinal = 0))
    private Entity tsf$showAnimatedObserverBody(Entity entity) {
        return entity instanceof AbstractClientPlayer player && player == ObserverView.target()
                && NeedsOfNatureObserver.rendersBody(player) ? null : entity;
    }

    @Inject(method = "extractPlayerState", at = @At("TAIL"))
    private void tsf$observerHands(Camera camera, DeltaTracker delta, float partialTick, PlayerRenderState state, CallbackInfo callback) {
        ObserverView.extractHands(state, partialTick);
    }

    @ModifyArg(method = "extractVisibleEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"), index = 0)
    private Entity tsf$slime(Entity entity) {
        if (entity instanceof AbstractClientPlayer player && BodyManager.form(player) == Form.SLIME) {
            return TSFClient.slime(player);
        }
        return entity;
    }
}
