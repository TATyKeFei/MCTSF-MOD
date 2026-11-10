package icu.tatyrealms.tatykefei.network;

import icu.tatyrealms.tatykefei.TSF;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** START selects by the server's ray; UPDATE only supplies the requested grab distance. */
public record BodyDragPayload(int entity, int operation, float distance) implements CustomPacketPayload {
    public static final int START = 0;
    public static final int UPDATE = 1;
    public static final int STOP = 2;
    public static final Type<BodyDragPayload> TYPE = new Type<>(TSF.id("body_drag"));
    public static final StreamCodec<FriendlyByteBuf, BodyDragPayload> CODEC = CustomPacketPayload.codec(
            (payload, buffer) -> {
                buffer.writeVarInt(payload.entity());
                buffer.writeByte(payload.operation());
                buffer.writeFloat(payload.distance());
            }, buffer -> new BodyDragPayload(buffer.readVarInt(), buffer.readUnsignedByte(), buffer.readFloat()));

    @Override
    public Type<BodyDragPayload> type() {
        return TYPE;
    }
}
