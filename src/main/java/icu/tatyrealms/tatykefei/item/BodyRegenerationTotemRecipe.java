package icu.tatyrealms.tatykefei.item;

import com.mojang.serialization.MapCodec;
import icu.tatyrealms.tatykefei.TSF;
import java.util.List;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

/** 肉体再生图腾：任意玩家来源的精液瓶、金胡萝卜和骨头无序合成。 */
public final class BodyRegenerationTotemRecipe extends CustomRecipe {
    public static final RecipeSerializer<BodyRegenerationTotemRecipe> SERIALIZER = new RecipeSerializer<>(
            MapCodec.unit(BodyRegenerationTotemRecipe::new), StreamCodec.unit(new BodyRegenerationTotemRecipe()));

    public static void initialize() {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, TSF.id("body_regeneration_totem"), SERIALIZER);
        // 声明该序列化器的配方需要同步到客户端，JEI 等配方查看器才能读取
        RecipeSynchronization.synchronizeRecipeSerializer(SERIALIZER);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return matchesInput(input);
    }

    private boolean matchesInput(CraftingInput input) {
        int potions = 0;
        int carrots = 0;
        int bones = 0;
        for (int slot = 0; slot < input.width() * input.height(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (LatexPotions.isPlayerLiquidBottle(stack)) {
                potions++;
            } else if (stack.is(Items.GOLDEN_CARROT)) {
                carrots++;
            } else if (stack.is(Items.BONE)) {
                bones++;
            } else {
                return false;
            }
        }
        return potions == 1 && carrots == 1 && bones == 1;
    }

    @Override
    public boolean isSpecial() {
        // 声明为非特殊配方，JEI 等配方查看器才会把它当作普通无序配方显示
        return false;
    }

    @Override
    public PlacementInfo placementInfo() {
        // 提供可摆放的材料表，避免服务端加载时按空材料表把配方忽略
        return PlacementInfo.create(List.of(
                Ingredient.of(Items.POTION), Ingredient.of(Items.GOLDEN_CARROT), Ingredient.of(Items.BONE)));
    }

    @Override
    public List<RecipeDisplay> display() {
        ItemStack bottle = new ItemStack(Items.POTION);
        bottle.set(DataComponents.ITEM_NAME, Component.translatable("item.tsf.player_liquid_bottle"));
        return List.of(new ShapelessCraftingRecipeDisplay(
                List.of(
                        new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(bottle)),
                        new SlotDisplay.ItemSlotDisplay(Items.GOLDEN_CARROT),
                        new SlotDisplay.ItemSlotDisplay(Items.BONE)),
                new SlotDisplay.ItemStackSlotDisplay(
                        ItemStackTemplate.fromNonEmptyStack(new ItemStack(TSFItems.BODY_REGENERATION_TOTEM))),
                new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return matchesInput(input) ? new ItemStack(TSFItems.BODY_REGENERATION_TOTEM) : ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<BodyRegenerationTotemRecipe> getSerializer() {
        return SERIALIZER;
    }
}
