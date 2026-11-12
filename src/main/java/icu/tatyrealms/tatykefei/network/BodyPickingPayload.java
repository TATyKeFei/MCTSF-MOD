package icu.tatyrealms.tatykefei.network;

import icu.tatyrealms.tatykefei.TSF;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BodyPickingPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<BodyPickingPayload> TYPE = new Type<>(TSF.id("body_picking"));
    public static final StreamCodec<FriendlyByteBuf, BodyPickingPayload> CODEC = CustomPacketPayload.codec(
            (payload, buffer) -> buffer.writeBoolean(payload.enabled()), buffer -> new BodyPickingPayload(buffer.readBoolean()));

    @Override
    public Type<BodyPickingPayload> type() {
        return TYPE;
    }
}
