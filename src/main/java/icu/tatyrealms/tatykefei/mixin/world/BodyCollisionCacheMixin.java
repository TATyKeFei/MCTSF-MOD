package icu.tatyrealms.tatykefei.mixin.world;

import icu.tatyrealms.tatykefei.body.BodyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class BodyCollisionCacheMixin {
    @Shadow
    public abstract Level getLevel();

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void tsf$invalidateBodyCollisions(BlockPos pos, BlockState state, int flags,
            CallbackInfoReturnable<BlockState> callback) {
        if (callback.getReturnValue() != null && getLevel() instanceof ServerLevel level && BodyManager.get() != null) {
            BodyManager.get().bodyBlockChanged(level, pos);
        }
    }
}
