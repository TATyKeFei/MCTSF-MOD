package icu.tatyrealms.tatykefei.mixin;

import icu.tatyrealms.tatykefei.state.EmptyBodyHolder;
import icu.tatyrealms.tatykefei.state.BodyManager;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "onSyncedDataUpdated(Lnet/minecraft/network/syncher/EntityDataAccessor;)V", at = @At("TAIL"))
    private void tsf$bodyDimensions(EntityDataAccessor<?> accessor, CallbackInfo callback) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof EmptyBodyHolder holder && holder.tsf$isEmptyBody()) {
            var dimensions = holder.tsf$isFoldedSkin() ? BodyManager.SKIN_DIMENSIONS : BodyManager.BODY_DIMENSIONS;
            if (Math.abs(entity.getBbWidth() - dimensions.width()) > 0.01F
                    || Math.abs(entity.getBbHeight() - dimensions.height()) > 0.01F) {
                entity.refreshDimensions();
            }
        }
    }
}
