package icu.tatyrealms.tatykefei.client;

import icu.tatyrealms.tatykefei.TSFServerConfig;
import icu.tatyrealms.tatykefei.network.SettingsPayload;
import java.io.IOException;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TSFSettingsScreen extends Screen {
    private final Screen parent;
    private TSFServerConfig settings = new TSFServerConfig();
    private boolean editable;
    private Component status = Component.empty();

    public TSFSettingsScreen(Screen parent) {
        super(Component.translatable("screen.tsf.settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (minecraft.getConnection() == null) {
            try {
                settings = TSFServerConfig.read();
                editable = true;
                status = Component.translatable("screen.tsf.settings_local");
            } catch (IOException exception) {
                status = Component.literal(exception.getMessage());
            }
        } else if (ClientPlayNetworking.canSend(SettingsPayload.TYPE)) {
            status = Component.translatable("screen.tsf.settings_loading");
            ClientPlayNetworking.send(new SettingsPayload("", false));
        } else {
            status = Component.translatable("screen.tsf.settings_unavailable");
        }
        buttons();
    }

    public void receive(SettingsPayload payload) {
        try {
            settings = TSFServerConfig.parse(payload.json());
            editable = payload.editable();
            status = Component.translatable(editable ? "screen.tsf.settings_server" : "screen.tsf.settings_readonly");
            buttons();
        } catch (IOException exception) {
            status = Component.literal(exception.getMessage());
        }
    }

    private void buttons() {
        clearWidgets();
        int left = (width - 300) / 2;
        int top = Math.max(32, (height - 220) / 2);
        toggle("skin_knife", settings.skinKnifeEnabled, left, top + 28,
                () -> settings.skinKnifeEnabled = !settings.skinKnifeEnabled);
        toggle("requires_hypnosis", settings.skinKnifeRequiresHypnosis, left, top + 52,
                () -> settings.skinKnifeRequiresHypnosis = !settings.skinKnifeRequiresHypnosis);
        toggle("requires_downed", settings.skinKnifeRequiresDowned, left, top + 76,
                () -> settings.skinKnifeRequiresDowned = !settings.skinKnifeRequiresDowned);
        Button death = addRenderableWidget(Button.builder(Component.translatable("screen.tsf.death_mode",
                Component.translatable(settings.deathLeavesBody ? "screen.tsf.death_slime" : "screen.tsf.death_normal")), button -> {
            settings.deathLeavesBody = !settings.deathLeavesBody;
            buttons();
        }).bounds(left, top + 100, 300, 20).build());
        death.active = editable;
        Button delay = addRenderableWidget(Button.builder(Component.translatable("screen.tsf.expulsion_delay", settings.expulsionDelaySeconds), button -> {
            settings.expulsionDelaySeconds = settings.expulsionDelaySeconds % 60 + 1;
            buttons();
        }).bounds(left, top + 124, 300, 20).build());
        delay.active = editable;
        Button save = addRenderableWidget(Button.builder(Component.translatable("screen.tsf.settings_save"), button -> save())
                .bounds(left, top + 170, 146, 20).build());
        save.active = editable;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(left + 154, top + 170, 146, 20).build());
    }

    private void toggle(String key, boolean enabled, int left, int top, Runnable change) {
        Button toggle = addRenderableWidget(Button.builder(Component.translatable("screen.tsf." + key,
                Component.translatable(enabled ? "options.on" : "options.off")), button -> {
            change.run();
            buttons();
        }).bounds(left, top, 300, 20).build());
        toggle.active = editable;
    }

    private void save() {
        if (minecraft.getConnection() != null) {
            ClientPlayNetworking.send(new SettingsPayload(settings.json(), false));
        } else {
            try {
                TSFServerConfig.save(settings);
                status = Component.translatable("message.tsf.config_saved");
            } catch (IOException exception) {
                status = Component.literal(exception.getMessage());
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xE0101A2A);
        int top = Math.max(32, (height - 220) / 2);
        graphics.centeredText(font, title, width / 2, top + 8, 0xFFE6F2FF);
        graphics.centeredText(font, status, width / 2, top + 150, 0xFFB5D6F4);
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
