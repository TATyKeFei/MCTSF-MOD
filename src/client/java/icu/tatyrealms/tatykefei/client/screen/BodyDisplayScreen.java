package icu.tatyrealms.tatykefei.client.screen;

import icu.tatyrealms.tatykefei.network.BodyDisplayPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class BodyDisplayScreen extends Screen {
    private BodyDisplayPayload settings;

    public BodyDisplayScreen(BodyDisplayPayload settings) {
        super(Component.translatable("screen.tsf.body_display"));
        this.settings = settings;
    }

    @Override
    protected void init() {
        buttons();
    }

    public void receive(BodyDisplayPayload payload) {
        if (!settings.pos().equals(payload.pos())) {
            return;
        }
        settings = payload;
        buttons();
    }

    private void buttons() {
        clearWidgets();
        int left = (width - 240) / 2;
        int top = height / 2 - 72;
        addRenderableWidget(Button.builder(Component.translatable("screen.tsf.display_name",
                Component.translatable(settings.showName() ? "options.on" : "options.off")), button ->
                send(BodyDisplayPayload.UPDATE, !settings.showName(), settings.poseable()))
                .bounds(left, top, 240, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.tsf.display_poseable",
                Component.translatable(settings.poseable() ? "options.on" : "options.off")), button ->
                send(BodyDisplayPayload.UPDATE, settings.showName(), !settings.poseable()))
                .bounds(left, top + 24, 240, 20).build());
        Button edit = addRenderableWidget(Button.builder(Component.translatable("screen.tsf.display_edit"), button ->
                send(BodyDisplayPayload.EDIT, settings.showName(), settings.poseable()))
                .bounds(left, top + 56, 240, 20).build());
        edit.active = settings.poseable() && settings.entity() >= 0 && minecraft.player.getMainHandItem().isEmpty();
        edit.setTooltip(Tooltip.create(Component.translatable("screen.tsf.display_edit_hint")));
        Button reset = addRenderableWidget(Button.builder(Component.translatable("screen.tsf.display_reset"), button ->
                send(BodyDisplayPayload.RESET, settings.showName(), settings.poseable()))
                .bounds(left, top + 80, 240, 20).build());
        reset.active = settings.poseable() && settings.entity() >= 0;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left, top + 116, 240, 20).build());
    }

    private void send(int operation, boolean showName, boolean poseable) {
        ClientPlayNetworking.send(new BodyDisplayPayload(settings.pos(), operation, showName, poseable, -1));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.centeredText(font, title, width / 2, height / 2 - 102, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        send(BodyDisplayPayload.CLOSE, settings.showName(), settings.poseable());
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
