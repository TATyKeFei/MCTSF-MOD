package icu.tatyrealms.tatykefei.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

public final class ObserverInventoryScreen extends InventoryScreen {
    private final AbstractClientPlayer target;

    public ObserverInventoryScreen(AbstractClientPlayer target) {
        super(target);
        this.target = target;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        leftPos = (width - imageWidth) / 2;
    }

    @Override
    public void containerTick() {
        AbstractClientPlayer current = ObserverView.target();
        if (current == null) {
            onClose();
        } else if (current != target) {
            minecraft.gui.setScreen(new ObserverInventoryScreen(current));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractContents(graphics, mouseX, mouseY, partialTick);
        extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(graphics);
        graphics.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_LOCATION, leftPos, topPos, 0, 0,
                imageWidth, imageHeight, 256, 256);
        extractEntityInInventoryFollowsMouse(graphics, leftPos + 26, topPos + 8, leftPos + 75, topPos + 78,
                30, 0.0625F, mouseX, mouseY, target);
    }

    @Override
    protected void extractSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                extractSlot(graphics, slot, mouseX, mouseY);
            }
        }
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int button, ContainerInput input) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE || minecraft.options.keyInventory.matches(event)) {
            onClose();
        }
        return true;
    }

    @Override
    public void removed() {
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(null);
    }
}
