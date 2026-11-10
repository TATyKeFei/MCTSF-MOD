package icu.tatyrealms.tatykefei.item;

import com.mojang.serialization.JsonOps;
import java.util.Arrays;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.component.ItemLore;

public final class ItemLoreText {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final GsonComponentSerializer SERIALIZER = GsonComponentSerializer.gson();

    private ItemLoreText() {
    }

    public static ItemLore translated(String... keys) {
        return new ItemLore(Arrays.stream(keys).map(key -> {
            // Keep the translation unresolved so each client uses its own language.
            var text = MINI_MESSAGE.deserialize("<gray><italic:false><lang:" + key + "></italic></gray>");
            return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, SERIALIZER.serializeToTree(text)).getOrThrow();
        }).toList());
    }
}
