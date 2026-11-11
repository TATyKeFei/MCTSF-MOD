package icu.tatyrealms.tatykefei.mixin.item;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import icu.tatyrealms.tatykefei.body.BodyManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KineticWeapon.class)
public abstract class KineticWeaponMixin {
    @WrapOperation(method = "damageEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;stabAttack(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/Entity;FZZZ)Z"))
    private boolean tsf$spiritSpearHit(LivingEntity attacker, EquipmentSlot slot, Entity target, float damage,
            boolean dealsDamage, boolean dealsKnockback, boolean dismounts, Operation<Boolean> original,
            @Local(argsOnly = true) ItemStack stack) {
        // Keep the original stack: possession can replace the attacker's inventory during this hit loop.
        if (BodyManager.spiritSpear(stack)) {
            return attacker instanceof ServerPlayer player && target instanceof ServerPlayer other
                    && BodyManager.get().hitSpiritSpear(player, other,
                            slot == EquipmentSlot.OFFHAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        }
        return original.call(attacker, slot, target, damage, dealsDamage, dealsKnockback, dismounts);
    }
}
