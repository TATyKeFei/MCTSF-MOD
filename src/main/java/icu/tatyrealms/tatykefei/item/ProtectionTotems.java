package icu.tatyrealms.tatykefei.item;

import icu.tatyrealms.tatykefei.compat.trinkets.TrinketsCompat;
import icu.tatyrealms.tatykefei.body.BodyManager;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ProtectionTotems {
    private static final int FAILURE_COOLDOWN_SECONDS = 60;
    private static final Map<UUID, Map<Kind, Long>> BLOCKED_UNTIL = new HashMap<>();

    private ProtectionTotems() {
    }

    public enum Kind {
        HYPNOSIS,
        POSSESSION,
        SKIN
    }

    public static boolean canAttempt(ServerPlayer actor, Kind kind) {
        long now = actor.level().getServer().overworld().getGameTime();
        Map<Kind, Long> deadlines = BLOCKED_UNTIL.get(actor.getUUID());
        long remaining = deadlines == null ? 0 : deadlines.getOrDefault(kind, 0L) - now;
        if (remaining <= 0) {
            return true;
        }
        actor.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "message.tsf.protection_cooldown",
                net.minecraft.network.chat.Component.translatable("protection.tsf." + kind.name().toLowerCase(java.util.Locale.ROOT)),
                (remaining + 19) / 20), true);
        return false;
    }

    public static int failed(ServerPlayer actor, Kind kind) {
        long now = actor.level().getServer().overworld().getGameTime();
        BLOCKED_UNTIL.values().removeIf(deadlines -> deadlines.values().stream().allMatch(deadline -> deadline <= now));
        Map<Kind, Long> deadlines = BLOCKED_UNTIL.computeIfAbsent(actor.getUUID(), ignored -> new EnumMap<>(Kind.class));
        int seconds = kind == Kind.SKIN ? 1 : FAILURE_COOLDOWN_SECONDS;
        if (kind == Kind.SKIN) {
            deadlines.put(kind, now + seconds * 20L);
        } else {
            // 附身与催眠共用失败冷却，切换攻击方式或目标不能绕过
            deadlines.put(Kind.HYPNOSIS, now + seconds * 20L);
            deadlines.put(Kind.POSSESSION, now + seconds * 20L);
        }
        return seconds;
    }

    public static void clear(UUID player) {
        BLOCKED_UNTIL.remove(player);
    }

    public static void clear() {
        BLOCKED_UNTIL.clear();
    }

    public static Item[] items(Kind kind) {
        return switch (kind) {
            case HYPNOSIS -> new Item[]{TSFItems.TSF_PROTECTION_TOTEM, TSFItems.HYPNOSIS_PROTECTION_TOTEM};
            case POSSESSION -> new Item[]{TSFItems.TSF_PROTECTION_TOTEM, TSFItems.LIN_ZHENGYING_TALISMAN};
            case SKIN -> new Item[]{TSFItems.TSF_PROTECTION_TOTEM};
        };
    }

    public static boolean blocks(ServerPlayer actor, ServerPlayer target, Kind kind) {
        if (actor == target) {
            return false;
        }
        if (!canAttempt(actor, kind)) {
            return true;
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
        boolean unlimited = stack.is(TSFItems.TSF_PROTECTION_TOTEM);
        int previousDamage = stack.getDamageValue();
        int previousCount = stack.getCount();
        if (!unlimited) {
            // 统一走原版耐久流程，使耐久附魔和不可破坏组件生效
            // 空肉体没有在线操控者，不能借用攻击者的创造模式来免除损耗
            stack.hurtAndBreak(1, actor.level(), null, broken -> {});
        }
        int seconds = failed(actor, kind);
        var effect = net.minecraft.network.chat.Component.translatable("protection.tsf." + kind.name().toLowerCase(java.util.Locale.ROOT));
        BodyManager.message(actor, "protection_blocked", name, effect, seconds);
        ServerPlayer target = actor.level().getServer().getPlayerList().getPlayer(owner);
        if (target != null) {
            String message = unlimited ? "protection_saved_unlimited"
                    : stack.getCount() < previousCount ? "protection_saved_broken"
                    : stack.getDamageValue() > previousDamage ? "protection_saved" : "protection_saved_no_damage";
            BodyManager.message(target, message, effect);
        }
    }
}
