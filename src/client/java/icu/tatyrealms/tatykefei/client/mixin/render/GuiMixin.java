package icu.tatyrealms.tatykefei.client.mixin.render;

import icu.tatyrealms.tatykefei.client.observer.ObserverView;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen tsf$observerInventory(Screen screen) {
        return ObserverView.inventoryScreen(screen);
    }
}
