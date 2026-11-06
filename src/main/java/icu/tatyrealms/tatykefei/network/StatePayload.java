package icu.tatyrealms.tatykefei.network;

import icu.tatyrealms.tatykefei.TSF;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record StatePayload(String json, boolean openPhone) implements CustomPacketPayload {
    public static final Type<StatePayload> TYPE = new Type<>(TSF.id("state"));
    public static final StreamCodec<FriendlyByteBuf, StatePayload> CODEC = CustomPacketPayload.codec(
            (payload, buffer) -> {
                buffer.writeUtf(payload.json(), 262144);
                buffer.writeBoolean(payload.openPhone());
            },
            buffer -> new StatePayload(buffer.readUtf(262144), buffer.readBoolean()));

    @Override
    public Type<StatePayload> type() {
        return TYPE;
    }
}
