package icu.tatyrealms.tatykefei.mixin;

import icu.tatyrealms.tatykefei.state.BodyManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void tsf$leaveBeforeSave(ServerPlayer player, CallbackInfo callback) {
        if (BodyManager.get() != null) {
            BodyManager.get().left(player);
        }
    }
}
