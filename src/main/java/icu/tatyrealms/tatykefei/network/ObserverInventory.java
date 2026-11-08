package icu.tatyrealms.tatykefei.network;

import com.google.gson.JsonElement;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.InteractionHand;

public record ObserverInventory(UUID target, boolean full, Map<Integer, JsonElement> items,
        Map<Integer, JsonElement> crafting, Status status) {
    public record Status(int selectedSlot, float health, float absorption, int food, float saturation,
            float experienceProgress, int experienceLevel, int totalExperience, InteractionHand useItemHand,
            int useItemRemainingTicks) {
    }
}
