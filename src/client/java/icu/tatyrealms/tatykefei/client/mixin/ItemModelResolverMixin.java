package icu.tatyrealms.tatykefei.client.mixin;

import icu.tatyrealms.tatykefei.client.render.SpiritItemRenderState;
import icu.tatyrealms.tatykefei.state.BodyManager;
import icu.tatyrealms.tatykefei.state.Form;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @Inject(method = "updateForTopItem", at = @At("TAIL"))
    private void tsf$spiritItem(ItemStackRenderState output, ItemStack item, ItemDisplayContext displayContext,
                                Level level, ItemOwner owner, int seed, CallbackInfo callback) {
        boolean held = displayContext.firstPerson() || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        if (held && owner instanceof Player player && BodyManager.form(player) == Form.SPIRIT) {
            ((SpiritItemRenderState) output).tsf$applySpirit();
        }
    }
}
