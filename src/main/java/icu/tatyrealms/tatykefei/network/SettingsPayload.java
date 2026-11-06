package icu.tatyrealms.tatykefei.network;

import icu.tatyrealms.tatykefei.TSF;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettingsPayload(String json, boolean editable) implements CustomPacketPayload {
    public static final Type<SettingsPayload> TYPE = new Type<>(TSF.id("settings"));
    public static final StreamCodec<FriendlyByteBuf, SettingsPayload> CODEC = CustomPacketPayload.codec(
            (payload, buffer) -> {
                buffer.writeUtf(payload.json(), 4096);
                buffer.writeBoolean(payload.editable());
            }, buffer -> new SettingsPayload(buffer.readUtf(4096), buffer.readBoolean()));

    @Override
    public Type<SettingsPayload> type() {
        return TYPE;
    }
}
