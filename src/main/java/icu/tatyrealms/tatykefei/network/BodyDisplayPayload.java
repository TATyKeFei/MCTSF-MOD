package icu.tatyrealms.tatykefei.network;

import icu.tatyrealms.tatykefei.TSF;
import icu.tatyrealms.tatykefei.block.BodyDisplayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BodyDisplayPayload(BlockPos pos, int operation, boolean showName, boolean poseable,
        boolean showCape, boolean hideStand, int entity, String face, String displayName, float offset)
        implements CustomPacketPayload {
    public static final int UPDATE = 0;
    public static final int EDIT = 1;
    public static final int RESET = 2;
    public static final int CLOSE = 3;
    public static final int STOP_EDIT = 4;
    public static final int SNAPSHOT = 5;
    public static final int BEGIN_EDIT = 6;
    public static final int END_EDIT = 7;
    public static final int OPEN = 8;
    public static final int RENAME = 9;
    public static final int MOVE_HORIZONTAL = 10;
    public static final int MOVE_VERTICAL = 11;
    public static final int MOVE_DEPTH = 12;
    public static final int RESET_POSITION = 13;
    public static final Type<BodyDisplayPayload> TYPE = new Type<>(TSF.id("body_display"));
    public static final StreamCodec<FriendlyByteBuf, BodyDisplayPayload> CODEC = CustomPacketPayload.codec(
            (payload, buffer) -> {
                buffer.writeBlockPos(payload.pos());
                buffer.writeByte(payload.operation());
                buffer.writeBoolean(payload.showName());
                buffer.writeBoolean(payload.poseable());
                buffer.writeBoolean(payload.showCape());
                buffer.writeBoolean(payload.hideStand());
                buffer.writeVarInt(payload.entity());
                buffer.writeUtf(payload.face());
                buffer.writeUtf(payload.displayName(), BodyDisplayBlockEntity.MAX_NAME_LENGTH);
                buffer.writeFloat(payload.offset());
            }, buffer -> new BodyDisplayPayload(buffer.readBlockPos(), buffer.readUnsignedByte(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt(),
                    buffer.readUtf(), buffer.readUtf(BodyDisplayBlockEntity.MAX_NAME_LENGTH), buffer.readFloat()));

    public static BodyDisplayPayload request(BlockPos pos, int operation) {
        return new BodyDisplayPayload(pos, operation, false, false, true, false, -1, "", "", 0);
    }

    public static BodyDisplayPayload rename(BlockPos pos, String name) {
        return new BodyDisplayPayload(pos, RENAME, false, false, true, false, -1, "", name, 0);
    }

    public static BodyDisplayPayload move(BlockPos pos, int operation, int entity, float offset) {
        return new BodyDisplayPayload(pos, operation, false, false, true, false, entity, "", "", offset);
    }

    @Override
    public Type<BodyDisplayPayload> type() {
        return TYPE;
    }
}
