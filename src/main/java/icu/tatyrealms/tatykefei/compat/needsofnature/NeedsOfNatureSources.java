package icu.tatyrealms.tatykefei.compat.needsofnature;

import icu.tatyrealms.tatykefei.config.TSFServerConfig;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

public final class NeedsOfNatureSources {
    public static final String DATA_KEY = "tsf:liquid_sources";
    /** 自然需求记录液体瓶来源实体类型所用的键。 */
    public static final String LIQUID_ENTITY_KEY = "liquid_entity";

    private NeedsOfNatureSources() {
    }

    public static NatureLiquidSources get(Object value) {
        return value instanceof NatureLiquidSourceHolder holder ? holder.tsf$liquidSources() : NatureLiquidSources.UNKNOWN;
    }

    public static void set(Object value, NatureLiquidSources sources) {
        if (value instanceof NatureLiquidSourceHolder holder) {
            holder.tsf$setLiquidSources(sources == null ? NatureLiquidSources.UNKNOWN : sources);
        }
    }

    public static NatureLiquidSources producedBy(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            return new NatureLiquidSources(List.of(new NatureLiquidSources.Source(
                    player.getUUID(), player.getGameProfile().name())), false);
        }
        return NatureLiquidSources.UNKNOWN;
    }

    /** 判断液体瓶是否来自玩家实体。 */
    public static boolean bottledFromPlayer(ItemStack stack) {
        return !stack.isEmpty() && "minecraft:player".equals(stack
                .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .getString(LIQUID_ENTITY_KEY).orElse(""));
    }

    public static void labelBottle(Object tank, ItemStack stack) {
        NatureLiquidSources sources = get(tank);
        if (stack.isEmpty() || !stack.is(Items.POTION)
                || sources.players().isEmpty()) {
            return;
        }
        // 来源供炼制使用，显示开关只控制可见标签。
        NatureLiquidSources.CODEC.encodeStart(NbtOps.INSTANCE, sources).result().ifPresent(tag ->
                CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.put(DATA_KEY, tag)));
        if (!TSFServerConfig.get().natureLiquidSourceLabels) {
            return;
        }
        String names = sources.players().stream().map(NatureLiquidSources.Source::name)
                .collect(Collectors.joining(", "));
        String key = sources.other() || sources.players().size() > 1
                ? "tooltip.tsf.liquid_sources_mixed" : "tooltip.tsf.liquid_source";
        Component line = Component.translatable(key, names).withStyle(ChatFormatting.GRAY)
                .withStyle(style -> style.withItalic(false));
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        if (lore.lines().size() < ItemLore.MAX_LINES) {
            stack.set(DataComponents.LORE, lore.withLineAdded(line));
        }
    }
}
