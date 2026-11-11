package icu.tatyrealms.tatykefei.client.mixin.player;

import icu.tatyrealms.tatykefei.client.TSFClient;
import icu.tatyrealms.tatykefei.state.Form;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerDisplayNameMixin {
    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void tsf$visibleIdentity(CallbackInfoReturnable<Component> callback) {
        Player player = (Player) (Object) this;
        if (!player.level().isClientSide()) {
            return;
        }
        var visual = TSFClient.visual(player.getUUID());
        if (visual != null && visual.form() == Form.HUMAN && visual.appearanceOwner() != null
                && !visual.appearanceOwner().equals(player.getUUID())) {
            callback.setReturnValue(Component.literal(visual.bodyName()));
        }
    }
}
