package icu.tatyrealms.tatykefei.client.screen;

import icu.tatyrealms.tatykefei.client.compat.punchymoves.PunchyMovesClientCompat;
import icu.tatyrealms.tatykefei.client.render.BodyPreview;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class FacePickerScreen extends Screen {
    private static final int ROWS = 5;
    private static final int MIN_CELL = 64;
    private static final int GAP = 4;
    private final BodyDisplayScreen parent;
    private int page;
    private int pages = 1;
    private int columns = 4;
    private int perPage = 20;
    private int cell = 72;
    private int gridLeft;
    private int gridWidth;
    private int gridTop;

    public FacePickerScreen(BodyDisplayScreen parent) {
        super(Component.translatable("screen.tsf.face_pick"));
        this.parent = parent;
    }

    public static Component label(String face) {
        if (face == null || face.isEmpty()) {
            return Component.translatable("screen.tsf.face_auto");
        }
        return Component.translatableWithFallback("screen.tsf.face." + face, displayName(face));
    }

    private static String displayName(String face) {
        String trimmed = face.replace('_', ' ');
        return trimmed.isEmpty() ? face : Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }

    @Override
    protected void init() {
        clearWidgets();
        List<String> faces = new ArrayList<>();
        faces.add("");
        faces.addAll(PunchyMovesClientCompat.lookNames());
        int area = Math.max(120, width / 2 - 32);
        columns = Math.max(2, Math.min(4, (area + GAP) / (MIN_CELL + GAP)));
        cell = Math.max(40, (area - (columns - 1) * GAP) / columns);
        perPage = columns * ROWS;
        gridWidth = columns * cell + (columns - 1) * GAP;
        gridLeft = width / 2 + 8;
        gridTop = height / 2 - 60;
        pages = (faces.size() + perPage - 1) / perPage;
        page = Math.min(Math.max(page, 0), pages - 1);
        int start = page * perPage;
        for (int index = 0; index < perPage; index++) {
            int option = start + index;
            if (option >= faces.size()) {
                break;
            }
            String face = faces.get(option);
            Component text = label(face);
            if (face.equals(parent.face())) {
                text = Component.literal("> ").append(text);
            }
            int column = index % columns;
            int row = index / columns;
            addRenderableWidget(Button.builder(text, button -> choose(face))
                    .bounds(gridLeft + column * (cell + GAP), gridTop + row * 24, cell, 20).build());
        }
        int nav = gridTop + ROWS * 24 + 6;
        int navWidth = Math.max(48, Math.min(92, (gridWidth - 8) / 2));
        Button previous = addRenderableWidget(Button.builder(Component.translatable("screen.tsf.face_previous"),
                button -> turn(-1)).bounds(gridLeft, nav, navWidth, 20).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.translatable("screen.tsf.face_next"),
                button -> turn(1)).bounds(gridLeft + gridWidth - navWidth, nav, navWidth, 20).build());
        next.active = page < pages - 1;
        int backWidth = Math.min(200, gridWidth);
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(gridLeft + (gridWidth - backWidth) / 2, nav + 24, backWidth, 20).build());
    }

    private void turn(int delta) {
        page += delta;
        rebuildWidgets();
    }

    private void choose(String face) {
        parent.applyFace(face);
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        BodyPreview.extractForColumn(graphics, parent.entityId(), width, height, width / 2 + 8, mouseX, mouseY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int titleX = gridLeft + gridWidth / 2;
        graphics.centeredText(font, title, titleX, height / 2 - 116, 0xFFFFFFFF);
        if (pages > 1) {
            graphics.centeredText(font, Component.literal((page + 1) + " / " + pages),
                    titleX, gridTop + ROWS * 24 + 12, 0xFFAAAAAA);
        }
        if (!PunchyMovesClientCompat.available()) {
            graphics.centeredText(font, Component.translatable("screen.tsf.face_unavailable"),
                    titleX, height / 2 - 100, 0xFFFF8080);
        }
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
