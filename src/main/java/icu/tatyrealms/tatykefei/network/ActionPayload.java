package icu.tatyrealms.tatykefei.network;

import icu.tatyrealms.tatykefei.TSF;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ActionPayload(Action action, String target) implements CustomPacketPayload {
    public static final Type<ActionPayload> TYPE = new Type<>(TSF.id("action"));
    public static final StreamCodec<FriendlyByteBuf, ActionPayload> CODEC = CustomPacketPayload.codec(
            (payload, buffer) -> {
                buffer.writeEnum(payload.action());
                buffer.writeUtf(payload.target(), 36);
            },
            buffer -> new ActionPayload(buffer.readEnum(Action.class), buffer.readUtf(36)));

    @Override
    public Type<ActionPayload> type() {
        return TYPE;
    }
}
