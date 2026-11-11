package icu.tatyrealms.tatykefei.item;

import icu.tatyrealms.tatykefei.body.BodyManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class BottledSlimeItem extends Item {
    public BottledSlimeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            return BodyManager.get().releaseSlimeBottle(serverPlayer, hand,
                    player.position().add(player.getLookAngle().multiply(1.5, 0, 1.5)))
                    ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            return BodyManager.get().releaseSlimeBottle(player, context.getHand(),
                    Vec3.atBottomCenterOf(context.getClickedPos().relative(context.getClickedFace())))
                    ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onDestroyed(ItemEntity entity) {
        if (!entity.level().isClientSide() && BodyManager.get() != null) {
            BodyManager.get().breakSlimeBottle(entity);
        }
    }
}
