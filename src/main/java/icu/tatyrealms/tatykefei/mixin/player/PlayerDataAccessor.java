package icu.tatyrealms.tatykefei.mixin.player;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Player.class)
public interface PlayerDataAccessor {
    @Accessor("enchantmentSeed")
    void tsf$setEnchantmentSeed(int seed);
}
