package icu.tatyrealms.tatykefei.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.datafixers.util.Pair;
import icu.tatyrealms.tatykefei.client.ObserverView;
import java.util.List;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @WrapWithCondition(method = "handleEntityEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;handleEntityEvent(B)V"))
    private boolean tsf$observerHandSwap(Entity entity, byte event) {
        return event != EntityEvent.SWAP_HANDS || entity != ObserverView.target();
    }

    @ModifyExpressionValue(method = "handleSetEquipment", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundSetEquipmentPacket;getSlots()Ljava/util/List;"))
    private List<Pair<EquipmentSlot, ItemStack>> tsf$observerEquipment(List<Pair<EquipmentSlot, ItemStack>> slots,
            ClientboundSetEquipmentPacket packet) {
        var player = ObserverView.target();
        if (player == null || player.getId() != packet.getEntity()) {
            return slots;
        }
        return slots.stream().filter(slot -> slot.getFirst() != EquipmentSlot.MAINHAND).toList();
    }
}
