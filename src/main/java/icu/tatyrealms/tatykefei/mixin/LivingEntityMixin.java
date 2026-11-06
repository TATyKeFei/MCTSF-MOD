package icu.tatyrealms.tatykefei.mixin;

import icu.tatyrealms.tatykefei.state.EmptyBodyHolder;
import icu.tatyrealms.tatykefei.state.BodyManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void tsf$bodyPushable(CallbackInfoReturnable<Boolean> callback) {
        if (BodyManager.emptyBody((Entity) (Object) this)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void tsf$bodyCannotPush(CallbackInfo callback) {
        if (BodyManager.emptyBody((Entity) (Object) this)) {
            callback.cancel();
        }
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void tsf$noBodyPush(Entity entity, CallbackInfo callback) {
        Entity self = (Entity) (Object) this;
        if (BodyManager.emptyBody(self) || BodyManager.emptyBody(entity)) {
            Entity body = BodyManager.emptyBody(self) ? self : entity;
            Entity source = body == self ? entity : self;
            if (!body.noPhysics && !source.noPhysics && !source.isSpectator()) {
                double offsetX = body.getX() - source.getX();
                double offsetZ = body.getZ() - source.getZ();
                double distance = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
                if (distance > 0.01) {
                    body.push(offsetX / distance * 0.05, 0, offsetZ / distance * 0.05);
                }
            }
            callback.cancel();
        }
    }

    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void tsf$emptyBodyDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> callback) {
        if (this instanceof EmptyBodyHolder holder && holder.tsf$isEmptyBody()) {
            callback.setReturnValue(holder.tsf$isFoldedSkin() ? BodyManager.SKIN_DIMENSIONS : BodyManager.BODY_DIMENSIONS);
        }
    }
}
