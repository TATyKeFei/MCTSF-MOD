package icu.tatyrealms.tatykefei.client;

import icu.tatyrealms.tatykefei.network.Action;
import icu.tatyrealms.tatykefei.network.VisualState;
import icu.tatyrealms.tatykefei.state.BodyManager;
import icu.tatyrealms.tatykefei.state.HypnosisRestrictions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class HypnosisScreen extends Screen {
    private final Screen parent;
    private final UUID target;
    private final List<Button> restrictions = new ArrayList<>();
    private static final int[] FLAGS = {HypnosisRestrictions.MOVEMENT, HypnosisRestrictions.INTERACTION,
            HypnosisRestrictions.LOOK, HypnosisRestrictions.SPEECH};
    private static final Action[] ACTIONS = {Action.HYPNOSIS_MOVEMENT, Action.HYPNOSIS_INTERACTION,
            Action.HYPNOSIS_LOOK, Action.HYPNOSIS_SPEECH};
    private static final String[] LABELS = {"movement", "interaction", "look", "speech"};

    public HypnosisScreen(Screen parent, UUID target) {
        super(Component.translatable("screen.tsf.hypnosis_controls"));
        this.parent = parent;
        this.target = target;
    }

    @Override
    protected void init() {
        restrictions.clear();
        int left = (width - 280) / 2;
        int top = (height - 208) / 2;
        for (int index = 0; index < FLAGS.length; index++) {
            Action action = ACTIONS[index];
            restrictions.add(addRenderableWidget(Button.builder(Component.empty(),
                    button -> TSFClient.send(action, target)).bounds(left, top + 50 + index * 24, 280, 20).build()));
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(left, top + 176, 280, 20).build());
        refresh();
    }

    private void refresh() {
        VisualState visual = TSFClient.visual(target);
        var entity = minecraft.level == null ? null : minecraft.level.getPlayerByUUID(target);
        boolean owned = minecraft.player != null && visual != null && visual.hypnotized()
                && minecraft.player.getUUID().equals(visual.hypnotist()) && entity != null
                && minecraft.player.distanceToSqr(entity) <= 256 && minecraft.player.hasLineOfSight(entity);
        for (int index = 0; index < restrictions.size(); index++) {
            boolean locked = visual != null && (visual.hypnosisRestrictions() & FLAGS[index]) != 0;
            Button button = restrictions.get(index);
            button.active = owned;
            button.setMessage(Component.translatable("screen.tsf.restrict_" + LABELS[index],
                    Component.translatable(locked ? "screen.tsf.restricted" : "screen.tsf.allowed")));
        }
    }

    @Override
    public void tick() {
        if (minecraft.player == null || !minecraft.player.isAlive() || BodyManager.hypnotized(minecraft.player)) {
            onClose();
            return;
        }
        refresh();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xE0101A2A);
        int top = (height - 208) / 2;
        graphics.centeredText(font, title, width / 2, top + 8, 0xFFE6F2FF);
        VisualState visual = TSFClient.visual(target);
        if (visual != null) {
            graphics.centeredText(font, Component.literal(visual.bodyName()), width / 2, top + 28, 0xFFB5D6F4);
        }
        graphics.centeredText(font, Component.translatable("screen.tsf.hypnosis_range"), width / 2, top + 154, 0xFFB5D6F4);
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
