package icu.tatyrealms.tatykefei.network;

import icu.tatyrealms.tatykefei.TSF;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ObserverPayload(String json) implements CustomPacketPayload {
    public static final Type<ObserverPayload> TYPE = new Type<>(TSF.id("observer_inventory"));
    public static final StreamCodec<FriendlyByteBuf, ObserverPayload> CODEC = CustomPacketPayload.codec(
            (payload, buffer) -> buffer.writeUtf(payload.json(), 262144),
            buffer -> new ObserverPayload(buffer.readUtf(262144)));

    @Override
    public Type<ObserverPayload> type() {
        return TYPE;
    }
}
