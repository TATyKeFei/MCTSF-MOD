package icu.tatyrealms.tatykefei.client.screen;

import icu.tatyrealms.tatykefei.client.TSFClient;
import icu.tatyrealms.tatykefei.body.BodyManager;
import icu.tatyrealms.tatykefei.state.Form;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SlimeColorScreen extends Screen {
    private final Screen parent;
    private EditBox color;
    private int left;
    private int top;

    public SlimeColorScreen(Screen parent) {
        super(Component.translatable("screen.tsf.slime_color"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        left = (width - 240) / 2;
        top = (height - 150) / 2;
        color = addRenderableWidget(new EditBox(font, left + 16, top + 44, 164, 20,
                Component.translatable("screen.tsf.slime_hex")));
        color.setMaxLength(7);
        var visual = TSFClient.visual(minecraft.player.getUUID());
        color.setValue(visual != null && visual.slimeColor() != null
                ? String.format(Locale.ROOT, "#%06X", visual.slimeColor()) : "#7FBF55");
        Button apply = addRenderableWidget(Button.builder(Component.translatable("screen.tsf.color_apply"), button -> {
            TSFClient.sendColor(color.getValue());
            onClose();
        }).bounds(left + 16, top + 82, 100, 20).build());
        color.setResponder(value -> apply.active = valid(value));
        addRenderableWidget(Button.builder(Component.translatable("screen.tsf.color_reset"), button -> {
            TSFClient.sendColor("reset");
            onClose();
        }).bounds(left + 124, top + 82, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left + 16, top + 112, 208, 20).build());
        setInitialFocus(color);
    }

    private boolean valid(String value) {
        return value.matches("#?[0-9a-fA-F]{6}");
    }

    @Override
    public void tick() {
        if (minecraft.player == null || !minecraft.player.isAlive()
                || BodyManager.form(minecraft.player) != Form.SLIME || BodyManager.hypnotized(minecraft.player)) {
            onClose();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xB5080C16);
        graphics.fill(left - 2, top - 2, left + 242, top + 152, 0xFF5575A2);
        graphics.fill(left, top, left + 240, top + 150, 0xFF101A2A);
        graphics.text(font, title, left + 16, top + 12, 0xFFE6F2FF);
        graphics.text(font, Component.translatable("screen.tsf.slime_hex"), left + 16, top + 29, 0xFFB5D6F4);
        String value = color.getValue();
        int preview = valid(value) ? Integer.parseInt(value.replace("#", ""), 16) : 0x555555;
        graphics.fill(left + 194, top + 44, left + 222, top + 64, 0xFF000000 | preview);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(minecraft.player == null ? null : parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
