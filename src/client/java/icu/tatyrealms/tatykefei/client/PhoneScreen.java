package icu.tatyrealms.tatykefei.client;

import icu.tatyrealms.tatykefei.network.Action;
import icu.tatyrealms.tatykefei.network.HypnosisSpeechPayload;
import icu.tatyrealms.tatykefei.network.VisualState;
import icu.tatyrealms.tatykefei.state.BodyManager;
import icu.tatyrealms.tatykefei.state.Form;
import icu.tatyrealms.tatykefei.state.HypnosisRestrictions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

public final class PhoneScreen extends Screen {
    private static final int[] FLAGS = {HypnosisRestrictions.MOVEMENT, HypnosisRestrictions.INTERACTION,
            HypnosisRestrictions.LOOK, HypnosisRestrictions.SPEECH};
    private static final Action[] RESTRICTION_ACTIONS = {Action.HYPNOSIS_MOVEMENT, Action.HYPNOSIS_INTERACTION,
            Action.HYPNOSIS_LOOK, Action.HYPNOSIS_SPEECH};
    private static final String[] RESTRICTION_LABELS = {"movement", "interaction", "look", "speech"};
    private static final int TEXT_COLOR = 0xFF404040;
    private final List<Button> restrictions = new ArrayList<>();
    private List<Player> targets = List.of();
    private UUID selected;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int modelWidth;
    private int controlsX;
    private int controlsWidth;
    private int speechY;
    private int footerY;
    private int refreshTicks;
    private int speechCooldown;
    private Button previous;
    private Button next;
    private Button release;
    private EditBox speech;
    private Button speak;
    private Button observerChat;
    private Button slimeColors;
    private Button leave;
    private Button returnToBody;
    private Button undress;

    public PhoneScreen() {
        super(Component.translatable("screen.tsf.phone"));
    }

