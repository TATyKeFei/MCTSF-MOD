package icu.tatyrealms.tatykefei.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.level.Level;

public final class LatexSplashPotionItem extends SplashPotionItem {
    public LatexSplashPotionItem(Properties properties) {
        super(LatexPotions.properties(properties));
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
