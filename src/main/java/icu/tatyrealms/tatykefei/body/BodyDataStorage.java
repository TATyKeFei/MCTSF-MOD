package icu.tatyrealms.tatykefei.body;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import icu.tatyrealms.tatykefei.compat.trinkets.TrinketsCompat;
import icu.tatyrealms.tatykefei.mixin.entity.LivingEntityDataAccessor;
import icu.tatyrealms.tatykefei.mixin.player.PlayerDataAccessor;
import icu.tatyrealms.tatykefei.state.BodyCooldowns;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** 只读写属于肉体的数据，不加载账号身份、权限或整个玩家实体。 */
public final class BodyDataStorage {
    private BodyDataStorage() {
    }

    public static void capture(BodyData data, ServerPlayer player) {
        var operations = player.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        List<JsonElement> inventory = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            inventory.add(ItemStack.OPTIONAL_CODEC.encodeStart(operations,
                    BodyManager.spiritSpear(stack) ? ItemStack.EMPTY : stack).getOrThrow());
        }
        // 缺少兼容模组或槽位时保留档案中的饰品，避免切服后静默丢失
        Map<String, JsonElement> accessories = new HashMap<>(data.accessories);
        for (TrinketsCompat.Slot slot : TrinketsCompat.slots(player)) {
            JsonElement stored = accessories.get(slot.name());
            if (slot.stack().isEmpty() && stored != null
                    && !slot.inventory().canPlaceItem(slot.index(), decodeItem(player, stored))) {
                continue;
            }
            accessories.remove(slot.name());
            if (!slot.stack().isEmpty()) {
                accessories.put(slot.name(), ItemStack.OPTIONAL_CODEC.encodeStart(operations, slot.stack()).getOrThrow());
            }
        }
        JsonElement attributes = AttributeInstance.Packed.LIST_CODEC.encodeStart(operations, player.getAttributes().pack()).getOrThrow();
        JsonElement effects = MobEffectInstance.CODEC.listOf().encodeStart(operations,
                new ArrayList<>(player.getActiveEffects())).getOrThrow();
        TagValueOutput food = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        player.getFoodData().addAdditionalSaveData(food);
        JsonElement foodData = CompoundTag.CODEC.encodeStart(JsonOps.INSTANCE, food.buildResult()).getOrThrow();

