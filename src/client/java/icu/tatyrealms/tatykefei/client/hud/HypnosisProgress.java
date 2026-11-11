package icu.tatyrealms.tatykefei.client.hud;

import icu.tatyrealms.tatykefei.item.TSFItems;
import icu.tatyrealms.tatykefei.network.HypnosisProgressPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class HypnosisProgress {
    private static HypnosisProgressPayload progress;
    private static long receivedAt;

    private HypnosisProgress() {
    }

    public static void receive(HypnosisProgressPayload update) {
        var level = Minecraft.getInstance().level;
        if (update.duration() <= 0 || level == null) {
            clear();
        } else {
            progress = update;
            receivedAt = level.getGameTime();
        }
    }

    public static void clear() {
        progress = null;
    }

    public static void tick(Minecraft client) {
        if (client.player == null || client.level == null || !client.player.isAlive()
                || !client.player.isUsingItem() || !client.player.getUseItem().is(TSFItems.PHONE)
                || progress != null && client.level.getGameTime() - receivedAt > 20) {
            clear();
        }
    }

    public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (progress == null || client.level == null || client.player == null || !client.player.isUsingItem()
                || !client.player.getUseItem().is(TSFItems.PHONE)) {
            return;
        }
        float elapsed = progress.elapsed() + client.level.getGameTime() - receivedAt + delta.getGameTimeDeltaPartialTick(false);
        float fraction = Mth.clamp(elapsed / progress.duration(), 0.0F, 1.0F);
        int barWidth = Math.min(180, graphics.guiWidth() - 24);
        int left = (graphics.guiWidth() - barWidth) / 2;
        int top = graphics.guiHeight() / 2 + 34;
        graphics.fill(left - 6, top - 18, left + barWidth + 6, top + 25, 0xC0101A2A);
        graphics.fill(left - 1, top - 1, left + barWidth + 1, top + 9, 0xFF8CA4C2);
        graphics.fill(left, top, left + barWidth, top + 8, 0xFF21334E);
        graphics.fill(left, top, left + (int) (barWidth * fraction), top + 8, 0xFF9575ED);
        String label = Component.translatable("screen.tsf.hypnotizing", progress.targetName()).getString();
        graphics.centeredText(client.font, client.font.plainSubstrByWidth(label, barWidth), graphics.guiWidth() / 2,
                top - 13, 0xFFE6F2FF);
        graphics.centeredText(client.font, Component.translatable("screen.tsf.hypnosis_percent", (int) (fraction * 100)),
                graphics.guiWidth() / 2, top + 13, 0xFFB5D6F4);
    }
}
