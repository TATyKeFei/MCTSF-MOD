package icu.tatyrealms.tatykefei.mixin.item;

import icu.tatyrealms.tatykefei.state.BodyCooldowns;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemCooldowns.class)
public abstract class ItemCooldownsMixin implements BodyCooldowns {
    @Shadow @Final
    private Map<Identifier, ?> cooldowns;
    @Shadow
    private int tickCount;

    @Override
    public Map<String, Integer> tsf$captureCooldowns() {
        Map<String, Integer> result = new HashMap<>();
        cooldowns.forEach((id, value) -> {
            int remaining = ((CooldownInstanceAccessor) value).tsf$getEndTime() - tickCount;
            if (remaining > 0) {
                result.put(id.toString(), remaining);
            }
        });
        return result;
    }

    @Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
    public interface CooldownInstanceAccessor {
        @Accessor("endTime")
        int tsf$getEndTime();
    }
}
