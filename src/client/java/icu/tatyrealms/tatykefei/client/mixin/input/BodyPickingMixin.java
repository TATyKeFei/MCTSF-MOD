package icu.tatyrealms.tatykefei.client.mixin.input;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import icu.tatyrealms.tatykefei.client.input.BodyPosePicking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class BodyPickingMixin {
    @Shadow
    public HitResult hitResult;

    @WrapOperation(method = "startUseItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isWithinEntityInteractionRange(Lnet/minecraft/world/entity/Entity;D)Z"))
    private boolean tsf$bodyReach(LocalPlayer player, Entity entity, double buffer, Operation<Boolean> original) {
        if (BodyPosePicking.applies(entity) && hitResult instanceof EntityHitResult hit && hit.getEntity() == entity) {
            double range = player.entityInteractionRange() + buffer;
            return !entity.isRemoved() && player.getEyePosition().distanceToSqr(hit.getLocation()) < range * range;
        }
        return original.call(player, entity, buffer);
    }
}