        data.inventory = inventory;
        data.accessories = accessories;
        data.attributes = attributes;
        data.effects = effects;
        data.foodData = foodData;
        data.health = player.getHealth();
        data.food = player.getFoodData().getFoodLevel();
        data.saturation = player.getFoodData().getSaturationLevel();
        data.experienceProgress = player.experienceProgress;
        data.experienceLevel = player.experienceLevel;
        data.totalExperience = player.totalExperience;
        data.enchantmentSeed = player.getEnchantmentSeed();
        data.selectedSlot = player.getInventory().getSelectedSlot();
        data.absorption = player.getAbsorptionAmount();
        data.air = player.getAirSupply();
        data.fireTicks = player.getRemainingFireTicks();
        data.frozenTicks = player.getTicksFrozen();
        data.hurtTime = player.hurtTime;
        data.damageCooldownTime = player.damageCooldownTime;
        data.takeXpDelay = player.takeXpDelay;
        data.fallDistance = player.fallDistance;
        data.itemCooldowns = ((BodyCooldowns) player.getCooldowns()).tsf$captureCooldowns();
        data.playerDataInitialized = true;
    }

    /** 先完成所有解码，损坏的档案不能在清空玩家物品后才报错。 */
    public static Prepared prepare(BodyData data, ServerPlayer player) {
        var operations = player.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        List<ItemStack> inventory = data.inventory.stream().map(json -> decodeItem(player, json)).toList();
        Map<String, ItemStack> accessories = new HashMap<>();
        data.accessories.forEach((slot, json) -> accessories.put(slot, decodeItem(player, json)));
        List<AttributeInstance.Packed> attributes = data.attributes == null ? List.of()
                : AttributeInstance.Packed.LIST_CODEC.parse(operations, data.attributes).getOrThrow();
        List<MobEffectInstance> effects = data.effects == null ? List.of()
                : MobEffectInstance.CODEC.listOf().parse(operations, data.effects).getOrThrow();
        CompoundTag food = data.foodData == null ? new CompoundTag()
                : CompoundTag.CODEC.parse(JsonOps.INSTANCE, data.foodData).getOrThrow();
        food.putInt("foodLevel", data.food);
        food.putFloat("foodSaturationLevel", data.saturation);
        Map<Identifier, Integer> cooldowns = new HashMap<>();
        data.itemCooldowns.forEach((name, remaining) -> cooldowns.put(Identifier.parse(name), remaining));
        return new Prepared(data, inventory, accessories, attributes, effects, food, cooldowns);
    }

    public static void restore(BodyData data, ServerPlayer player) {
        prepare(data, player).apply(player);
    }

    private static ItemStack decodeItem(ServerPlayer player, JsonElement json) {
        return ItemStack.OPTIONAL_CODEC.parse(player.registryAccess().createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
    }

    public record Prepared(BodyData data, List<ItemStack> inventory, Map<String, ItemStack> accessories,
                           List<AttributeInstance.Packed> attributes, List<MobEffectInstance> effects,
                           CompoundTag food, Map<Identifier, Integer> cooldowns) {
        public void apply(ServerPlayer player) {
            player.stopUsingItem();
            player.removeAllEffects();
            // 不调用 Inventory.clearContent()，避免兼容模组额外清除未处理的数据
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
            List<TrinketsCompat.Slot> slots = TrinketsCompat.slots(player);
            slots.forEach(TrinketsCompat.Slot::clear);
            ((LivingEntityDataAccessor) player).tsf$updateEquipment();
            // 原版 apply() 不移除旧的永久修饰符，必须先复位旧肉体的属性
            for (AttributeInstance.Packed previous : player.getAttributes().pack()) {
                AttributeInstance instance = player.getAttribute(previous.attribute());
                for (var modifier : List.copyOf(instance.getPermanentModifiers())) {
                    instance.removeModifier(modifier.id());
                }
                player.getAttributes().resetBaseValue(previous.attribute());
            }
            player.getAttributes().apply(attributes);
            effects.forEach(effect -> player.addEffect(new MobEffectInstance(effect)));
            for (int slot = 0; slot < Math.min(inventory.size(), player.getInventory().getContainerSize()); slot++) {
                player.getInventory().setItem(slot, inventory.get(slot).copy());
            }
            player.getInventory().setSelectedSlot(Math.clamp(data.selectedSlot, 0, 8));
            for (TrinketsCompat.Slot slot : slots) {
                ItemStack stack = accessories.get(slot.name());
                if (stack != null && slot.inventory().canPlaceItem(slot.index(), stack)) {
                    slot.inventory().setItem(slot.index(), stack.copy());
                    slot.inventory().setChanged();
                }
            }
            ((LivingEntityDataAccessor) player).tsf$updateEquipment();
            player.getFoodData().readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), food));
            player.setHealth(Math.min(data.health, player.getMaxHealth()));
            player.setAbsorptionAmount(data.absorption);
            player.setAirSupply(data.air);
            player.setRemainingFireTicks(data.fireTicks);
            player.setTicksFrozen(data.frozenTicks);
            player.hurtTime = data.hurtTime;
            player.damageCooldownTime = data.damageCooldownTime;
            player.takeXpDelay = data.takeXpDelay;
            player.fallDistance = data.fallDistance;
            player.experienceProgress = data.experienceProgress;
            player.experienceLevel = data.experienceLevel;
            player.totalExperience = data.totalExperience;
            ((PlayerDataAccessor) player).tsf$setEnchantmentSeed(data.enchantmentSeed == 0
                    ? player.getRandom().nextInt() : data.enchantmentSeed);
            var previousCooldowns = ((BodyCooldowns) player.getCooldowns()).tsf$captureCooldowns();
            previousCooldowns.keySet().forEach(name -> player.getCooldowns().removeCooldown(Identifier.parse(name)));
            cooldowns.forEach(player.getCooldowns()::addCooldown);
            player.connection.send(new ClientboundSetExperiencePacket(player.experienceProgress, player.totalExperience, player.experienceLevel));
        }
    }
}
