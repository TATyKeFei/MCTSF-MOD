package icu.tatyrealms.tatykefei.item;

import com.mojang.serialization.MapCodec;
import icu.tatyrealms.tatykefei.TSF;
import java.util.Optional;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PotionIngredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

public final class LatexBrewingRecipe extends BrewingRecipe {
    public static final RecipeSerializer<BrewingRecipe> SERIALIZER = new RecipeSerializer<>(
            MapCodec.unit(LatexBrewingRecipe::new), RecipeStreamCodecs.empty(LatexBrewingRecipe::new));

    public LatexBrewingRecipe() {
        super(new PotionIngredient(Ingredient.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION,
                        TSFItems.LATEX_POTION, TSFItems.LATEX_SPLASH_POTION, TSFItems.LATEX_LINGERING_POTION), Optional.empty()),
                new PotionIngredient(Ingredient.of(Items.POTION, Items.GUNPOWDER, Items.DRAGON_BREATH), Optional.empty()),
                new ItemStackTemplate(TSFItems.LATEX_POTION));
    }

    public static void initialize() {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, TSF.id("latex_brewing"), SERIALIZER);
        // 声明该序列化器的配方需要同步到客户端，JEI 等配方查看器才能读取
        RecipeSynchronization.synchronizeRecipeSerializer(SERIALIZER);
    }

    @Override
    public boolean matches(BrewingInput input) {
        if (input.reagent().is(Items.GUNPOWDER)) {
            return input.input().is(TSFItems.LATEX_POTION) && LatexPotions.source(input.input()).isPresent();
        }
        if (input.reagent().is(Items.DRAGON_BREATH)) {
            return input.input().is(TSFItems.LATEX_SPLASH_POTION) && LatexPotions.source(input.input()).isPresent();
        }
        var source = LatexPotions.reagentSource(input.reagent());
        if (source.isEmpty()) {
            return false;
        }
        ItemStack potion = input.input();
        if (LatexPotions.isLatexItem(potion) && !potion.is(TSFItems.LATEX_ARROW)) {
            return LatexPotions.source(potion).map(previous -> !previous.uuid().equals(source.get().uuid())
                    || LatexPotions.level(potion) < LatexPotions.MAX_LEVEL).orElse(false);
        }
        return (potion.is(Items.POTION) || potion.is(Items.SPLASH_POTION) || potion.is(Items.LINGERING_POTION))
                && potion.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.OOZING);
    }

    @Override
    public ItemStack assemble(BrewingInput input) {
        if (!matches(input)) {
            return ItemStack.EMPTY;
        }
        if (input.reagent().is(Items.GUNPOWDER)) {
            return LatexPotions.convert(input.input(), TSFItems.LATEX_SPLASH_POTION);
        }
        if (input.reagent().is(Items.DRAGON_BREATH)) {
            return LatexPotions.convert(input.input(), TSFItems.LATEX_LINGERING_POTION);
        }
        var source = LatexPotions.reagentSource(input.reagent()).orElseThrow();
        int level = LatexPotions.source(input.input()).filter(previous -> previous.uuid().equals(source.uuid()))
                .map(previous -> LatexPotions.level(input.input()) + 1).orElse(1);
        var profile = level > 1 ? input.input().getOrDefault(DataComponents.PROFILE, LatexPotions.profile(source))
                : LatexPotions.profile(source);
        return LatexPotions.create(source, profile, level, LatexPotions.potionType(input.input()));
    }

    @Override
    public RecipeSerializer<BrewingRecipe> getSerializer() {
        return SERIALIZER;
    }
}
