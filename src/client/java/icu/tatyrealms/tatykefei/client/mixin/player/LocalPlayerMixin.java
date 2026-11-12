package icu.tatyrealms.tatykefei.client.mixin.player;

import icu.tatyrealms.tatykefei.body.BodyManager;
import icu.tatyrealms.tatykefei.client.input.BodyPosePicking;
import icu.tatyrealms.tatykefei.state.Form;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @WrapOperation(method = "pick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"))
    private static EntityHitResult tsf$pickBodyPose(Entity camera, Vec3 from, Vec3 to, AABB bounds,
            Predicate<Entity> predicate, double maxDistance, Operation<EntityHitResult> original,
            Entity cameraEntity, double blockRange, double entityRange, float partialTick) {
        EntityHitResult hit = original.call(camera, from, to, bounds, BodyPosePicking.vanillaTargets(predicate), maxDistance);
        return BodyPosePicking.nearest(camera, from, to, partialTick, predicate, hit);
    }

    @WrapOperation(method = "raycastHitResult", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/component/AttackRange;getClosesetHit(Lnet/minecraft/world/entity/Entity;FLjava/util/function/Predicate;)Lnet/minecraft/world/phys/HitResult;"))
    private HitResult tsf$pickBodyWithWeapon(AttackRange range, Entity camera, float partialTick,
            Predicate<Entity> predicate, Operation<HitResult> original) {
        HitResult hit = original.call(range, camera, partialTick, BodyPosePicking.vanillaTargets(predicate));
        return BodyPosePicking.withAttackRange(camera, partialTick, range, predicate, hit);
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void tsf$spiritFlight(CallbackInfo callback) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (BodyManager.form(player) == Form.SPIRIT) {
            player.noPhysics = false;
            player.setOnGround(false);
            player.getAbilities().flying = player.getAbilities().mayfly;
        }
    }

    @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
    private void tsf$spiritCanOverlapPartialBlocks(double horizontal, double depth, CallbackInfo callback) {
        if (BodyManager.form((LocalPlayer) (Object) this) == Form.SPIRIT) {
            callback.cancel();
        }
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isSpectator()Z"))
    private boolean tsf$keepSpiritFlying(MultiPlayerGameMode gameMode) {
        return BodyManager.form((LocalPlayer) (Object) this) == Form.SPIRIT || gameMode.isSpectator();
    }
}