    @Override
    protected void init() {
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        panelWidth = Math.min(438, width - 16);
        panelHeight = Math.min(344, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        modelWidth = (panelWidth - 36) * 2 / 5;
        controlsX = left + modelWidth + 24;
        controlsWidth = panelWidth - modelWidth - 36;
        footerY = top + panelHeight - 56;
        restrictions.clear();
        if (selected == null && minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player target) {
            selected = target.getUUID();
        }
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> select(-1))
                .bounds(left + 12, footerY - 22, 20, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.tsf.previous_target"))).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> select(1))
                .bounds(left + modelWidth - 8, footerY - 22, 20, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.tsf.next_target"))).build());

        int columns = panelHeight < 300 ? 2 : 1;
        int buttonWidth = (controlsWidth - (columns - 1) * 4) / columns;
        Tooltip rangeHint = Tooltip.create(Component.translatable("screen.tsf.hypnosis_range"));
        for (int index = 0; index < FLAGS.length; index++) {
            Action action = RESTRICTION_ACTIONS[index];
            restrictions.add(addRenderableWidget(Button.builder(Component.empty(), button -> TSFClient.send(action, selected))
                    .bounds(controlsX + index % columns * (buttonWidth + 4), top + 56 + index / columns * 24,
                            buttonWidth, 20).tooltip(rangeHint).build()));
        }
        int releaseY = top + 56 + (FLAGS.length / columns) * 24;
        release = action("release", "release_hint", Action.RELEASE, controlsX, releaseY, controlsWidth);
        speechY = releaseY + 36;
        String draft = speech == null ? "" : speech.getValue();
        speech = addRenderableWidget(new EditBox(font, controlsX, speechY, controlsWidth - 48, 20,
                Component.translatable("screen.tsf.control_speech")));
        speech.setMaxLength(HypnosisSpeechPayload.MAX_LENGTH);
        speech.setValue(draft);
        speak = addRenderableWidget(Button.builder(Component.translatable("screen.tsf.send_speech"), button -> {
            if (speak.active && TSFClient.sendHypnotizedSpeech(selected, speech.getValue())) {
                speech.setValue("");
                speechCooldown = 10;
                updateActions();
            }
        }).bounds(controlsX + controlsWidth - 44, speechY, 44, 20).build());
        speech.setResponder(value -> updateActions());

        int half = (panelWidth - 28) / 2;
        observerChat = addRenderableWidget(Button.builder(Component.empty(), button -> TSFClient.send(Action.OBSERVER_CHAT, null))
                .bounds(left + 12, footerY, half, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.tsf.observer_chat_hint"))).build());
        slimeColors = addRenderableWidget(Button.builder(Component.translatable("screen.tsf.slime_color"),
                button -> minecraft.gui.setScreen(new SlimeColorScreen(this)))
                .bounds(left + 12, footerY, half, 20).build());
        leave = addRenderableWidget(Button.builder(Component.empty(), button -> {
            VisualState visual = TSFClient.visual(minecraft.player.getUUID());
            TSFClient.send(visual != null && visual.skinRecoverySeconds() >= 0 ? Action.RESTORE_SKIN : Action.LEAVE_ZIPPER, null);
            onClose();
        }).bounds(left + 16 + half, footerY, half, 20).build());
        returnToBody = action("return", "return_hint", Action.RETURN, left + 12, footerY + 24, half);
        undress = action("undress", "undress_hint", Action.UNDRESS, left + 16 + half, footerY + 24, half);
        refresh();
    }

    private Button action(String name, String hint, Action action, int x, int y, int buttonWidth) {
        return addRenderableWidget(Button.builder(Component.translatable("screen.tsf." + name), button -> {
            TSFClient.send(action, action == Action.RELEASE ? selected : null);
            if (action == Action.RETURN) {
                onClose();
            }
        }).bounds(x, y, buttonWidth, 20).tooltip(Tooltip.create(Component.translatable("screen.tsf." + hint))).build());
    }

    private void select(int direction) {
        int index = selectedIndex() + direction;
        if (index >= 0 && index < targets.size()) {
            selected = targets.get(index).getUUID();
            speech.setValue("");
            updateActions();
        }
    }

    private int selectedIndex() {
        for (int index = 0; index < targets.size(); index++) {
            if (targets.get(index).getUUID().equals(selected)) {
                return index;
            }
        }
        return -1;
    }

    private Player selectedPlayer() {
        return minecraft.level == null || selected == null ? null : minecraft.level.getPlayerByUUID(selected);
    }

    public void refresh() {
        if (minecraft.level == null || minecraft.player == null || speech == null) {
            return;
        }
        targets = minecraft.level.players().stream()
                .filter(player -> player != minecraft.player && player.isAlive() && !player.isSpectator())
                .filter(player -> player.distanceToSqr(minecraft.player) <= 256)
                .filter(player -> {
                    VisualState visual = TSFClient.visual(player.getUUID());
                    return visual != null && visual.hypnotized() && visual.form() == Form.HUMAN
                            && minecraft.player.getUUID().equals(visual.hypnotist());
                })
                .sorted(Comparator.comparing(player -> player.getUUID().toString()))
                .map(player -> (Player) player).toList();
        if (selectedIndex() < 0) {
            selected = targets.isEmpty() ? null : targets.getFirst().getUUID();
            speech.setValue("");
        }
        updateActions();
    }

    private void updateActions() {
        if (minecraft.player == null || undress == null) {
            return;
        }
        Form form = BodyManager.form(minecraft.player);
        boolean free = !BodyManager.hypnotized(minecraft.player) && form != Form.OBSERVER;
        Player target = selectedPlayer();
        VisualState targetVisual = selected == null ? null : TSFClient.visual(selected);
        boolean owned = free && target != null && target.isAlive() && !target.isSpectator()
                && targetVisual != null && targetVisual.hypnotized() && targetVisual.form() == Form.HUMAN
                && minecraft.player.getUUID().equals(targetVisual.hypnotist());
        boolean inRange = owned && target.distanceToSqr(minecraft.player) <= 256 && minecraft.player.hasLineOfSight(target);
        int index = selectedIndex();
        previous.active = index > 0;
        next.active = index >= 0 && index + 1 < targets.size();
        release.active = owned;
        for (int flag = 0; flag < restrictions.size(); flag++) {
            boolean locked = targetVisual != null && (targetVisual.hypnosisRestrictions() & FLAGS[flag]) != 0;
            Button button = restrictions.get(flag);
            button.active = inRange;
            button.setMessage(Component.translatable("screen.tsf.restrict_" + RESTRICTION_LABELS[flag],
                    Component.translatable(locked ? "screen.tsf.restricted" : "screen.tsf.allowed")));
        }
        speech.active = inRange && form == Form.HUMAN;
        speak.active = speech.active && speechCooldown == 0 && !speech.getValue().isBlank();
        VisualState visual = TSFClient.visual(minecraft.player.getUUID());
        observerChat.visible = form == Form.HUMAN;
        observerChat.active = free && visual != null && visual.controllingObservers();
        observerChat.setMessage(Component.translatable(visual != null && visual.observerChatPrivate()
                ? "screen.tsf.observer_chat_private" : "screen.tsf.observer_chat_public"));
        slimeColors.visible = form == Form.SLIME;
        slimeColors.active = free;
        boolean recovery = visual != null && visual.skinRecoverySeconds() >= 0;
        leave.visible = visual != null && (visual.zipperControl() || recovery);
        leave.active = recovery ? visual.skinRecoverySeconds() == 0 : free && leave.visible;
        leave.setMessage(recovery ? Component.translatable(visual.skinRecoverySeconds() == 0
                ? "screen.tsf.restore_skin" : "screen.tsf.restore_skin_wait", visual.skinRecoverySeconds())
                : Component.translatable("screen.tsf.leave_zipper"));
        leave.setTooltip(Tooltip.create(Component.translatable(recovery
                ? "screen.tsf.restore_skin_hint" : "screen.tsf.leave_zipper_hint")));
        returnToBody.active = !BodyManager.hypnotized(minecraft.player);
        undress.active = free && visual != null && visual.wearingSkin();
    }

    @Override
    public void tick() {
        if (minecraft.player == null || minecraft.level == null || !minecraft.player.isAlive() || BodyManager.hypnotized(minecraft.player)) {
            onClose();
            return;
        }
        if (speechCooldown > 0) {
            speechCooldown--;
        }
        if (++refreshTicks % 20 == 0) {
            refresh();
        }
        updateActions();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        panel(graphics, left, top, panelWidth, panelHeight);
        graphics.text(font, title, left + 12, top + 10, TEXT_COLOR, false);
        if (minecraft.player != null) {
            VisualState visual = TSFClient.visual(minecraft.player.getUUID());
            Component form = Component.translatable("form.tsf." + BodyManager.form(minecraft.player).name().toLowerCase(java.util.Locale.ROOT));
            String bodyName = visual == null ? minecraft.player.getGameProfile().name() : visual.bodyName();
            text(graphics, Component.translatable("screen.tsf.status", form, bodyName), left + 12, top + 25, panelWidth - 24);
        }
        text(graphics, Component.translatable("screen.tsf.hypnotized_players"), left + 12, top + 42, modelWidth);
        text(graphics, Component.translatable("screen.tsf.target_actions"), controlsX, top + 42, controlsWidth);
        int previewTop = top + 56;
        int previewBottom = footerY - 48;
        inset(graphics, left + 12, previewTop, modelWidth, previewBottom - previewTop);
        Player target = selectedPlayer();
        if (target != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, left + 14, previewTop + 2,
                    left + modelWidth + 10, previewBottom - 2, Math.max(16, (previewBottom - previewTop - 8) * 5 / 11),
                    0.0625F, mouseX, mouseY, target);
            VisualState visual = TSFClient.visual(target.getUUID());
            centeredText(graphics, Component.literal(visual == null ? target.getGameProfile().name() : visual.bodyName()),
                    left + 12, footerY - 44, modelWidth);
            centeredText(graphics, Component.translatable("screen.tsf.target_distance", (int) Math.ceil(target.distanceTo(minecraft.player))),
                    left + 12, footerY - 33, modelWidth);
        } else {
            int y = previewTop + 6;
            for (var line : font.split(Component.translatable("screen.tsf.no_hypnotized_players"), modelWidth - 12)) {
                graphics.text(font, line, left + 18, y, TEXT_COLOR, false);
                y += font.lineHeight;
            }
        }
        Component page = Component.translatable("screen.tsf.target_page", selectedIndex() + 1, targets.size());
        centeredText(graphics, page, left + 34, footerY - 16, modelWidth - 44);
        text(graphics, Component.translatable("screen.tsf.control_speech"), controlsX, speechY - 12, controlsWidth);
        if (panelHeight >= 300) {
            int y = speechY + 26;
            for (var line : font.split(Component.translatable("screen.tsf.hypnosis_range"), controlsWidth)) {
                if (y + font.lineHeight > footerY - 10) {
                    break;
                }
                graphics.text(font, line, controlsX, y, TEXT_COLOR, false);
                y += font.lineHeight;
            }
        }
        graphics.fill(left + 12, footerY - 6, left + panelWidth - 12, footerY - 5, 0xFF8B8B8B);
        graphics.fill(left + 12, footerY - 5, left + panelWidth - 12, footerY - 4, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void text(GuiGraphicsExtractor graphics, Component label, int x, int y, int maxWidth) {
        graphics.text(font, font.substrByWidth(label, maxWidth).getString(), x, y, TEXT_COLOR, false);
    }

    private void centeredText(GuiGraphicsExtractor graphics, Component label, int x, int y, int maxWidth) {
        String text = font.substrByWidth(label, maxWidth).getString();
        graphics.text(font, text, x + (maxWidth - font.width(text)) / 2, y, TEXT_COLOR, false);
    }

    private static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x + 2, y, x + width - 2, y + height, 0xFF000000);
        graphics.fill(x, y + 2, x + width, y + height - 2, 0xFF000000);
        graphics.fill(x + 2, y + 1, x + width - 2, y + height - 1, 0xFF555555);
        graphics.fill(x + 1, y + 2, x + width - 1, y + height - 2, 0xFF555555);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xFFC6C6C6);
        graphics.fill(x + 3, y + 2, x + width - 3, y + 4, 0xFFFFFFFF);
        graphics.fill(x + 2, y + 3, x + 4, y + height - 3, 0xFFFFFFFF);
        graphics.fill(x + 4, y + height - 4, x + width - 2, y + height - 2, 0xFF555555);
        graphics.fill(x + width - 4, y + 4, x + width - 2, y + height - 3, 0xFF555555);
    }

    private static void inset(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF8B8B8B);
        graphics.fill(x, y, x + width, y + 1, 0xFF373737);
        graphics.fill(x, y, x + 1, y + height, 0xFF373737);
        graphics.fill(x + 1, y + height - 1, x + width, y + height, 0xFFFFFFFF);
        graphics.fill(x + width - 1, y + 1, x + width, y + height, 0xFFFFFFFF);
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
