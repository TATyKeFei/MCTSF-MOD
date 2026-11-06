package icu.tatyrealms.tatykefei.client.mixin;

import icu.tatyrealms.tatykefei.client.ObserverInventoryScreen;
import icu.tatyrealms.tatykefei.client.ObserverView;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen tsf$observerInventory(Screen screen) {
        return screen instanceof InventoryScreen && !(screen instanceof ObserverInventoryScreen) && ObserverView.target() != null
                ? new ObserverInventoryScreen(ObserverView.target()) : screen;
    }
}
