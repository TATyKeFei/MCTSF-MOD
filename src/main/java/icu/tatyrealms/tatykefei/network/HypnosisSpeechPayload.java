package icu.tatyrealms.tatykefei.network;

import icu.tatyrealms.tatykefei.TSF;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HypnosisSpeechPayload(UUID target, String message) implements CustomPacketPayload {
    public static final int MAX_LENGTH = 256;
    public static final Type<HypnosisSpeechPayload> TYPE = new Type<>(TSF.id("hypnosis_speech"));
    public static final StreamCodec<FriendlyByteBuf, HypnosisSpeechPayload> CODEC = CustomPacketPayload.codec(
            (payload, buffer) -> {
                buffer.writeUUID(payload.target());
                buffer.writeUtf(payload.message(), MAX_LENGTH);
            }, buffer -> new HypnosisSpeechPayload(buffer.readUUID(), buffer.readUtf(MAX_LENGTH)));

    @Override
    public Type<HypnosisSpeechPayload> type() {
        return TYPE;
    }
}
