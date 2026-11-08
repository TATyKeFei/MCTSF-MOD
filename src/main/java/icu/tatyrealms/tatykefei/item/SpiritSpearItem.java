package icu.tatyrealms.tatykefei.item;

import icu.tatyrealms.tatykefei.state.BodyManager;
import icu.tatyrealms.tatykefei.state.Form;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.Level;

public final class SpiritSpearItem extends Item {
    public SpiritSpearItem(Properties properties) {
        super(properties.spear(ToolMaterial.IRON, 0.95F, 0.95F, 0.6F, 2.5F, 11.0F, 6.75F, 5.1F, 11.25F, 4.6F));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (BodyManager.form(player) != Form.SPIRIT || BodyManager.hypnotized(player) || player.isSpectator()) {
            return InteractionResult.FAIL;
        }
        return super.use(level, player, hand);
    }
}
