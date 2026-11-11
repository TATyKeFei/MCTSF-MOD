package icu.tatyrealms.tatykefei.mixin.world;

import icu.tatyrealms.tatykefei.body.BodyManager;
import icu.tatyrealms.tatykefei.state.Form;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityCollisionContext.class)
public abstract class EntityCollisionContextMixin {
    @Shadow @Final
    private Entity entity;

    @Inject(method = "getCollisionShape", at = @At("RETURN"), cancellable = true)
    private void tsf$spiritCollision(BlockState state, CollisionGetter level, BlockPos position,
                                     CallbackInfoReturnable<VoxelShape> callback) {
        if (entity instanceof Player player && BodyManager.form(player) == Form.SPIRIT
                && !Block.isShapeFullBlock(callback.getReturnValue())) {
            callback.setReturnValue(Shapes.empty());
        }
    }
}
