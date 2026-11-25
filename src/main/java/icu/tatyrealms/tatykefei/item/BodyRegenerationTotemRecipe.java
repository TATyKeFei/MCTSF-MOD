package icu.tatyrealms.tatykefei.item;

import com.mojang.serialization.MapCodec;
import icu.tatyrealms.tatykefei.TSF;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** 肉体再生图腾：任意玩家来源的精液瓶、金胡萝卜和骨头无序合成。 */
public final class BodyRegenerationTotemRecipe extends CustomRecipe {
    public static final RecipeSerializer<BodyRegenerationTotemRecipe> SERIALIZER = new RecipeSerializer<>(
            MapCodec.unit(BodyRegenerationTotemRecipe::new), StreamCodec.unit(new BodyRegenerationTotemRecipe()));

    public static void initialize() {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, TSF.id("body_regeneration_totem"), SERIALIZER);
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
            if (LatexPotions.reagentSource(stack).isPresent()) {
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
    public ItemStack assemble(CraftingInput input) {
        return matchesInput(input) ? new ItemStack(TSFItems.BODY_REGENERATION_TOTEM) : ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<BodyRegenerationTotemRecipe> getSerializer() {
        return SERIALIZER;
    }
}
