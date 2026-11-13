package icu.tatyrealms.tatykefei.client.screen;

import icu.tatyrealms.tatykefei.client.TSFClient;
import icu.tatyrealms.tatykefei.network.Action;
import icu.tatyrealms.tatykefei.network.SkinLayersPayload;
import icu.tatyrealms.tatykefei.body.BodyManager;
import icu.tatyrealms.tatykefei.state.Form;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SkinLayersScreen extends Screen {
    private final Screen parent;
    private SkinLayersPayload.Snapshot snapshot = new SkinLayersPayload.Snapshot(List.of(), List.of(), 16);
    private UUID selected;
    private int page;
    private int refreshTicks;
    private int panelWidth;
    private int panelHeight;
    private int left;
    private int top;
    private int rows;

    public SkinLayersScreen(Screen parent) {
        super(Component.translatable("screen.tsf.skin_management"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(440, width - 16);
        panelHeight = Math.min(300, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        rows = Math.max(1, (panelHeight - 140) / 20);
        rebuild();
        TSFClient.send(Action.OPEN_SKINS, null);
    }

    public void receive(SkinLayersPayload.Snapshot update) {
        if (!snapshot.equals(update)) {
            snapshot = update;
            if (snapshot.available().stream().noneMatch(entry -> entry.body().equals(selected))) {
                selected = null;
            }
            rebuild();
        }
    }

    private void rebuild() {
        clearWidgets();
        int columnWidth = (panelWidth - 36) / 2;
        int right = left + panelWidth / 2 + 6;
        int maxPage = Math.max(0, (Math.max(snapshot.layers().size(), snapshot.available().size()) - 1) / rows);
        page = Math.min(page, maxPage);
        for (int row = 0; row < rows; row++) {
            int index = page * rows + row;
            if (index < snapshot.available().size()) {
                SkinLayersPayload.Entry entry = snapshot.available().get(index);
                String prefix = entry.body().equals(selected) ? "● " : "";
                addRenderableWidget(Button.builder(Component.literal(prefix + entry.name()), button -> {
                    selected = entry.body();
                    rebuild();
                }).bounds(right, top + 60 + row * 20, columnWidth, 18).build());
            }
        }
        int navigation = top + panelHeight - 72;
        Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), button -> {
            page--;
            rebuild();
        }).bounds(left + 12, navigation, 26, 18).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("›"), button -> {
            page++;
            rebuild();
        }).bounds(left + panelWidth - 38, navigation, 26, 18).build());
        next.active = page < maxPage;
        Button remove = addRenderableWidget(Button.builder(Component.translatable("screen.tsf.undress"),
                button -> TSFClient.send(Action.UNDRESS, null))
                .bounds(left + 12, top + panelHeight - 48, columnWidth, 20).build());
        remove.active = !snapshot.layers().isEmpty();
        Button replace = addRenderableWidget(Button.builder(Component.translatable("screen.tsf.replace_outer_skin"),
                button -> TSFClient.send(Action.REPLACE_SKIN, selected))
                .bounds(right, top + panelHeight - 48, columnWidth, 20).build());
        replace.active = selected != null && !snapshot.layers().isEmpty();
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(left + 12, top + panelHeight - 24, panelWidth - 24, 20).build());
    }

    @Override
    public void tick() {
        if (minecraft.player == null || !minecraft.player.isAlive() || BodyManager.hypnotized(minecraft.player)
                || BodyManager.form(minecraft.player) != Form.HUMAN) {
            onClose();
            return;
        }
        if (++refreshTicks % 20 == 0) {
            TSFClient.send(Action.OPEN_SKINS, null);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xE0101A2A);
        graphics.centeredText(font, title, width / 2, top + 8, 0xFFE6F2FF);
        graphics.centeredText(font, Component.translatable("screen.tsf.skin_count", snapshot.layers().size(), snapshot.limit()),
                width / 2, top + 25, 0xFFB5D6F4);
        graphics.text(font, Component.translatable("screen.tsf.skin_order"), left + 12, top + 44, 0xFFB5D6F4);
        graphics.text(font, Component.translatable("screen.tsf.skin_available"), left + panelWidth / 2 + 6, top + 44, 0xFFB5D6F4);
        for (int row = 0; row < rows; row++) {
            int index = page * rows + row;
            if (index < snapshot.layers().size()) {
                SkinLayersPayload.Entry entry = snapshot.layers().get(index);
                Component label = Component.literal((index + 1) + ". " + entry.name());
                if (index == 0) {
                    label = Component.translatable("screen.tsf.outer_skin", label);
                }
                graphics.text(font, font.plainSubstrByWidth(label.getString(), (panelWidth - 36) / 2),
                        left + 12, top + 65 + row * 20, index == 0 ? 0xFFFFD48C : 0xFFB5D6F4);
            }
        }
        graphics.centeredText(font, Component.literal(Integer.toString(page + 1)), width / 2, top + panelHeight - 67, 0xFFB5D6F4);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
