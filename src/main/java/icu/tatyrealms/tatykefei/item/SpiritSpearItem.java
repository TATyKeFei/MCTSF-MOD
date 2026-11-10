package icu.tatyrealms.tatykefei.item;

import icu.tatyrealms.tatykefei.state.BodyManager;
import icu.tatyrealms.tatykefei.state.Form;
import icu.tatyrealms.tatykefei.state.HypnosisRestrictions;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.level.Level;

public final class SpiritSpearItem extends Item {
    public SpiritSpearItem(Properties properties) {
        // Match the vanilla iron spear's right-click charge, reach and movement settings.
        super(properties
                .component(DataComponents.KINETIC_WEAPON, new KineticWeapon(10, 12,
                        KineticWeapon.Condition.ofAttackerSpeed(50, 11.0F),
                        KineticWeapon.Condition.ofAttackerSpeed(135, 5.1F),
                        KineticWeapon.Condition.ofRelativeSpeed(225, 4.6F),
                        0.38F, 0.95F, Optional.of(SoundEvents.SPEAR_USE), Optional.of(SoundEvents.SPEAR_HIT)))
                .component(DataComponents.ATTACK_RANGE, new AttackRange(2.0F, 4.5F, 2.0F, 6.5F, 0.125F, 0.5F))
                .component(DataComponents.USE_EFFECTS, new UseEffects(true, false, 1.0F)));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isAlive() || player.isSpectator() || BodyManager.hypnotized(player)
                || BodyManager.restricted(player, HypnosisRestrictions.INTERACTION)) {
            return InteractionResult.FAIL;
        }
        if (BodyManager.form(player) != Form.SPIRIT) {
            if (player instanceof ServerPlayer serverPlayer) {
                BodyManager.message(serverPlayer, "spear_spirit_only");
            }
            return InteractionResult.FAIL;
        }
        return super.use(level, player, hand);
    }
}
