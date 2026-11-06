package icu.tatyrealms.tatykefei.network;

import icu.tatyrealms.tatykefei.TSF;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HypnosisProgressPayload(String targetName, int elapsed, int duration) implements CustomPacketPayload {
    public static final int DURATION_TICKS = 60;
    public static final Type<HypnosisProgressPayload> TYPE = new Type<>(TSF.id("hypnosis_progress"));
    public static final StreamCodec<FriendlyByteBuf, HypnosisProgressPayload> CODEC = CustomPacketPayload.codec(
            (payload, buffer) -> {
                buffer.writeUtf(payload.targetName(), 256);
                buffer.writeVarInt(payload.elapsed());
                buffer.writeVarInt(payload.duration());
            }, buffer -> new HypnosisProgressPayload(buffer.readUtf(256), buffer.readVarInt(), buffer.readVarInt()));

    @Override
    public Type<HypnosisProgressPayload> type() {
        return TYPE;
    }
}
