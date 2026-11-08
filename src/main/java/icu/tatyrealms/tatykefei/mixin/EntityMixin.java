package icu.tatyrealms.tatykefei.mixin;

import icu.tatyrealms.tatykefei.state.EmptyBodyHolder;
import icu.tatyrealms.tatykefei.state.BodyManager;
import icu.tatyrealms.tatykefei.state.BodyPhysics;
import icu.tatyrealms.tatykefei.state.Form;
import icu.tatyrealms.tatykefei.state.HypnosisRestrictions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Unique
    private Vec3 tsf$lastClearBodyPosition;

    @Inject(method = "move", at = @At("HEAD"))
    private void tsf$bodyBlockCollision(MoverType mover, Vec3 movement, CallbackInfo callback) {
        Entity entity = (Entity) (Object) this;
        if (!BodyManager.emptyBody(entity) || entity.level().isClientSide()) {
            return;
        }
        entity.noPhysics = false;
        if (BodyPhysics.isClear(entity.level(), entity.getBoundingBox())) {
            tsf$lastClearBodyPosition = entity.position();
            return;
        }
        var dimensions = entity.getDimensions(entity.getPose());
        Vec3 destination = tsf$lastClearBodyPosition;
        if (destination == null || destination.distanceToSqr(entity.position()) > 16
                || !BodyPhysics.isClear(entity.level(), dimensions.makeBoundingBox(destination))) {
            destination = BodyPhysics.findPosition(entity.level(), entity.position(), dimensions);
        }
        if (destination != null) {
            entity.setPos(destination);
            entity.setDeltaMovement(Vec3.ZERO);
            tsf$lastClearBodyPosition = destination;
        }
    }

    @Inject(method = "isInvulnerable", at = @At("HEAD"), cancellable = true)
    private void tsf$spiritInvulnerable(CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof Player player && BodyManager.form(player) == Form.SPIRIT) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void tsf$spiritCannotPush(Entity entity, CallbackInfo callback) {
        if ((Object) this instanceof Player player && BodyManager.form(player) == Form.SPIRIT
                || entity instanceof Player other && BodyManager.form(other) == Form.SPIRIT) {
            callback.cancel();
        }
    }

    @Inject(method = "canCollideWith", at = @At("HEAD"), cancellable = true)
    private void tsf$spiritCannotCollide(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof Player player && BodyManager.form(player) == Form.SPIRIT
                || entity instanceof Player other && BodyManager.form(other) == Form.SPIRIT) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void tsf$hypnosisLook(double yaw, double pitch, CallbackInfo callback) {
        if ((Object) this instanceof Player player && BodyManager.restricted(player, HypnosisRestrictions.LOOK)) {
            callback.cancel();
        }
    }

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
