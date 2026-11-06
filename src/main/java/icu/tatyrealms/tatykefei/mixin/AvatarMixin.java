package icu.tatyrealms.tatykefei.mixin;

import icu.tatyrealms.tatykefei.state.Form;
import icu.tatyrealms.tatykefei.state.FormHolder;
import icu.tatyrealms.tatykefei.state.BodyManager;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Avatar.class)
public abstract class AvatarMixin {
    @Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
    private void tsf$slimeDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> callback) {
        if (this instanceof FormHolder holder && holder.tsf$getForm() == Form.SLIME) {
            callback.setReturnValue(pose == Pose.CROUCHING
                    ? BodyManager.CROUCHING_SLIME_DIMENSIONS : BodyManager.SLIME_DIMENSIONS);
        }
    }
}
