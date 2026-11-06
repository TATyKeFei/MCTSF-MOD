package icu.tatyrealms.tatykefei.network;

import icu.tatyrealms.tatykefei.TSF;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SkinLayersPayload(String json) implements CustomPacketPayload {
    public static final Type<SkinLayersPayload> TYPE = new Type<>(TSF.id("skin_layers"));
    public static final StreamCodec<FriendlyByteBuf, SkinLayersPayload> CODEC = CustomPacketPayload.codec(
            (payload, buffer) -> buffer.writeUtf(payload.json(), 65536),
            buffer -> new SkinLayersPayload(buffer.readUtf(65536)));

    @Override
    public Type<SkinLayersPayload> type() {
        return TYPE;
    }

    public record Entry(UUID body, String name, int slot) {
    }

    public record Snapshot(List<Entry> layers, List<Entry> available, int limit) {
    }
}
