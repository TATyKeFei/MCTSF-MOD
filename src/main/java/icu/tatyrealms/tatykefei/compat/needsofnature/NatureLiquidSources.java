package icu.tatyrealms.tatykefei.compat.needsofnature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/** 保存已知玩家来源；other 表示还含有动物或无法追溯的来源。 */
public record NatureLiquidSources(List<Source> players, boolean other) {
    public static final NatureLiquidSources EMPTY = new NatureLiquidSources(List.of(), false);
    public static final NatureLiquidSources UNKNOWN = new NatureLiquidSources(List.of(), true);
    public static final Codec<NatureLiquidSources> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Source.CODEC.listOf().fieldOf("players").forGetter(NatureLiquidSources::players),
            Codec.BOOL.fieldOf("other").forGetter(NatureLiquidSources::other)
    ).apply(instance, NatureLiquidSources::new));

    public NatureLiquidSources {
        players = List.copyOf(players);
    }

    public NatureLiquidSources merge(NatureLiquidSources incoming) {
        var combined = new LinkedHashMap<UUID, Source>();
        players.forEach(source -> combined.put(source.uuid(), source));
        incoming.players.forEach(source -> combined.put(source.uuid(), source));
        return new NatureLiquidSources(List.copyOf(combined.values()), other || incoming.other);
    }

    public record Source(UUID uuid, String name) {
        public static final Codec<Source> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.STRING_CODEC.fieldOf("uuid").forGetter(Source::uuid),
                Codec.string(1, 64).fieldOf("name").forGetter(Source::name)
        ).apply(instance, Source::new));
    }
}
