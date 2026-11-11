package icu.tatyrealms.tatykefei.block;

import icu.tatyrealms.tatykefei.TSF;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class BodyDisplayBlockEntity extends BlockEntity {
    public static final BlockEntityType<BodyDisplayBlockEntity> TYPE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, TSF.id("body_display"),
            new BlockEntityType<>(BodyDisplayBlockEntity::new, Set.of(BodyDisplayBlock.INSTANCE)));
    private boolean showName;
    private boolean poseable = true;

    public BodyDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    public static void initialize() {
    }

    public boolean showName() {
        return showName;
    }

    public boolean poseable() {
        return poseable;
    }

    public void configure(boolean showName, boolean poseable) {
        this.showName = showName;
        this.poseable = poseable;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("show_name", showName);
        output.putBoolean("poseable", poseable);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        showName = input.getBooleanOr("show_name", false);
        poseable = input.getBooleanOr("poseable", true);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
