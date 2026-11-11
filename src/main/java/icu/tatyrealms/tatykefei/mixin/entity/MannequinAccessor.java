package icu.tatyrealms.tatykefei.mixin.entity;

import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mannequin.class)
public interface MannequinAccessor {
    @Invoker("setProfile")
    void tsf$setProfile(ResolvableProfile profile);

    @Invoker("setImmovable")
    void tsf$setImmovable(boolean immovable);

    @Invoker("setHideDescription")
    void tsf$setHideDescription(boolean hidden);
}
