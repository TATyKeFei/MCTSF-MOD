package icu.tatyrealms.tatykefei.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import icu.tatyrealms.tatykefei.client.mixin.LivingEntityUseAccessor;
import icu.tatyrealms.tatykefei.network.ObserverInventory;
import icu.tatyrealms.tatykefei.state.BodyManager;
import icu.tatyrealms.tatykefei.state.Form;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState.HandRenderSelection;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.Identifier;

public final class ObserverView {
    private static final Gson GSON = new Gson();
    private static UUID target;
    private static List<ItemStack> items = List.of();
    private static List<ItemStack> crafting = List.of();
    private static ObserverInventory snapshot;
    private static long receivedTick;
    private static boolean useItemPending;
    private static final BitSet changedItems = new BitSet();
    private static final BitSet changedCrafting = new BitSet();
    private static AbstractClientPlayer inventoryPlayer;
    private static boolean statusPending;
    private static AbstractClientPlayer handPlayer;
    private static ObserverHands handAnimation;

    private ObserverView() {
    }

    public static void receive(String json) {
        ObserverInventory inventory = GSON.fromJson(json, ObserverInventory.class);
        Minecraft client = Minecraft.getInstance();
        if (inventory == null || client.level == null) {
            clear();
            return;
        }
        if (inventory.full()) {
            if (!inventory.target().equals(target)) {
                clear();
            }
            target = inventory.target();
            items = new ArrayList<>();
            crafting = new ArrayList<>();
            changedItems.clear();
            changedCrafting.clear();
        } else if (!inventory.target().equals(target)) {
            return;
        }
        snapshot = inventory;
        receivedTick = client.level.getGameTime();
        useItemPending = inventory.status().useItemHand() != null;
        statusPending = true;
        decodeChanges(items, inventory.items(), changedItems);
        decodeChanges(crafting, inventory.crafting(), changedCrafting);
        updateInventory();
    }

    private static void decodeChanges(List<ItemStack> decoded, Map<Integer, JsonElement> changes, BitSet changedSlots) {
        if (changes.isEmpty()) {
            return;
        }
        var operations = Minecraft.getInstance().level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (var entry : changes.entrySet()) {
            while (decoded.size() <= entry.getKey()) {
                decoded.add(ItemStack.EMPTY);
            }
            decoded.set(entry.getKey(), ItemStack.OPTIONAL_CODEC.parse(operations, entry.getValue()).getOrThrow());
            changedSlots.set(entry.getKey());
        }
    }

    public static void clear() {
        target = null;
        items = List.of();
        crafting = List.of();
        snapshot = null;
        receivedTick = 0;
        useItemPending = false;
        changedItems.clear();
        changedCrafting.clear();
        inventoryPlayer = null;
        statusPending = false;
        handPlayer = null;
        handAnimation = null;
    }

    public static AbstractClientPlayer target() {
        Minecraft client = Minecraft.getInstance();
        if (target == null || client.player == null || client.level == null
                || BodyManager.form(client.player) != Form.OBSERVER) {
            return null;
        }
        return client.getCameraEntity() instanceof AbstractClientPlayer player && player.getUUID().equals(target)
                && BodyManager.form(player) == Form.HUMAN ? player : null;
    }

