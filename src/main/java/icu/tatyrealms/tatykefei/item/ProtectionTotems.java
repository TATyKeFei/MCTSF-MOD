package icu.tatyrealms.tatykefei.item;

import icu.tatyrealms.tatykefei.compat.trinkets.TrinketsCompat;
import icu.tatyrealms.tatykefei.body.BodyManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ProtectionTotems {
    private static final Map<UUID, Long> BLOCKED_UNTIL = new HashMap<>();

    private ProtectionTotems() {
    }

    public enum Kind {
        HYPNOSIS,
        POSSESSION,
        SKIN
    }

    public static boolean coolingDown(ServerPlayer actor) {
        return actor.level().getServer().overworld().getGameTime() < BLOCKED_UNTIL.getOrDefault(actor.getUUID(), 0L);
    }

    public static void clear(UUID player) {
        BLOCKED_UNTIL.remove(player);
    }

    public static void clear() {
        BLOCKED_UNTIL.clear();
    }

    public static Item[] items(Kind kind) {
        Item specific = switch (kind) {
            case HYPNOSIS -> TSFItems.HYPNOSIS_PROTECTION_TOTEM;
            case POSSESSION -> TSFItems.LIN_ZHENGYING_TALISMAN;
            case SKIN -> TSFItems.SKIN_PROTECTION_TOTEM;
        };
        return new Item[]{specific, TSFItems.TSF_PROTECTION_TOTEM};
    }

    public static boolean blocks(ServerPlayer actor, ServerPlayer target, Kind kind) {
        if (actor == target) {
            return false;
        }
        for (Item item : items(kind)) {
            for (int slot = 0; slot < target.getInventory().getContainerSize(); slot++) {
                ItemStack stack = target.getInventory().getItem(slot);
                if (stack.is(item)) {
                    consume(actor, target.getUUID(), target.getGameProfile().name(), stack, kind);
                    target.getInventory().setChanged();
                    target.inventoryMenu.sendAllDataToRemote();
                    return true;
                }
            }
            for (TrinketsCompat.Slot slot : TrinketsCompat.slots(target)) {
                if (slot.stack().is(item)) {
                    consume(actor, target.getUUID(), target.getGameProfile().name(), slot.stack(), kind);
                    slot.inventory().setChanged();
                    return true;
                }
            }
        }
        return false;
    }

    public static void consume(ServerPlayer actor, UUID owner, String name, ItemStack stack, Kind kind) {
        if (stack.getDamageValue() + 1 >= stack.getMaxDamage()) {
            stack.shrink(1);
        } else {
            stack.setDamageValue(stack.getDamageValue() + 1);
        }
        BLOCKED_UNTIL.put(actor.getUUID(), actor.level().getServer().overworld().getGameTime() + 20);
        var effect = net.minecraft.network.chat.Component.translatable("protection.tsf." + kind.name().toLowerCase(java.util.Locale.ROOT));
        BodyManager.message(actor, "protection_blocked", name, effect);
        ServerPlayer target = actor.level().getServer().getPlayerList().getPlayer(owner);
        if (target != null) {
            BodyManager.message(target, "protection_saved", effect);
        }
    }
}
