package icu.tatyrealms.tatykefei.mixin.compat;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import icu.tatyrealms.tatykefei.compat.needsofnature.NatureLiquidSourceHolder;
import icu.tatyrealms.tatykefei.compat.needsofnature.NatureLiquidSources;
import icu.tatyrealms.tatykefei.compat.needsofnature.NeedsOfNatureSources;
import icu.tatyrealms.tatykefei.compat.needsofnature.NeedsOfNatureTanks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 自然之需先把液体接口合入 Entity，再接入新增的方法。
@Mixin(value = Entity.class, priority = 900)
public abstract class NeedsOfNatureSourceEntityMixin implements NatureLiquidSourceHolder {
    @Unique
    private NatureLiquidSources tsf$liquidSources = NatureLiquidSources.UNKNOWN;

    @Override
    public NatureLiquidSources tsf$liquidSources() {
        return tsf$liquidSources;
    }

    @Override
    public void tsf$setLiquidSources(NatureLiquidSources sources) {
        tsf$liquidSources = sources;
    }

    @Inject(method = "setLiquidStored", at = @At("HEAD"), remap = false, require = 0)
    private void tsf$beforeLiquidChange(int amount, CallbackInfo callback,
            @Share("tsf$stored") LocalIntRef previous) {
        previous.set(NeedsOfNatureTanks.stored(this));
    }

    @Inject(method = "setLiquidStored", at = @At("TAIL"), remap = false, require = 0)
    private void tsf$changedLiquid(int amount, CallbackInfo callback,
            @Share("tsf$stored") LocalIntRef previous) {
        int stored = NeedsOfNatureTanks.stored(this);
        if (stored <= 0) {
            tsf$liquidSources = NatureLiquidSources.EMPTY;
        } else if (stored > previous.get()) {
            // 已知供体的增加会在 addLiquidWithComposition 返回时替换此占位记录。
            tsf$liquidSources = previous.get() <= 0 ? NatureLiquidSources.UNKNOWN
                    : tsf$liquidSources.merge(NatureLiquidSources.UNKNOWN);
        }
    }

    @Inject(method = {"clearLiquidComposition", "setLiquidCompositionHoney"}, at = @At("TAIL"), remap = false, require = 0)
    private void tsf$clearNonLiquidSources(CallbackInfo callback) {
        tsf$liquidSources = NatureLiquidSources.EMPTY;
    }

    @Inject(method = "saveWithoutId", at = @At("TAIL"))
    private void tsf$saveSources(ValueOutput output, CallbackInfo callback) {
        if ((Object) this instanceof ServerPlayer && NeedsOfNatureTanks.stored(this) > 0) {
            output.store(NeedsOfNatureSources.DATA_KEY, NatureLiquidSources.CODEC, tsf$liquidSources);
        }
    }

    // 等自然之需还原液体后再读取来源，避免初始化液体时清掉已读取的数据。
    @Inject(method = "load", at = @At("TAIL"), order = 1500)
    private void tsf$loadSources(ValueInput input, CallbackInfo callback) {
        if ((Object) this instanceof ServerPlayer) {
            tsf$liquidSources = input.read(NeedsOfNatureSources.DATA_KEY, NatureLiquidSources.CODEC)
                    .orElse(NatureLiquidSources.UNKNOWN);
        }
    }
}
