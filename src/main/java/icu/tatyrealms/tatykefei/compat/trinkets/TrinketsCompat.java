package icu.tatyrealms.tatykefei.compat.trinkets;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.slf4j.LoggerFactory;

public final class TrinketsCompat {
    private static Method componentGetter;
    private static Method inventoryGetter;

    private TrinketsCompat() {
    }

    public static void initialize() {
        boolean updated = FabricLoader.getInstance().isModLoaded("trinkets_updated");
        if (!updated && !FabricLoader.getInstance().isModLoaded("trinkets")) {
            return;
        }
        try {
            String packageName = updated ? "eu.pb4.trinkets.api." : "dev.emi.trinkets.api.";
            String getter = updated ? "getAttachment" : "getTrinketComponent";
            Class<?> api = Class.forName(packageName + "TrinketsApi");
            Class<?> component = Class.forName(packageName + (updated ? "TrinketAttachment" : "TrinketComponent"));
            for (Method method : api.getMethods()) {
                if (method.getName().equals(getter) && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isAssignableFrom(ServerPlayer.class)) {
                    componentGetter = method;
                    break;
                }
            }
            inventoryGetter = component.getMethod("getInventory");
            if (componentGetter == null) {
                throw new NoSuchMethodException("TrinketsApi." + getter);
            }
            LoggerFactory.getLogger("TSF").info("Enabled {} skin slots", updated ? "Trinkets Updated" : "Trinkets");
        } catch (ReflectiveOperationException | LinkageError exception) {
            disable(exception);
        }
    }

    public static boolean equipSkin(ServerPlayer player, ItemStack stack) {
        return equip(player, stack, "chest/tsf_skin/");
    }

    public static boolean available() {
        return componentGetter != null;
    }

    public static boolean equipNecklace(ServerPlayer player, ItemStack stack) {
        return equip(player, stack, "chest/necklace/");
    }

    private static boolean equip(ServerPlayer player, ItemStack stack, String prefix) {
        for (Slot slot : slots(player)) {
            if (slot.name().startsWith(prefix) && slot.stack().isEmpty()
                    && slot.inventory().canPlaceItem(slot.index(), stack)) {
                slot.inventory().setItem(slot.index(), stack.copyWithCount(1));
                slot.inventory().setChanged();
                return true;
            }
        }
        return false;
    }

    public static List<Slot> slots(ServerPlayer player) {
        List<Slot> slots = new ArrayList<>();
        if (componentGetter == null) {
            return slots;
        }
        try {
            Object component = componentGetter.invoke(null, player);
            if (component instanceof Optional<?> optional) {
                component = optional.orElse(null);
            }
            if (component == null) {
                return slots;
            }
            Map<?, ?> groups = (Map<?, ?>) inventoryGetter.invoke(component);
            for (var group : groups.entrySet()) {
                for (var entry : ((Map<?, ?>) group.getValue()).entrySet()) {
                    if (!(entry.getValue() instanceof Container inventory)) {
                        throw new IllegalStateException("Unsupported Trinkets inventory: " + entry.getValue().getClass());
                    }
                    for (int index = 0; index < inventory.getContainerSize(); index++) {
                        slots.add(new Slot(group.getKey() + "/" + entry.getKey() + "/" + index, inventory, index));
                    }
                }
            }
            slots.sort(java.util.Comparator.comparing(Slot::name));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            disable(exception);
            slots.clear();
        }
        return slots;
    }

    private static void disable(Throwable exception) {
        componentGetter = null;
        inventoryGetter = null;
        LoggerFactory.getLogger("TSF").warn("Trinkets API is incompatible; TSF built-in skin layering remains available", exception);
    }

    public record Slot(String name, Container inventory, int index) {
        public ItemStack stack() {
            return inventory.getItem(index);
        }

        public void clear() {
            inventory.setItem(index, ItemStack.EMPTY);
            inventory.setChanged();
        }
    }
}
