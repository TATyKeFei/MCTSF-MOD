package icu.tatyrealms.tatykefei.client.mixin;

import icu.tatyrealms.tatykefei.client.ObserverView;
import icu.tatyrealms.tatykefei.client.HypnosisProgress;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void tsf$hypnosisProgress(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo callback) {
        if (!((Hud) (Object) this).isHidden()) {
            HypnosisProgress.extract(graphics, delta);
        }
    }

    @Shadow
    protected abstract void extractItemHotbar(GuiGraphicsExtractor graphics, DeltaTracker delta);

    @Shadow
    protected abstract void extractPlayerHealth(GuiGraphicsExtractor graphics);

    @Shadow
    protected abstract void extractVehicleHealth(GuiGraphicsExtractor graphics);

    @Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
    private void tsf$observerHotbar(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo callback) {
        if (ObserverView.target() != null) {
            ObserverView.updateInventory();
            extractItemHotbar(graphics, delta);
            extractPlayerHealth(graphics);
            extractVehicleHealth(graphics);
            ObserverView.extractExperience(graphics);
            callback.cancel();
        }
    }
}
