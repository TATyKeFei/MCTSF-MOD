package icu.tatyrealms.tatykefei.client.screen;

import icu.tatyrealms.tatykefei.client.render.BodyPreview;
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
        int left = columnLeft();
        int buttonWidth = columnWidth();
        int top = height / 2 - 90;
        addRenderableWidget(Button.builder(Component.translatable("screen.tsf.display_name",
                Component.translatable(settings.showName() ? "options.on" : "options.off")), button ->
                send(BodyDisplayPayload.UPDATE, !settings.showName(), settings.poseable()))
                .bounds(left, top, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.tsf.display_poseable",
                Component.translatable(settings.poseable() ? "options.on" : "options.off")), button ->
                send(BodyDisplayPayload.UPDATE, settings.showName(), !settings.poseable()))
                .bounds(left, top + 24, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.tsf.display_cape",
                Component.translatable(settings.showCape() ? "options.on" : "options.off")), button ->
                send(BodyDisplayPayload.UPDATE, settings.showName(), settings.poseable(),
                        !settings.showCape(), settings.hideStand(), settings.face()))
                .bounds(left, top + 48, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.tsf.display_face",
                FacePickerScreen.label(settings.face())), button ->
                minecraft.gui.setScreen(new FacePickerScreen(this)))
                .bounds(left, top + 72, buttonWidth, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.tsf.display_face_hint"))).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.tsf.display_hide_stand",
                Component.translatable(settings.hideStand() ? "options.on" : "options.off")), button ->
                send(BodyDisplayPayload.UPDATE, settings.showName(), settings.poseable(),
                        settings.showCape(), !settings.hideStand(), settings.face()))
                .bounds(left, top + 96, buttonWidth, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.tsf.display_hide_stand_hint"))).build());
        Button edit = addRenderableWidget(Button.builder(Component.translatable("screen.tsf.display_edit"), button ->
                send(BodyDisplayPayload.EDIT, settings.showName(), settings.poseable()))
                .bounds(left, top + 120, buttonWidth, 20).build());
        edit.active = settings.poseable() && settings.entity() >= 0 && minecraft.player.getMainHandItem().isEmpty();
        edit.setTooltip(Tooltip.create(Component.translatable("screen.tsf.display_edit_hint")));
        Button reset = addRenderableWidget(Button.builder(Component.translatable("screen.tsf.display_reset"), button ->
                send(BodyDisplayPayload.RESET, settings.showName(), settings.poseable()))
                .bounds(left, top + 144, buttonWidth, 20).build());
        reset.active = settings.poseable() && settings.entity() >= 0;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left, top + 172, buttonWidth, 20).build());
    }

    private int columnLeft() {
        return width / 2 + 8;
    }

    private int columnWidth() {
        return Math.max(80, Math.min(240, width / 2 - 32));
    }

    public void applyFace(String face) {
        settings = new BodyDisplayPayload(settings.pos(), settings.operation(), settings.showName(),
                settings.poseable(), settings.showCape(), settings.hideStand(), settings.entity(), face);
        send(BodyDisplayPayload.UPDATE, settings.showName(), settings.poseable(), settings.showCape(),
                settings.hideStand(), face);
    }

    public String face() {
        return settings.face();
    }

    public int entityId() {
        return settings.entity();
    }

    private void send(int operation, boolean showName, boolean poseable) {
        send(operation, showName, poseable, settings.showCape(), settings.hideStand(), settings.face());
    }

    private void send(int operation, boolean showName, boolean poseable, boolean showCape, boolean hideStand,
            String face) {
        ClientPlayNetworking.send(new BodyDisplayPayload(settings.pos(), operation, showName, poseable, showCape,
                hideStand, -1, face));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        BodyPreview.extractForColumn(graphics, settings.entity(), width, height, columnLeft(), mouseX, mouseY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.centeredText(font, title, columnLeft() + columnWidth() / 2, height / 2 - 116, 0xFFFFFFFF);
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
