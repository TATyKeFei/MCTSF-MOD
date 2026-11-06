package icu.tatyrealms.tatykefei.item;

import icu.tatyrealms.tatykefei.TSF;
import icu.tatyrealms.tatykefei.state.BodyManager;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.InstantaneousMobEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public final class BodyPotionEffects {
    public static final Holder<MobEffect> SOUL_PROJECTION = register("soul_projection", false, 0x81C6ED);
    public static final Holder<MobEffect> PERSONALITY_EXPULSION = register("personality_expulsion", true, 0x76C853);

    private BodyPotionEffects() {
    }

    private static Holder<MobEffect> register(String name, boolean expulsion, int color) {
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, TSF.id(name), new InstantaneousMobEffect(MobEffectCategory.NEUTRAL, color) {
            @Override
            public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
                if (entity instanceof ServerPlayer player && BodyManager.get() != null) {
                    BodyManager.get().applyBodyPotion(player, expulsion);
                }
                return false;
            }
        });
    }
}
