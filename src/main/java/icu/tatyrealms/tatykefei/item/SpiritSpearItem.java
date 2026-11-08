package icu.tatyrealms.tatykefei.item;

import icu.tatyrealms.tatykefei.state.BodyManager;
import icu.tatyrealms.tatykefei.state.Form;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

public final class SpiritSpearItem extends Item {
    public static final int DASH_TICKS = 10;
    public static final int COOLDOWN_TICKS = 24;
    public static final double DASH_SPEED = 1.25;

    public SpiritSpearItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (BodyManager.form(player) != Form.SPIRIT || BodyManager.hypnotized(player)
                || player.isSpectator() || !player.isAlive() || player.isUsingItem()
                || player.getCooldowns().isOnCooldown(player.getItemInHand(hand))) {
            return InteractionResult.FAIL;
        }
        if (player instanceof ServerPlayer serverPlayer && !BodyManager.get().beginSpiritSpearDash(serverPlayer, hand)) {
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        player.setDeltaMovement(player.getLookAngle().scale(DASH_SPEED));
        player.getCooldowns().addCooldown(TSFItems.POSSESSION_SPEAR.getDefaultInstance(), COOLDOWN_TICKS);
        player.getCooldowns().addCooldown(TSFItems.SNATCHING_SPEAR.getDefaultInstance(), COOLDOWN_TICKS);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.TRIDENT;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return DASH_TICKS;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int ticksRemaining) {
        if (user instanceof Player player && player.isLocalPlayer() && BodyManager.form(player) == Form.SPIRIT
                && !BodyManager.hypnotized(player)) {
            player.setDeltaMovement(ticksRemaining > 0 ? player.getLookAngle().scale(DASH_SPEED) : net.minecraft.world.phys.Vec3.ZERO);
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int ticksRemaining) {
        endDash(user);
        return true;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        endDash(user);
        return stack;
    }

    private void endDash(LivingEntity user) {
        if (user instanceof ServerPlayer player) {
            BodyManager.get().cancelSpiritSpearDash(player);
        } else if (user instanceof Player player && player.isLocalPlayer()) {
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        }
    }
}
