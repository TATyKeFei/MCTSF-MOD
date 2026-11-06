package icu.tatyrealms.tatykefei.client;

import icu.tatyrealms.tatykefei.network.Action;
import icu.tatyrealms.tatykefei.network.VisualState;
import icu.tatyrealms.tatykefei.state.BodyManager;
import icu.tatyrealms.tatykefei.state.Form;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

public final class PhoneScreen extends Screen {
    private UUID selected;
    private int page;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int rows;
    private int refreshTicks;
    private List<Player> targets = new ArrayList<>();
    private final List<Button> actions = new ArrayList<>();
    private Button slimeColors;

    public PhoneScreen() {
        super(Component.translatable("screen.tsf.phone"));
    }

    @Override
    protected void init() {
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        panelWidth = Math.min(400, width - 16);
        panelHeight = Math.min(310, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        rows = Math.max(1, Math.min(7, (panelHeight - 147) / 20));
        if (selected == null && minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player target) {
            selected = target.getUUID();
        }
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        actions.clear();
        targets = minecraft.level.players().stream().filter(player -> player != minecraft.player && player.isAlive() && !player.isSpectator())
                .filter(player -> player.distanceToSqr(minecraft.player) <= 256)
                .sorted(Comparator.comparingDouble(player -> player.distanceToSqr(minecraft.player)))
                .map(player -> (Player) player).toList();
        int maxPage = Math.max(0, (targets.size() - 1) / rows);
        page = Math.min(page, maxPage);
        int listWidth = panelWidth / 2 - 18;
        for (int row = 0; row < rows; row++) {
            int index = page * rows + row;
            if (index >= targets.size()) {
                break;
            }
            Player target = targets.get(index);
            String prefix = target.getUUID().equals(selected) ? "● " : "";
            VisualState targetVisual = TSFClient.visual(target.getUUID());
            String label = prefix + (targetVisual == null ? target.getGameProfile().name() : targetVisual.bodyName())
                    + " · " + (int) Math.ceil(target.distanceTo(minecraft.player)) + "m";
            addRenderableWidget(Button.builder(Component.literal(label), button -> {
                selected = target.getUUID();
                rebuild();
            }).bounds(left + 12, top + 70 + row * 20, listWidth, 18).build());
        }
        int navigationY = top + 72 + rows * 20;
        Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), button -> {
            page--;
            rebuild();
        }).bounds(left + 12, navigationY, 24, 18).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("›"), button -> {
            page++;
            rebuild();
        }).bounds(left + listWidth - 12, navigationY, 24, 18).build());
        next.active = page < maxPage;
        int buttonX = left + panelWidth / 2 + 2;
        int buttonWidth = panelWidth / 2 - 14;
        action("release", "release_hint", Action.RELEASE, buttonX, top + 70, buttonWidth);
        action("expel_target", "expel_target_hint", Action.EXPEL_TARGET, buttonX, top + 90, buttonWidth);
        int bottom = top + panelHeight - 67;
        int half = (panelWidth - 28) / 2;
        action("leave_zipper", "leave_zipper_hint", Action.LEAVE_ZIPPER, left + 12, bottom, panelWidth - 24);
        action("return", "return_hint", Action.RETURN, left + 12, bottom + 24, half);
        action("undress", "undress_hint", Action.UNDRESS, left + 16 + half, bottom + 24, half);
        slimeColors = addRenderableWidget(Button.builder(Component.translatable("screen.tsf.slime_color"),
                button -> minecraft.gui.setScreen(new SlimeColorScreen(this)))
                .bounds(buttonX, top + 110, buttonWidth, 20).build());
        updateActions();
    }

    private void action(String name, String hint, Action action, int positionX, int positionY, int buttonWidth) {
        Button button = addRenderableWidget(Button.builder(Component.translatable("screen.tsf." + name), ignored -> {
            VisualState visual = TSFClient.visual(minecraft.player.getUUID());
            TSFClient.send(action == Action.LEAVE_ZIPPER && visual != null && visual.skinRecoverySeconds() >= 0
                    ? Action.RESTORE_SKIN : action, selected);
            if (action == Action.LEAVE_ZIPPER || action == Action.RETURN) {
                onClose();
            }
        }).bounds(positionX, positionY, buttonWidth, 20).tooltip(Tooltip.create(Component.translatable("screen.tsf." + hint))).build());
        actions.add(button);
    }

    private void updateActions() {
        if (minecraft.player == null || actions.size() != 5) {
            return;
        }
        Form form = BodyManager.form(minecraft.player);
        boolean targetPresent = targets.stream().anyMatch(player -> player.getUUID().equals(selected));
        boolean free = !BodyManager.hypnotized(minecraft.player) && form != Form.OBSERVER;
        actions.get(0).active = free && targetPresent;
        slimeColors.visible = form == Form.SLIME;
        slimeColors.active = free && slimeColors.visible;
        actions.get(1).active = free && targetPresent;
        VisualState visual = TSFClient.visual(minecraft.player.getUUID());
        boolean recovery = visual != null && visual.skinRecoverySeconds() >= 0;
        actions.get(2).visible = visual != null && (visual.zipperControl() || recovery);
        actions.get(2).active = recovery ? visual.skinRecoverySeconds() == 0 : free && actions.get(2).visible;
        actions.get(2).setMessage(recovery ? Component.translatable(visual.skinRecoverySeconds() == 0
                ? "screen.tsf.restore_skin" : "screen.tsf.restore_skin_wait", visual.skinRecoverySeconds())
                : Component.translatable("screen.tsf.leave_zipper"));
        actions.get(2).setTooltip(Tooltip.create(Component.translatable(recovery
                ? "screen.tsf.restore_skin_hint" : "screen.tsf.leave_zipper_hint")));
        actions.get(3).active = !BodyManager.hypnotized(minecraft.player);
        actions.get(4).active = free && visual != null && visual.wearingSkin();
    }

    public void refresh() {
        if (minecraft.level != null && minecraft.player != null) {
            rebuild();
        }
    }

    @Override
    public void tick() {
        if (minecraft.player == null || !minecraft.player.isAlive() || BodyManager.hypnotized(minecraft.player)) {
            onClose();
            return;
        }
        if (++refreshTicks % 20 == 0) {
            refresh();
        }
        updateActions();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xB5080C16);
        graphics.fill(left - 2, top - 2, left + panelWidth + 2, top + panelHeight + 2, 0xFF5575A2);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xFF101A2A);
        graphics.fill(left, top, left + panelWidth, top + 29, 0xFF21334E);
        graphics.text(font, title, left + 12, top + 11, 0xFFE6F2FF);
        graphics.text(font, "TATyKeFei", left + panelWidth - 70, top + 11, 0xFF99B5D5);
        if (minecraft.player != null) {
            Form form = BodyManager.form(minecraft.player);
            Component status = Component.translatable("form.tsf." + form.name().toLowerCase(java.util.Locale.ROOT));
            VisualState visual = TSFClient.visual(minecraft.player.getUUID());
            String bodyName = visual == null ? minecraft.player.getGameProfile().name() : visual.bodyName();
            graphics.text(font, Component.translatable("screen.tsf.status", status, bodyName), left + 12, top + 38, 0xFFB5D6F4);
            if (visual != null && visual.skinLayers() > 0) {
                graphics.text(font, Component.translatable("screen.tsf.layers", visual.skinLayers()),
                        left + panelWidth - 82, top + 38, 0xFFB5D6F4);
            }
        }
        graphics.text(font, Component.translatable("screen.tsf.nearby"), left + 12, top + 55, 0xFF8CA4C2);
        graphics.text(font, Component.translatable("screen.tsf.target_actions"), left + panelWidth / 2 + 2, top + 55, 0xFF8CA4C2);
        if (targets.isEmpty()) {
            graphics.text(font, Component.translatable("screen.tsf.no_players"), left + 16, top + 78, 0xFF7186A1);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
