package icu.tatyrealms.tatykefei.network;

import icu.tatyrealms.tatykefei.TSF;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BodyDisplayPayload(BlockPos pos, int operation, boolean showName, boolean poseable,
        boolean showCape, boolean hideStand, int entity)
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
            }, buffer -> new BodyDisplayPayload(buffer.readBlockPos(), buffer.readUnsignedByte(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt()));

    @Override
    public Type<BodyDisplayPayload> type() {
        return TYPE;
    }
}
