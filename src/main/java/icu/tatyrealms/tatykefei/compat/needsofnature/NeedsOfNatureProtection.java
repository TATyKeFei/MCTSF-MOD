package icu.tatyrealms.tatykefei.compat.needsofnature;

import icu.tatyrealms.tatykefei.body.BodyManager;
import icu.tatyrealms.tatykefei.compat.trinkets.TrinketsCompat;
import icu.tatyrealms.tatykefei.item.ProtectionTotems;
import java.lang.reflect.Method;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.LoggerFactory;

public final class NeedsOfNatureProtection {
    private static final String SLOT = "legs/a";
    private static final TagKey<Item> PLUGS = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath("trinkets", SLOT));
    private static boolean loaded;
    private static Method behavior;
    private static Method occupiesSlot;

    private NeedsOfNatureProtection() {
    }

    public static void initialize() {
        loaded = FabricLoader.getInstance().isModLoaded("needsofnature");
        if (!loaded) {
            return;
        }
        try {
            behavior = Class.forName("com.nonid.data.NonAccessoryDefinitions")
                    .getMethod("getBehavior", Identifier.class);
            occupiesSlot = Class.forName("com.nonid.data.NonAccessoryBehavior")
                    .getMethod("occupiesSlot", String.class);
        } catch (ReflectiveOperationException | LinkageError exception) {
            disableSharedSlots(exception);
        }
    }

    public static boolean protects(ServerPlayer player) {
        for (TrinketsCompat.Slot slot : TrinketsCompat.slots(player)) {
            if (protects(slot.name(), slot.stack())) {
                return true;
            }
        }
        return false;
    }

    public static boolean protects(String slot, ItemStack stack) {
        if (!loaded || !TrinketsCompat.available() || stack.isEmpty() || !stack.is(PLUGS)) {
            return false;
        }
        // 使用实际槽位与分组标签，背包中的同类物品不提供防护
        if (slot.startsWith(SLOT + "/")) {
            return true;
        }
        if (behavior != null && occupiesSlot != null) {
            try {
                // 双槽饰品可能装在另一槽，但仍占用肛塞槽
                Object definition = behavior.invoke(null, BuiltInRegistries.ITEM.getKey(stack.getItem()));
                return definition != null && Boolean.TRUE.equals(occupiesSlot.invoke(definition, SLOT));
            } catch (ReflectiveOperationException | LinkageError exception) {
                disableSharedSlots(exception);
            }
        }
        return false;
    }

    public static void notifyBlocked(ServerPlayer actor, UUID owner, String name) {
        int seconds = ProtectionTotems.failed(actor, ProtectionTotems.Kind.POSSESSION);
        BodyManager.message(actor, "plug_blocked", name, seconds);
        ServerPlayer target = actor.level().getServer().getPlayerList().getPlayer(owner);
        if (target != null) {
            BodyManager.message(target, "plug_saved");
        }
    }

    private static void disableSharedSlots(Throwable exception) {
        behavior = null;
        occupiesSlot = null;
        LoggerFactory.getLogger("TSF").warn("NeedsOfNature shared-slot protection unavailable; direct plug slot remains supported", exception);
    }
}
