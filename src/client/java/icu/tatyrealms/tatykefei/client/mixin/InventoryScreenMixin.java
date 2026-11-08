package icu.tatyrealms.tatykefei.client.mixin;

import icu.tatyrealms.tatykefei.client.TSFClient;
import icu.tatyrealms.tatykefei.state.BodyManager;
import icu.tatyrealms.tatykefei.state.Form;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    @ModifyVariable(method = "extractEntityInInventoryFollowsMouse", at = @At("HEAD"), argsOnly = true)
    private static LivingEntity tsf$slimePreview(LivingEntity entity) {
        return entity instanceof AbstractClientPlayer player && BodyManager.form(player) == Form.SLIME
                ? TSFClient.slime(player) : entity;
    }
}
