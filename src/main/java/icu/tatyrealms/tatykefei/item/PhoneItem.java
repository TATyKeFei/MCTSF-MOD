package icu.tatyrealms.tatykefei.item;

import icu.tatyrealms.tatykefei.body.BodyManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class PhoneItem extends Item {
    public PhoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (!BodyManager.get().beginHypnosis(serverPlayer, hand)) {
                BodyManager.get().openPhone(serverPlayer);
            }
        } else if (BodyManager.aimedPlayer(player, 16) != null) {
            player.startUsingItem(hand);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int ticksRemaining) {
        if (user instanceof ServerPlayer player) {
            BodyManager.get().tickHypnosis(player);
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int ticksRemaining) {
        if (user instanceof ServerPlayer player) {
            BodyManager.get().cancelHypnosis(player);
        }
        return false;
    }
}
