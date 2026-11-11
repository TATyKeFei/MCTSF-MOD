package icu.tatyrealms.tatykefei.mixin.player;

import icu.tatyrealms.tatykefei.body.BodyManager;
import icu.tatyrealms.tatykefei.state.HypnosisRestrictions;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "performUnsignedChatCommand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/commands/Commands;performCommand(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)V"), cancellable = true)
    private void tsf$observerUnsignedCommand(String command, CallbackInfo callback) {
        if (!BodyManager.get().allowObserverCommand(player, command)) {
            callback.cancel();
        }
    }

    @Inject(method = "performSignedChatCommand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/commands/Commands;performCommand(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)V"), cancellable = true)
    private void tsf$observerSignedCommand(ServerboundChatCommandSignedPacket packet, LastSeenMessages lastSeen, CallbackInfo callback) {
        if (!BodyManager.get().allowObserverCommand(player, packet.command())) {
            callback.cancel();
        }
    }

    @Inject(method = {"handleContainerClick", "handleContainerButtonClick", "handleSetCreativeModeSlot",
            "handleContainerSlotStateChanged", "handlePlaceRecipe", "handleSelectTrade"},
            at = @At("HEAD"), cancellable = true)
    private void tsf$hypnosisInventory(CallbackInfo callback) {
        if (tsf$interactionLocked()) {
            player.containerMenu.sendAllDataToRemote();
            callback.cancel();
        }
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void tsf$hypnosisActions(ServerboundPlayerActionPacket packet, CallbackInfo callback) {
        if (tsf$interactionLocked() && switch (packet.getAction()) {
            case DROP_ITEM, DROP_ALL_ITEMS, SWAP_ITEM_WITH_OFFHAND, STAB -> true;
            default -> false;
        }) {
            player.inventoryMenu.sendAllDataToRemote();
            callback.cancel();
        }
    }

    private boolean tsf$interactionLocked() {
        return player.level().getServer().isSameThread()
                && BodyManager.restricted(player, HypnosisRestrictions.INTERACTION);
    }
}
