package icu.tatyrealms.tatykefei.mixin;

import icu.tatyrealms.tatykefei.state.BodyManager;
import icu.tatyrealms.tatykefei.state.Form;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"))
    private void tsf$forgetSpirit(CallbackInfo callback) {
        Mob mob = (Mob) (Object) this;
        boolean targetingSpirit = tsf$isSpirit(mob.getTargetUnchecked());
        boolean brainTargetingSpirit = mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && tsf$isSpirit(mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null));
        if (targetingSpirit) {
            mob.setTarget(null);
        }
        if (brainTargetingSpirit) {
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            mob.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            mob.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        }
        if (mob.getBrain().hasMemoryValue(MemoryModuleType.ROAR_TARGET)
                && tsf$isSpirit(mob.getBrain().getMemory(MemoryModuleType.ROAR_TARGET).orElse(null))) {
            mob.getBrain().eraseMemory(MemoryModuleType.ROAR_TARGET);
        }
        if (targetingSpirit || brainTargetingSpirit) {
            mob.getNavigation().stop();
            mob.setAggressive(false);
        }
        if (tsf$isSpirit(mob.getLastHurtByMob())) {
            mob.setLastHurtByMob(null);
        }
        if (mob instanceof NeutralMob neutral
                && tsf$isSpirit(EntityReference.getLivingEntity(neutral.getPersistentAngerTarget(), mob.level()))) {
            neutral.stopBeingAngry();
        }
    }

    @Unique
    private static boolean tsf$isSpirit(Entity entity) {
        return entity instanceof Player player && BodyManager.form(player) == Form.SPIRIT;
    }
}
