package icu.tatyrealms.tatykefei.client.mixin.compat;

import icu.tatyrealms.tatykefei.client.compat.needsofnature.NeedsOfNatureClientCompat;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.nonid.client.NonPlayerInteractionClient$AnimationSelectionScreen", remap = false)
public abstract class NeedsOfNatureSelectionMixin {
    @Shadow @Final
    private UUID requestId;

    @Inject(method = "chooseGroup", at = @At("HEAD"), cancellable = true, require = 0)
    private void tsf$confirmAnimationRole(@Coerce Object group, CallbackInfo callback) {
        Screen selection = NeedsOfNatureClientCompat.roleSelection((Screen) (Object) this, requestId, group);
        if (selection != null) {
            Minecraft.getInstance().gui.setScreen(selection);
            callback.cancel();
        }
    }
}
