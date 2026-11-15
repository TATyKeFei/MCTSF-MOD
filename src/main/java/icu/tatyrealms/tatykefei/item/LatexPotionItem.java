package icu.tatyrealms.tatykefei.item;

import icu.tatyrealms.tatykefei.body.BodyManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;

public final class LatexPotionItem extends Item {
    public LatexPotionItem(Properties properties) {
        super(LatexPotions.properties(properties).component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)
                .usingConvertsTo(Items.GLASS_BOTTLE));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (LatexPotions.source(player.getItemInHand(hand)).isEmpty()) {
            return InteractionResult.FAIL;
        }
        return super.use(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        var source = LatexPotions.source(stack);
        if (source.isEmpty()) {
            return stack;
        }
        ResolvableProfile profile = stack.getOrDefault(DataComponents.PROFILE, LatexPotions.profile(source.get()));
        int potionLevel = LatexPotions.level(stack);
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (entity instanceof ServerPlayer player && BodyManager.get() != null) {
            BodyManager.get().applyLatexPotion(player, source.get().uuid(), profile, potionLevel);
        }
        return result;
    }
}
