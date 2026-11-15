package icu.tatyrealms.tatykefei.item;

import com.mojang.serialization.MapCodec;
import icu.tatyrealms.tatykefei.TSF;
import java.util.Optional;
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
            MapCodec.unit(LatexBrewingRecipe::new), StreamCodec.unit(new LatexBrewingRecipe()));

    public LatexBrewingRecipe() {
        super(new PotionIngredient(Ingredient.of(Items.POTION, TSFItems.LATEX_POTION), Optional.empty()),
                PotionIngredient.of(Items.POTION), new ItemStackTemplate(TSFItems.LATEX_POTION));
    }

    public static void initialize() {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, TSF.id("latex_brewing"), SERIALIZER);
    }

    @Override
    public boolean matches(BrewingInput input) {
        var source = LatexPotions.reagentSource(input.reagent());
        if (source.isEmpty()) {
            return false;
        }
        ItemStack potion = input.input();
        if (potion.is(TSFItems.LATEX_POTION)) {
            return LatexPotions.source(potion).map(previous -> !previous.uuid().equals(source.get().uuid())
                    || LatexPotions.level(potion) < LatexPotions.MAX_LEVEL).orElse(false);
        }
        return potion.is(Items.POTION) && potion.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.OOZING);
    }

    @Override
    public ItemStack assemble(BrewingInput input) {
        if (!matches(input)) {
            return ItemStack.EMPTY;
        }
        var source = LatexPotions.reagentSource(input.reagent()).orElseThrow();
        int level = LatexPotions.source(input.input()).filter(previous -> previous.uuid().equals(source.uuid()))
                .map(previous -> LatexPotions.level(input.input()) + 1).orElse(1);
        var profile = level > 1 ? input.input().getOrDefault(DataComponents.PROFILE, LatexPotions.profile(source))
                : LatexPotions.profile(source);
        return LatexPotions.create(source, profile, level);
    }

    @Override
    public RecipeSerializer<BrewingRecipe> getSerializer() {
        return SERIALIZER;
    }
}
