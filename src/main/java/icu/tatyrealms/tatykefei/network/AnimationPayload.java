package icu.tatyrealms.tatykefei.network;

import icu.tatyrealms.tatykefei.TSF;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record AnimationPayload(UUID player, BodyAnimation animation) implements CustomPacketPayload {
    public static final Type<AnimationPayload> TYPE = new Type<>(TSF.id("animation"));
    public static final StreamCodec<FriendlyByteBuf, AnimationPayload> CODEC = CustomPacketPayload.codec(
            (payload, buffer) -> {
                buffer.writeUUID(payload.player());
                buffer.writeEnum(payload.animation());
            },
            buffer -> new AnimationPayload(buffer.readUUID(), buffer.readEnum(BodyAnimation.class)));

    @Override
    public Type<AnimationPayload> type() {
        return TYPE;
    }
}
