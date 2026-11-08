package icu.tatyrealms.tatykefei.state;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import icu.tatyrealms.tatykefei.network.ObserverInventory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

final class ObserverSync {
    private static final Gson GSON = new Gson();
    private final UUID target;
    private final List<ItemStack> items = new ArrayList<>();
    private final List<ItemStack> crafting = new ArrayList<>();
    private final Map<Integer, JsonElement> encodedItems = new HashMap<>();
    private final Map<Integer, JsonElement> encodedCrafting = new HashMap<>();
    private ObserverInventory.Status status;
    private String fullJson;
    private String deltaJson;

    ObserverSync(UUID target) {
        this.target = target;
    }

    void update(Container inventory, IntFunction<ItemStack> craftingMenu, ObserverInventory.Status nextStatus,
            Function<ItemStack, JsonElement> encoder) {
        Map<Integer, JsonElement> changedItems = changes(inventory::getItem, inventory.getContainerSize(), items, encodedItems, encoder);
        Map<Integer, JsonElement> changedCrafting = changes(craftingMenu, 5, crafting, encodedCrafting, encoder);
        if (!changedItems.isEmpty() || !changedCrafting.isEmpty() || !nextStatus.equals(status)) {
            status = nextStatus;
            fullJson = null;
            deltaJson = GSON.toJson(new ObserverInventory(target, false, changedItems, changedCrafting, status));
        } else {
            deltaJson = null;
        }
    }

    String fullJson() {
        if (fullJson == null) {
            fullJson = GSON.toJson(new ObserverInventory(target, true, encodedItems, encodedCrafting, status));
        }
        return fullJson;
    }

    String deltaJson() {
        return deltaJson;
    }

    private Map<Integer, JsonElement> changes(IntFunction<ItemStack> container, int size, List<ItemStack> previous,
            Map<Integer, JsonElement> encoded, Function<ItemStack, JsonElement> encoder) {
        Map<Integer, JsonElement> changed = new HashMap<>();
        for (int slot = 0; slot < size; slot++) {
            ItemStack item = container.apply(slot);
            if (slot >= previous.size() || !ItemStack.matches(previous.get(slot), item)) {
                JsonElement json = encoder.apply(item);
                changed.put(slot, json);
                encoded.put(slot, json);
                if (slot >= previous.size()) {
                    previous.add(item.copy());
                } else {
                    previous.set(slot, item.copy());
                }
            }
        }
        return changed;
    }
}
