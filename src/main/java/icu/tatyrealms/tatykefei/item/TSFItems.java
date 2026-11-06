package icu.tatyrealms.tatykefei.item;

import icu.tatyrealms.tatykefei.TSF;
import icu.tatyrealms.tatykefei.state.BodyManager;
import java.util.function.Function;
import java.util.Optional;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.alchemy.PotionContents;
import java.util.List;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class TSFItems {
    public static final Item PHONE = register("phone", PhoneItem::new);
    public static final Item POSSESSION_SPEAR = register("possession_spear", properties -> new SpiritSpearItem(properties
            .component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("tooltip.tsf.possession_spear"))))));
    public static final Item SNATCHING_SPEAR = register("snatching_spear", properties -> new SpiritSpearItem(properties
            .component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("tooltip.tsf.snatching_spear"))))));
    public static final Item SKIN_KNIFE = register("skin_knife", Item::new);
    public static final Item SKIN_SUIT = register("skin_suit", SkinSuitItem::new);
    public static final Item EMPTY_BODY = register("empty_body", EmptyBodyItem::new);
    public static final Item BODY_REGENERATION_TOTEM = register("body_regeneration_totem", BodyRegenerationTotemItem::new);
    public static final Item SOUL_PROJECTION_POTION = register("soul_projection_potion", properties -> new BodyPotionItem(properties, false));
    public static final Item PERSONALITY_EXPULSION_POTION = register("personality_expulsion_potion", properties -> new BodyPotionItem(properties, true));
    public static final Item SOUL_PROJECTION_SPLASH_POTION = register("soul_projection_splash_potion", properties -> new BodySplashPotionItem(properties, false));
    public static final Item PERSONALITY_EXPULSION_SPLASH_POTION = register("personality_expulsion_splash_potion", properties -> new BodySplashPotionItem(properties, true));
    public static final Item SOUL_PROJECTION_LINGERING_POTION = register("soul_projection_lingering_potion", properties -> new BodyLingeringPotionItem(properties, false));
    public static final Item PERSONALITY_EXPULSION_LINGERING_POTION = register("personality_expulsion_lingering_potion", properties -> new BodyLingeringPotionItem(properties, true));
    public static final Item SOUL_PROJECTION_ARROW = register("soul_projection_arrow", properties -> new ArrowItem(potionProperties(properties.stacksTo(64), false).component(DataComponents.POTION_DURATION_SCALE, 0.125F)));
    public static final Item PERSONALITY_EXPULSION_ARROW = register("personality_expulsion_arrow", properties -> new ArrowItem(potionProperties(properties.stacksTo(64), true).component(DataComponents.POTION_DURATION_SCALE, 0.125F)));
    public static final Item ZIPPER = register("zipper", ZipperItem::new);

    private TSFItems() {
    }

    private static Item register(String name, Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TSF.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key,
                factory.apply(new Item.Properties().setId(key).stacksTo(1)));
    }

    public static void initialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TSF.id("main"), FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.tsf"))
                .icon(PHONE::getDefaultInstance)
                .displayItems((parameters, output) -> {
                    output.accept(PHONE);
                    output.accept(POSSESSION_SPEAR);
                    output.accept(SNATCHING_SPEAR);
                    output.accept(SKIN_KNIFE);
                    output.accept(SKIN_SUIT);
                    output.accept(EMPTY_BODY);
                    output.accept(BODY_REGENERATION_TOTEM);
                    output.accept(SOUL_PROJECTION_POTION);
                    output.accept(PERSONALITY_EXPULSION_POTION);
                    output.accept(SOUL_PROJECTION_SPLASH_POTION);
                    output.accept(PERSONALITY_EXPULSION_SPLASH_POTION);
                    output.accept(SOUL_PROJECTION_LINGERING_POTION);
                    output.accept(PERSONALITY_EXPULSION_LINGERING_POTION);
                    output.accept(SOUL_PROJECTION_ARROW);
                    output.accept(PERSONALITY_EXPULSION_ARROW);
                    output.accept(ZIPPER);
                })
                .build());
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(entries -> {
            entries.accept(SOUL_PROJECTION_POTION);
            entries.accept(PERSONALITY_EXPULSION_POTION);
            entries.accept(SOUL_PROJECTION_SPLASH_POTION);
            entries.accept(PERSONALITY_EXPULSION_SPLASH_POTION);
            entries.accept(SOUL_PROJECTION_LINGERING_POTION);
            entries.accept(PERSONALITY_EXPULSION_LINGERING_POTION);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(entries -> {
            entries.accept(SOUL_PROJECTION_ARROW);
            entries.accept(PERSONALITY_EXPULSION_ARROW);
        });
        for (Item item : List.of(SOUL_PROJECTION_SPLASH_POTION, PERSONALITY_EXPULSION_SPLASH_POTION,
                SOUL_PROJECTION_LINGERING_POTION, PERSONALITY_EXPULSION_LINGERING_POTION,
                SOUL_PROJECTION_ARROW, PERSONALITY_EXPULSION_ARROW)) {
            DispenserBlock.registerProjectileBehavior(item);
        }
    }

    private static Item.Properties potionProperties(Item.Properties properties, boolean expulsion) {
        return properties.component(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(),
                        Optional.of(expulsion ? 0x76C853 : 0x81C6ED),
                        List.of(new MobEffectInstance(expulsion ? BodyPotionEffects.PERSONALITY_EXPULSION : BodyPotionEffects.SOUL_PROJECTION, 1)), Optional.empty()))
                .component(DataComponents.LORE, new ItemLore(List.of(Component.translatable(expulsion
                        ? "tooltip.tsf.personality_expulsion_potion" : "tooltip.tsf.soul_projection_potion"))));
    }

    private static final class SkinSuitItem extends Item {
        private SkinSuitItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer serverPlayer) {
                if (!player.isShiftKeyDown()) {
                    BodyManager.get().wearSkin(serverPlayer, player.getItemInHand(hand));
                }
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            if (!context.isSecondaryUseActive()) {
                return InteractionResult.PASS;
            }
            if (context.getPlayer() instanceof ServerPlayer player) {
                return BodyManager.get().placeSkin(player, context) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
            return InteractionResult.SUCCESS;
        }
    }

    private static final class EmptyBodyItem extends Item {
        private EmptyBodyItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            if (context.getPlayer() instanceof ServerPlayer player) {
                return BodyManager.get().placeBody(player, context) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
            return InteractionResult.SUCCESS;
        }
    }

    private static final class BodyRegenerationTotemItem extends Item {
        private BodyRegenerationTotemItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer serverPlayer) {
                BodyManager.get().regenerateBody(serverPlayer, player.getItemInHand(hand));
            }
            return InteractionResult.SUCCESS;
        }
    }

    private static final class BodyPotionItem extends Item {
        private final boolean expulsion;

        private BodyPotionItem(Properties properties, boolean expulsion) {
            super(potionProperties(properties, expulsion).component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)
                    .usingConvertsTo(Items.GLASS_BOTTLE));
            this.expulsion = expulsion;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer serverPlayer && !BodyManager.get().canDrinkBodyPotion(serverPlayer, expulsion)) {
                return InteractionResult.FAIL;
            }
            return super.use(level, player, hand);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            if (entity instanceof ServerPlayer player) {
                if (!BodyManager.get().canDrinkBodyPotion(player, expulsion)) {
                    return stack;
                }
            }
            return super.finishUsingItem(stack, level, entity);
        }
    }

    private static final class BodySplashPotionItem extends SplashPotionItem {
        private BodySplashPotionItem(Properties properties, boolean expulsion) {
            super(potionProperties(properties, expulsion));
        }

        @Override
        public ItemStack getDefaultInstance() {
            return new ItemStack(this);
        }

        @Override
        public Component getName(ItemStack stack) {
            return Component.translatable(descriptionId);
        }
    }

    private static final class BodyLingeringPotionItem extends LingeringPotionItem {
        private BodyLingeringPotionItem(Properties properties, boolean expulsion) {
            super(potionProperties(properties, expulsion));
        }

        @Override
        public ItemStack getDefaultInstance() {
            return new ItemStack(this);
        }

        @Override
        public Component getName(ItemStack stack) {
            return Component.translatable(descriptionId);
        }
    }

    private static final class ZipperItem extends Item {
        private ZipperItem(Properties properties) {
            super(properties.component(DataComponents.LORE, new ItemLore(List.of(
                    Component.translatable("tooltip.tsf.zipper_slot"), Component.translatable("tooltip.tsf.zipper")))));
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer serverPlayer) {
                BodyManager.get().equipZipper(serverPlayer, player.getItemInHand(hand));
            }
            return InteractionResult.SUCCESS;
        }
    }
}
