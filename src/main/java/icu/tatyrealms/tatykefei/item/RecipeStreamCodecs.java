package icu.tatyrealms.tatykefei.item;

import java.util.function.Supplier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 无状态配方的网络编解码器。StreamCodec.unit 只接受同一个实例，
 * 配方同步给客户端时会因为实例不同抛出异常，因此这里不写入数据、解码时新建实例。
 */
final class RecipeStreamCodecs {
    private RecipeStreamCodecs() {
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, T> empty(Supplier<T> factory) {
        return StreamCodec.of((buffer, recipe) -> {
        }, buffer -> factory.get());
    }
}
