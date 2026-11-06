package icu.tatyrealms.tatykefei.network;

import com.google.gson.JsonElement;
import java.util.List;
import java.util.UUID;

public record ObserverInventory(UUID target, List<JsonElement> items, List<JsonElement> crafting, int selectedSlot,
        float health, float absorption, int food, float saturation, float experienceProgress, int experienceLevel,
        int totalExperience) {
}
