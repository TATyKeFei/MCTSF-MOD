package icu.tatyrealms.tatykefei.item;

import com.mojang.authlib.GameProfile;
import icu.tatyrealms.tatykefei.TSF;
import icu.tatyrealms.tatykefei.body.BodyManager;
import icu.tatyrealms.tatykefei.compat.needsofnature.NatureLiquidSources;
import icu.tatyrealms.tatykefei.compat.needsofnature.NeedsOfNatureSources;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

public final class LatexPotions {
    public static final int COLOR = 0xEEE8F0;
    public static final int TICKS_PER_LEVEL = 20 * 60 * 20;
    // 原版状态效果的等级范围为 I 至 CCLVI。
    public static final int MAX_LEVEL = MobEffectInstance.MAX_AMPLIFIER + 1;
    public static final Holder<MobEffect> EFFECT = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            TSF.id("latex"), new MobEffect(MobEffectCategory.NEUTRAL, COLOR) {});
    private static final String SOURCE_KEY = "tsf:latex_source";
    private static final String LEVEL_KEY = "tsf:latex_level";

    private LatexPotions() {
    }

    public static void initialize() {
    }

    public static Optional<NatureLiquidSources.Source> reagentSource(ItemStack stack) {
        if (!stack.is(Items.POTION)) {
            return Optional.empty();
        }
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return data.read(NeedsOfNatureSources.DATA_KEY, NatureLiquidSources.CODEC)
                .filter(sources -> !sources.other() && sources.players().size() == 1)
                .map(sources -> sources.players().getFirst());
    }

    /** 判断是否为任意玩家产出的液体瓶：先看自然需求记录的来源实体，再看乳胶来源标签。 */
    public static boolean isPlayerLiquidBottle(ItemStack stack) {
        return stack.is(Items.POTION)
                && (NeedsOfNatureSources.bottledFromPlayer(stack) || reagentSource(stack).isPresent());
    }

    public static Optional<NatureLiquidSources.Source> source(ItemStack stack) {
        if (!isLatexItem(stack)) {
            return Optional.empty();
        }
        return sourceData(stack);
    }

    public static Optional<NatureLiquidSources.Source> sourceData(DataComponentGetter components) {
        return components.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .read(SOURCE_KEY, NatureLiquidSources.Source.CODEC);
    }

    public static boolean isLatexItem(ItemStack stack) {
        return stack.is(TSFItems.LATEX_POTION) || stack.is(TSFItems.LATEX_SPLASH_POTION)
                || stack.is(TSFItems.LATEX_LINGERING_POTION) || stack.is(TSFItems.LATEX_ARROW);
    }

    public static Item potionType(ItemStack stack) {
        if (stack.is(Items.SPLASH_POTION) || stack.is(TSFItems.LATEX_SPLASH_POTION)) {
            return TSFItems.LATEX_SPLASH_POTION;
        }
        return stack.is(Items.LINGERING_POTION) || stack.is(TSFItems.LATEX_LINGERING_POTION)
                ? TSFItems.LATEX_LINGERING_POTION : TSFItems.LATEX_POTION;
    }

    public static int level(ItemStack stack) {
        return Math.clamp(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .getIntOr(LEVEL_KEY, 1), 1, MAX_LEVEL);
    }

    public static MobEffectInstance effect(int level) {
        return new MobEffectInstance(EFFECT, TICKS_PER_LEVEL * level, level - 1, false, false, true);
    }

    public static ItemStack create(NatureLiquidSources.Source source, ResolvableProfile profile, int level) {
        return create(source, profile, level, TSFItems.LATEX_POTION);
    }

    public static ItemStack create(NatureLiquidSources.Source source, ResolvableProfile profile, int level, Item item) {
        level = Math.clamp(level, 1, MAX_LEVEL);
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.PROFILE, profile);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(COLOR),
                List.of(effect(level)), Optional.empty()));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("tooltip.tsf.latex_target", source.name()).withStyle(ChatFormatting.GRAY),
                Component.translatable("tooltip.tsf.latex_level", roman(level),
                        java.math.BigDecimal.valueOf(level * 20.0 * stack.getOrDefault(DataComponents.POTION_DURATION_SCALE, 1.0F))
                                .stripTrailingZeros().toPlainString()).withStyle(ChatFormatting.GRAY))));
        int storedLevel = level;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
            data.store(SOURCE_KEY, NatureLiquidSources.Source.CODEC, source);
            data.putInt(LEVEL_KEY, storedLevel);
        });
        return stack;
    }

    public static ItemStack create(GameProfile profile, int level) {
        return create(new NatureLiquidSources.Source(profile.id(), profile.name()), ResolvableProfile.createResolved(profile), level);
    }

    public static ItemStack create(GameProfile profile, int level, Item item) {
        return create(new NatureLiquidSources.Source(profile.id(), profile.name()), ResolvableProfile.createResolved(profile), level, item);
    }

    public static ItemStack convert(ItemStack stack, Item item) {
        var source = source(stack).orElseThrow();
        return create(source, stack.getOrDefault(DataComponents.PROFILE, profile(source)), level(stack), item);
    }

    public static Item.Properties properties(Item.Properties properties) {
        return properties.component(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(COLOR),
                List.of(), Optional.empty())).component(DataComponents.LORE, ItemLoreText.translated("tooltip.tsf.latex_potion"));
    }

    public static boolean apply(LivingEntity target, ItemStack carrier, MobEffectInstance effect) {
        var source = source(carrier);
        if (source.isEmpty() || BodyManager.get() == null) {
            return false;
        }
        return BodyManager.get().applyLatexEffect(target, source.get().uuid(),
                carrier.getOrDefault(DataComponents.PROFILE, profile(source.get())), effect);
    }

    public static ResolvableProfile profile(NatureLiquidSources.Source source) {
        BodyManager manager = BodyManager.get();
        return manager == null ? ResolvableProfile.createUnresolved(source.uuid()) : manager.latexProfile(source);
    }

    public static Component effectName(int amplifier) {
        return Component.translatable("effect.tsf.latex").append(" " + roman(amplifier + 1));
    }

    private static String roman(int value) {
        StringBuilder text = new StringBuilder();
        int[] numbers = {100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] letters = {"C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        for (int index = 0; index < numbers.length; index++) {
            while (value >= numbers[index]) {
                text.append(letters[index]);
                value -= numbers[index];
            }
        }
        return text.toString();
    }
}
