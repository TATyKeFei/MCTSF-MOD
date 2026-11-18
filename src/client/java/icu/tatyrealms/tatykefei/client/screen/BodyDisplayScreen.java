package icu.tatyrealms.tatykefei.client.screen;

import icu.tatyrealms.tatykefei.block.BodyDisplayBlockEntity;
import icu.tatyrealms.tatykefei.client.render.BodyPreview;
import icu.tatyrealms.tatykefei.network.BodyDisplayPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class BodyDisplayScreen extends Screen {
    private static final long RESET_CLICK_WINDOW_MS = 1000L;
    private static final int ROW_HEIGHT = 22;

    private BodyDisplayPayload settings;
    private final ResetConfirmation resetPose = new ResetConfirmation("screen.tsf.display_reset", BodyDisplayPayload.RESET);
    private final ResetConfirmation resetPosition = new ResetConfirmation("screen.tsf.display_reset_position", BodyDisplayPayload.RESET_POSITION);
    private EditBox name;

    public BodyDisplayScreen(BodyDisplayPayload settings) {
        super(Component.translatable("screen.tsf.body_display"));
        this.settings = settings;
    }

    @Override
    protected void init() {
        buttons(name == null ? settings.displayName() : name.getValue());
    }

    public void receive(BodyDisplayPayload payload) {
        if (!settings.pos().equals(payload.pos())) {
            return;
        }
        String draft = name != null && !name.getValue().equals(settings.displayName())
                ? name.getValue() : payload.displayName();
        settings = payload;
        buttons(draft);
    }

    private void buttons(String draft) {
        boolean refocusName = name != null && name.isFocused();
        clearFocus();
        clearWidgets();
        int left = columnLeft();
        int buttonWidth = columnWidth();
        int top = height / 2 - 96;
        addRenderableWidget(Button.builder(Component.translatable("screen.tsf.display_name",
                Component.translatable(settings.showName() ? "options.on" : "options.off")), button ->
                send(BodyDisplayPayload.UPDATE, !settings.showName(), settings.poseable()))
                .bounds(left, top, buttonWidth, 20).build());
        Component nameLabel = Component.translatable("screen.tsf.display_custom_name");
        Component saveLabel = Component.translatable("screen.tsf.display_save_name");
        int saveWidth = Math.max(30, Math.min(buttonWidth / 2, font.width(saveLabel) + 10));
        name = addRenderableWidget(new EditBox(font, left, top + ROW_HEIGHT, buttonWidth - saveWidth - 4, 20, nameLabel));
        name.setMaxLength(BodyDisplayBlockEntity.MAX_NAME_LENGTH);
        name.setHint(nameLabel);
        name.setValue(draft);
        if (refocusName) {
            setInitialFocus(name);
        }
        addRenderableWidget(Button.builder(saveLabel, button -> {
            String value = name.getValue().strip();
            name.setValue(value);
            ClientPlayNetworking.send(BodyDisplayPayload.rename(settings.pos(), value));
        }).bounds(left + buttonWidth - saveWidth, top + ROW_HEIGHT, saveWidth, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.tsf.display_custom_name_hint"))).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.tsf.display_poseable",
                Component.translatable(settings.poseable() ? "options.on" : "options.off")), button ->
                send(BodyDisplayPayload.UPDATE, settings.showName(), !settings.poseable()))
                .bounds(left, top + 2 * ROW_HEIGHT, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.tsf.display_cape",
                Component.translatable(settings.showCape() ? "options.on" : "options.off")), button ->
                send(BodyDisplayPayload.UPDATE, settings.showName(), settings.poseable(),
                        !settings.showCape(), settings.hideStand(), settings.face()))
                .bounds(left, top + 3 * ROW_HEIGHT, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.tsf.display_face",
                FacePickerScreen.label(settings.face())), button ->
                minecraft.gui.setScreen(new FacePickerScreen(this)))
                .bounds(left, top + 4 * ROW_HEIGHT, buttonWidth, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.tsf.display_face_hint"))).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.tsf.display_hide_stand",
                Component.translatable(settings.hideStand() ? "options.on" : "options.off")), button ->
                send(BodyDisplayPayload.UPDATE, settings.showName(), settings.poseable(),
                        settings.showCape(), !settings.hideStand(), settings.face()))
                .bounds(left, top + 5 * ROW_HEIGHT, buttonWidth, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.tsf.display_hide_stand_hint"))).build());
        Button edit = addRenderableWidget(Button.builder(Component.translatable("screen.tsf.display_edit"), button ->
                send(BodyDisplayPayload.EDIT, settings.showName(), settings.poseable()))
                .bounds(left, top + 6 * ROW_HEIGHT, buttonWidth, 20).build());
        edit.active = settings.poseable() && settings.entity() >= 0 && minecraft.player.getMainHandItem().isEmpty();
        edit.setTooltip(Tooltip.create(Component.translatable("screen.tsf.display_edit_hint")));
        int resetWidth = (buttonWidth - 4) / 2;
        int resetTop = top + 7 * ROW_HEIGHT;
        resetPose.addButton(left, resetTop, resetWidth);
        resetPosition.addButton(left + resetWidth + 4, resetTop, buttonWidth - resetWidth - 4);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left, top + 8 * ROW_HEIGHT + 4, buttonWidth, 20).build());
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        resetPose.expire(now);
        resetPosition.expire(now);
    }

    private final class ResetConfirmation {
        private final String key;
        private final int operation;
        private Button button;
        private int clicks;
        private long firstClick;

        private ResetConfirmation(String key, int operation) {
            this.key = key;
            this.operation = operation;
        }

        private void addButton(int left, int top, int width) {
            clicks = 0;
            button = addRenderableWidget(Button.builder(Component.translatable(key), ignored -> click())
                    .bounds(left, top, width, 20)
                    .tooltip(Tooltip.create(Component.translatable(key + "_hint"))).build());
            button.active = settings.poseable() && settings.entity() >= 0;
        }

        private void click() {
            long now = System.currentTimeMillis();
            expire(now);
            if (clicks++ == 0) {
                firstClick = now;
            }
            if (clicks == 3) {
                clear();
                ClientPlayNetworking.send(BodyDisplayPayload.request(settings.pos(), operation));
            } else {
                button.setMessage(Component.translatable(key + "_progress", clicks));
            }
        }

        private void expire(long now) {
            if (clicks > 0 && now - firstClick > RESET_CLICK_WINDOW_MS) {
                clear();
            }
        }

        private void clear() {
            clicks = 0;
            button.setMessage(Component.translatable(key));
        }
    }

    private int columnLeft() {
        return width / 2 + 8;
    }

    private int columnWidth() {
        return Math.max(80, Math.min(240, width / 2 - 32));
    }

    public void applyFace(String face) {
        settings = new BodyDisplayPayload(settings.pos(), settings.operation(), settings.showName(),
                settings.poseable(), settings.showCape(), settings.hideStand(), settings.entity(), face, settings.displayName(), 0);
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
                hideStand, -1, face, "", 0));
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
