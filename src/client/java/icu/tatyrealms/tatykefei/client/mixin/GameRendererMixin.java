package icu.tatyrealms.tatykefei.client.mixin;

import icu.tatyrealms.tatykefei.client.ObserverView;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Redirect(method = "renderItemInHand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;"))
    private GameType tsf$observerHands(MultiPlayerGameMode gameMode) {
        return ObserverView.target() != null ? GameType.SURVIVAL : gameMode.getPlayerMode();
    }
}
