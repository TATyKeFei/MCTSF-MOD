package icu.tatyrealms.tatykefei.item;

import com.mojang.serialization.MapCodec;
import icu.tatyrealms.tatykefei.TSF;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class LatexArrowRecipe extends CustomRecipe {
    public static final RecipeSerializer<LatexArrowRecipe> SERIALIZER = new RecipeSerializer<>(
            MapCodec.unit(LatexArrowRecipe::new), RecipeStreamCodecs.empty(LatexArrowRecipe::new));

    public static void initialize() {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, TSF.id("latex_arrow"), SERIALIZER);
        // 声明该序列化器的配方需要同步到客户端，JEI 等配方查看器才能读取
        RecipeSynchronization.synchronizeRecipeSerializer(SERIALIZER);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return matchesInput(input);
    }

    private boolean matchesInput(CraftingInput input) {
        if (input.width() != 3 || input.height() != 3) {
            return false;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = input.getItem(slot);
            if (slot == 4 ? !stack.is(TSFItems.LATEX_LINGERING_POTION) || LatexPotions.source(stack).isEmpty()
                    : !stack.is(Items.ARROW)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        if (!matchesInput(input)) {
            return ItemStack.EMPTY;
        }
        return LatexPotions.convert(input.getItem(4), TSFItems.LATEX_ARROW).copyWithCount(8);
    }

    @Override
    public RecipeSerializer<LatexArrowRecipe> getSerializer() {
        return SERIALIZER;
    }
}