    public static ItemStack item(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    public static void tick() {
        updateInventory();
        AbstractClientPlayer player = target();
        if (player == null) {
            inventoryPlayer = null;
            handPlayer = null;
            handAnimation = null;
        } else {
            hands(player).tick(player);
        }
    }

    private static ObserverHands hands(AbstractClientPlayer player) {
        if (handPlayer != player) {
            handPlayer = player;
            handAnimation = new ObserverHands(player);
        }
        return handAnimation;
    }

    public static void updateInventory() {
        AbstractClientPlayer player = target();
        if (player != null) {
            var status = snapshot.status();
            if (inventoryPlayer != player) {
                inventoryPlayer = player;
                changedItems.set(0, player.getInventory().getContainerSize());
                changedCrafting.set(0, crafting.size());
                statusPending = true;
            }
            for (int slot = changedItems.nextSetBit(0); slot >= 0; slot = changedItems.nextSetBit(slot + 1)) {
                ItemStack item = item(slot);
                ItemStack current = player.getInventory().getItem(slot);
                if (!ItemStack.matches(current, item)) {
                    if (ItemStack.isSameItemSameComponents(current, item)) {
                        current.setCount(item.getCount());
                    } else {
                        player.getInventory().setItem(slot, item.copy());
                    }
                }
            }
            changedItems.clear();
            for (int slot = changedCrafting.nextSetBit(0); slot >= 0; slot = changedCrafting.nextSetBit(slot + 1)) {
                var craftingSlot = player.inventoryMenu.getSlot(slot);
                if (!ItemStack.matches(craftingSlot.getItem(), crafting.get(slot))) {
                    craftingSlot.set(crafting.get(slot).copy());
                }
            }
            changedCrafting.clear();
            if (statusPending) {
                player.getInventory().setSelectedSlot(status.selectedSlot());
                player.setHealth(status.health());
                player.setAbsorptionAmount(status.absorption());
                player.getFoodData().setFoodLevel(status.food());
                player.getFoodData().setSaturation(status.saturation());
                player.experienceProgress = status.experienceProgress();
                player.experienceLevel = status.experienceLevel();
                player.totalExperience = status.totalExperience();
                statusPending = false;
            }
            updateUseItem(player);
        }
    }

    private static void updateUseItem(AbstractClientPlayer player) {
        if (player.isUsingItem()) {
            ItemStack item = player.getItemInHand(player.getUsedItemHand());
            LivingEntityUseAccessor accessor = (LivingEntityUseAccessor) player;
            if (useItemPending && snapshot.status().useItemHand() == player.getUsedItemHand()) {
                long elapsed = Math.max(0, player.level().getGameTime() - receivedTick);
                accessor.tsf$setUseItemRemaining((int) Math.max(0, snapshot.status().useItemRemainingTicks() - elapsed));
                useItemPending = false;
            } else if (!ItemStack.isSameItem(player.getUseItem(), item)) {
                accessor.tsf$setUseItemRemaining(item.getUseDuration(player));
            }
            accessor.tsf$setUseItem(item);
        }
    }

    private static HandRenderSelection handsToRender(AbstractClientPlayer player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        boolean holdsBow = main.is(Items.BOW) || off.is(Items.BOW);
        boolean holdsCrossbow = main.is(Items.CROSSBOW) || off.is(Items.CROSSBOW);
        if (!holdsBow && !holdsCrossbow) {
            return HandRenderSelection.RENDER_BOTH_HANDS;
        }
        if (!player.isUsingItem()) {
            return main.is(Items.CROSSBOW) && CrossbowItem.isCharged(main)
                    ? HandRenderSelection.RENDER_MAIN_HAND_ONLY : HandRenderSelection.RENDER_BOTH_HANDS;
        }
        ItemStack used = player.getUseItem();
        InteractionHand hand = player.getUsedItemHand();
        if (used.is(Items.BOW) || used.is(Items.CROSSBOW)) {
            return HandRenderSelection.onlyForHand(hand);
        }
        return hand == InteractionHand.MAIN_HAND && off.is(Items.CROSSBOW) && CrossbowItem.isCharged(off)
                ? HandRenderSelection.RENDER_MAIN_HAND_ONLY : HandRenderSelection.RENDER_BOTH_HANDS;
    }

    public static void extractExperience(GuiGraphicsExtractor graphics) {
        AbstractClientPlayer player = target();
        if (player == null) {
            return;
        }
        int left = (graphics.guiWidth() - 182) / 2;
        int top = graphics.guiHeight() - 29;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("hud/experience_bar_background"),
                left, top, 182, 5);
        int progress = (int) (player.experienceProgress * 183.0F);
        if (progress > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("hud/experience_bar_progress"),
                    182, 5, 0, 0, left, top, progress, 5);
        }
        if (player.experienceLevel > 0) {
            ContextualBar.extractExperienceLevel(graphics, Minecraft.getInstance().font, player.experienceLevel);
        }
    }

    public static void extractHands(PlayerRenderState state, float partialTick) {
        AbstractClientPlayer player = target();
        if (player == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        updateUseItem(player);
        state.hasPlayer = true;
        state.avatarRenderState = (AvatarRenderState) client.getEntityRenderDispatcher().extractEntity(player, partialTick);
        var hands = state.firstPersonHandsAndItems;
        hands.attackHand = player.getCurrentSwing() == null ? InteractionHand.MAIN_HAND : player.getCurrentSwing().hand();
        ((ObserverViewAngles) player).tsf$extractViewAngles(hands, partialTick);
        hands.isScoping = player.isScoping();
        hands.useItemRemainingTicks = player.getUseItemRemainingTicks();
        hands.handRenderSelection = handsToRender(player);
        hands(player).extract(hands);
        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        hands.mainHandRenderState.clear();
        hands.offHandRenderState.clear();
        client.getItemModelResolver().updateForTopItem(hands.mainHandRenderState, hands.mainHandItem,
                right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, player.level(), player, player.getId());
        client.getItemModelResolver().updateForTopItem(hands.offHandRenderState, hands.offHandItem,
                right ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, player.level(), player, player.getId());
        hands.mainHandUseDuration = hands.mainHandItem.getUseDuration(player);
        hands.offHandUseDuration = hands.offHandItem.getUseDuration(player);
        hands.mainHandChargeDuration = CrossbowItem.getChargeDuration(hands.mainHandItem, player);
        hands.offHandChargeDuration = CrossbowItem.getChargeDuration(hands.offHandItem, player);
        hands.mainHandSwapScale = client.getItemModelResolver().swapAnimationScale(hands.mainHandItem);
        hands.offHandSwapScale = client.getItemModelResolver().swapAnimationScale(hands.offHandItem);
        hands.hasMainHandMapData = hands.hasOffHandMapData = false;
    }
}
