package icu.tatyrealms.tatykefei.block;

import icu.tatyrealms.tatykefei.TSF;
import icu.tatyrealms.tatykefei.state.BodyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BodyDisplayBlock extends FaceAttachedHorizontalDirectionalBlock {
    public static final BodyDisplayBlock INSTANCE = register();

    private static BodyDisplayBlock register() {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, TSF.id("body_display"));
        return Registry.register(BuiltInRegistries.BLOCK, key, new BodyDisplayBlock(BlockBehaviour.Properties.of()
                .setId(key).strength(2.0F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.IMMOVEABLE)));
    }

    private BodyDisplayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACE, AttachFace.FLOOR).setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        BlockState state = defaultBlockState().setValue(FACE, face == Direction.UP ? AttachFace.FLOOR
                : face == Direction.DOWN ? AttachFace.CEILING : AttachFace.WALL)
                .setValue(FACING, face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> Shapes.or(Block.box(2, 0, 2, 14, 2, 14), Block.box(7, 2, 7, 9, 16, 9));
            case CEILING -> Shapes.or(Block.box(2, 14, 2, 14, 16, 14), Block.box(7, 0, 7, 9, 14, 9));
            case WALL -> switch (state.getValue(FACING)) {
                case NORTH -> Shapes.or(Block.box(2, 2, 14, 14, 14, 16), Block.box(7, 7, 3, 9, 9, 14));
                case SOUTH -> Shapes.or(Block.box(2, 2, 0, 14, 14, 2), Block.box(7, 7, 2, 9, 9, 13));
                case WEST -> Shapes.or(Block.box(14, 2, 2, 16, 14, 14), Block.box(3, 7, 7, 14, 9, 9));
                default -> Shapes.or(Block.box(0, 2, 2, 2, 14, 14), Block.box(2, 7, 7, 13, 9, 9));
            };
        };
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        return interact(level, pos, player, hand);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return interact(level, pos, player, InteractionHand.MAIN_HAND);
    }

    private InteractionResult interact(Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer actor) {
            BodyManager.get().interactDisplay(actor, pos, hand);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (BodyManager.get() != null && !level.getBlockState(pos).is(this)) {
            BodyManager.get().removeDisplay(level, pos);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING);
    }
}
