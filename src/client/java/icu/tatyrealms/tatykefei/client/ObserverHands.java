package icu.tatyrealms.tatykefei.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;

final class ObserverHands {
    private ItemStack mainHandItem;
    private ItemStack offHandItem;
    private float mainHandHeight = 1;
    private float oldMainHandHeight = 1;
    private float offHandHeight = 1;
    private float oldOffHandHeight = 1;

    ObserverHands(AbstractClientPlayer player) {
        mainHandItem = player.getMainHandItem();
        offHandItem = player.getOffhandItem();
    }

    void tick(AbstractClientPlayer player) {
        oldMainHandHeight = mainHandHeight;
        oldOffHandHeight = offHandHeight;
        ItemStack nextMainHand = player.getMainHandItem();
        ItemStack nextOffHand = player.getOffhandItem();
        if (replaceImmediately(mainHandItem, nextMainHand)) {
            mainHandItem = nextMainHand;
        }
        if (replaceImmediately(offHandItem, nextOffHand)) {
            offHandItem = nextOffHand;
        }
        if (player.getControlledVehicle() instanceof AbstractBoat boat && (boat.getPaddleState(0) || boat.getPaddleState(1))) {
            mainHandHeight = Mth.clamp(mainHandHeight - 0.4F, 0, 1);
            offHandHeight = Mth.clamp(offHandHeight - 0.4F, 0, 1);
        } else {
            float swap = player.getItemSwapScale(1);
            float mainTarget = mainHandItem == nextMainHand ? swap * swap * swap : 0;
            float offTarget = offHandItem == nextOffHand ? 1 : 0;
            mainHandHeight += Mth.clamp(mainTarget - mainHandHeight, -0.4F, 0.4F);
            offHandHeight += Mth.clamp(offTarget - offHandHeight, -0.4F, 0.4F);
        }
        if (mainHandHeight < 0.1F) {
            mainHandItem = nextMainHand;
        }
        if (offHandHeight < 0.1F) {
            offHandItem = nextOffHand;
        }
    }

    void extract(FirstPersonHandsAndItemsRenderState hands) {
        hands.mainHandItem = mainHandItem;
        hands.offHandItem = offHandItem;
        hands.mainHandHeight = mainHandHeight;
        hands.oldMainHandHeight = oldMainHandHeight;
        hands.offHandHeight = offHandHeight;
        hands.oldOffHandHeight = oldOffHandHeight;
    }

    private boolean replaceImmediately(ItemStack visible, ItemStack expected) {
        return ItemStack.matchesIgnoringComponents(visible, expected, DataComponentType::ignoreSwapAnimation)
                || !Minecraft.getInstance().getItemModelResolver().shouldPlaySwapAnimation(expected);
    }
}
