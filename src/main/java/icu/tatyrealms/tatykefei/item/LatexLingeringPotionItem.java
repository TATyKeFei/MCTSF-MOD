package icu.tatyrealms.tatykefei.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.level.Level;

public final class LatexLingeringPotionItem extends LingeringPotionItem {
    public LatexLingeringPotionItem(Properties properties) {
        super(LatexPotions.properties(properties).component(DataComponents.POTION_DURATION_SCALE, 0.25F));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return LatexPotions.source(player.getItemInHand(hand)).isEmpty()
                ? InteractionResult.FAIL : super.use(level, player, hand);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(descriptionId);
    }
}
