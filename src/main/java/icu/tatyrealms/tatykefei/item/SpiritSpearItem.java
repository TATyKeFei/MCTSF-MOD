package icu.tatyrealms.tatykefei.item;

import icu.tatyrealms.tatykefei.state.BodyManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public final class SpiritSpearItem extends Item {
    public SpiritSpearItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            Player target = BodyManager.aimedPlayer(player, 5);
            BodyManager.get().useSpiritSpear(serverPlayer, target instanceof ServerPlayer other ? other : null, hand);
        }
        return InteractionResult.SUCCESS;
    }
}
