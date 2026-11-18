package icu.tatyrealms.tatykefei.block;

import icu.tatyrealms.tatykefei.TSF;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.StringUtil;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class BodyDisplayBlockEntity extends BlockEntity {
    public static final int MAX_NAME_LENGTH = 64;
    public static final BlockEntityType<BodyDisplayBlockEntity> TYPE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, TSF.id("body_display"),
            new BlockEntityType<>(BodyDisplayBlockEntity::new, Set.of(BodyDisplayBlock.INSTANCE)));
    private boolean showName;
    private boolean poseable = true;
    private boolean showCape = true;
    private String face = "";
    private String displayName = "";
    private float horizontalOffset;
    private float verticalOffset;
    private float depthOffset;

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

    public boolean showCape() {
        return showCape;
    }

    public String face() {
        return face;
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String name) {
        displayName = sanitizeName(name);
        setChanged();
    }

    private static String sanitizeName(String name) {
        return StringUtil.truncateStringIfNecessary(StringUtil.filterText(name).strip(), MAX_NAME_LENGTH, false);
    }

    public float horizontalOffset() {
        return horizontalOffset;
    }

    public float verticalOffset() {
        return verticalOffset;
    }

    public float depthOffset() {
        return depthOffset;
    }

    public void setOffsets(float horizontal, float vertical, float depth) {
        horizontalOffset = clampOffset(horizontal, BodyDisplayBlock.MAX_HORIZONTAL_OFFSET);
        verticalOffset = clampOffset(vertical, BodyDisplayBlock.MAX_VERTICAL_OFFSET);
        depthOffset = clampOffset(depth, BodyDisplayBlock.MAX_DEPTH_OFFSET);
        setChanged();
    }

    private static float clampOffset(float offset, float limit) {
        return Float.isFinite(offset) ? Math.clamp(offset, -limit, limit) : 0;
    }

    public boolean hideStand() {
        return getBlockState().getValue(BodyDisplayBlock.HIDDEN);
    }

    public void configure(boolean showName, boolean poseable, boolean showCape, String face, boolean hideStand) {
        this.showName = showName;
        this.poseable = poseable;
        this.showCape = showCape;
        this.face = face == null ? "" : face;
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            BlockState updated = state.setValue(BodyDisplayBlock.HIDDEN, hideStand);
            if (state != updated) {
                level.setBlock(worldPosition, updated, Block.UPDATE_ALL);
            } else {
                level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("show_name", showName);
        output.putBoolean("poseable", poseable);
        output.putBoolean("show_cape", showCape);
        output.putString("face", face);
        output.putString("display_name", displayName);
        output.putFloat("horizontal_offset", horizontalOffset);
        output.putFloat("vertical_offset", verticalOffset);
        output.putFloat("depth_offset", depthOffset);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        showName = input.getBooleanOr("show_name", false);
        poseable = input.getBooleanOr("poseable", true);
        showCape = input.getBooleanOr("show_cape", true);
        face = input.getStringOr("face", "");
        displayName = sanitizeName(input.getStringOr("display_name", ""));
        horizontalOffset = clampOffset(input.getFloatOr("horizontal_offset", 0), BodyDisplayBlock.MAX_HORIZONTAL_OFFSET);
        verticalOffset = clampOffset(input.getFloatOr("vertical_offset", 0), BodyDisplayBlock.MAX_VERTICAL_OFFSET);
        depthOffset = clampOffset(input.getFloatOr("depth_offset", 0), BodyDisplayBlock.MAX_DEPTH_OFFSET);
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
