package icu.tatyrealms.tatykefei.client.screen;

import icu.tatyrealms.tatykefei.config.TSFServerConfig;
import icu.tatyrealms.tatykefei.network.SettingsPayload;
import java.io.IOException;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TSFSettingsScreen extends Screen {
    private final Screen parent;
    private TSFServerConfig settings = new TSFServerConfig();
    private boolean editable;
    private Component status = Component.empty();
    private int rowStep = 21;

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
        rowStep = Math.min(21, Math.max(18, (height - 30) / 11));
        int top = Math.max(0, (height - 30 - 11 * rowStep) / 2);
        toggle("skin_knife", settings.skinKnifeEnabled, left, top + 24,
                () -> settings.skinKnifeEnabled = !settings.skinKnifeEnabled);
        toggle("requires_hypnosis", settings.skinKnifeRequiresHypnosis, left, top + 24 + rowStep,
                () -> settings.skinKnifeRequiresHypnosis = !settings.skinKnifeRequiresHypnosis);
        toggle("requires_downed", settings.skinKnifeRequiresDowned, left, top + 24 + 2 * rowStep,
                () -> settings.skinKnifeRequiresDowned = !settings.skinKnifeRequiresDowned);
        Button death = addRenderableWidget(Button.builder(Component.translatable("screen.tsf.death_mode",
                Component.translatable(settings.deathLeavesBody ? "screen.tsf.death_slime" : "screen.tsf.death_normal")), button -> {
            settings.deathLeavesBody = !settings.deathLeavesBody;
            buttons();
        }).bounds(left, top + 24 + 3 * rowStep, 300, rowStep - 1).build());
        death.active = editable;
        Button delay = addRenderableWidget(Button.builder(Component.translatable("screen.tsf.expulsion_delay", settings.expulsionDelaySeconds), button -> {
            settings.expulsionDelaySeconds = settings.expulsionDelaySeconds % 60 + 1;
            buttons();
        }).bounds(left, top + 24 + 4 * rowStep, 300, rowStep - 1).build());
        delay.active = editable;
        Button layers = addRenderableWidget(Button.builder(Component.translatable("screen.tsf.max_skin_layers", settings.maxSkinLayers), button -> {
            settings.maxSkinLayers = settings.maxSkinLayers % 64 + 1;
            buttons();
        }).bounds(left, top + 24 + 5 * rowStep, 300, rowStep - 1).build());
        layers.active = editable;
        toggle("body_limb_physics", settings.bodyLimbPhysics, left, top + 24 + 6 * rowStep,
                () -> settings.bodyLimbPhysics = !settings.bodyLimbPhysics);
        toggle("body_pose_picking", settings.bodyPosePicking, left, top + 24 + 7 * rowStep,
                () -> settings.bodyPosePicking = !settings.bodyPosePicking)
                .setTooltip(Tooltip.create(Component.translatable("screen.tsf.body_pose_picking_hint")));
        toggle("client_skin_lookup", settings.clientSkinLookup, left, top + 24 + 8 * rowStep,
                () -> settings.clientSkinLookup = !settings.clientSkinLookup)
                .setTooltip(Tooltip.create(Component.translatable("screen.tsf.client_skin_lookup_hint")));
        toggle("nature_liquid_source_labels", settings.natureLiquidSourceLabels, left, top + 24 + 9 * rowStep,
                () -> settings.natureLiquidSourceLabels = !settings.natureLiquidSourceLabels)
                .setTooltip(Tooltip.create(Component.translatable("screen.tsf.nature_liquid_source_labels_hint")));
        Button save = addRenderableWidget(Button.builder(Component.translatable("screen.tsf.settings_save"), button -> save())
                .bounds(left, top + 24 + 10 * rowStep, 146, rowStep - 1).build());
        save.active = editable;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(left + 154, top + 24 + 10 * rowStep, 146, rowStep - 1).build());
    }

    private Button toggle(String key, boolean enabled, int left, int top, Runnable change) {
        Button toggle = addRenderableWidget(Button.builder(Component.translatable("screen.tsf." + key,
                Component.translatable(enabled ? "options.on" : "options.off")), button -> {
            change.run();
            buttons();
        }).bounds(left, top, 300, rowStep - 1).build());
        toggle.active = editable;
        return toggle;
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
        int top = Math.max(0, (height - 30 - 11 * rowStep) / 2);
        graphics.centeredText(font, title, width / 2, top + 4, 0xFFE6F2FF);
        graphics.centeredText(font, status, width / 2, top + 14, 0xFFB5D6F4);
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
