package icu.tatyrealms.tatykefei.client.mixin;

import icu.tatyrealms.tatykefei.state.BodyManager;
import icu.tatyrealms.tatykefei.state.Form;
import icu.tatyrealms.tatykefei.client.ObserverView;
import icu.tatyrealms.tatykefei.client.render.FirstPersonArmSway;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonRendererMixin {
    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER, ordinal = 0))
    private void tsf$armSway(PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, float partialTick,
                             float pitch, InteractionHand hand, float swing, ItemStack stack, float equip,
                             PoseStack poses, SubmitNodeCollector collector, int light, CallbackInfo callback) {
        if (Minecraft.getInstance().options.bobView().get()) {
            FirstPersonArmSway.apply(poses, player.avatarRenderState, hand, hands.useItemRemainingTicks > 0);
        }
    }

    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void tsf$hideHands(CallbackInfo callback) {
        var player = Minecraft.getInstance().player;
        if (player != null && BodyManager.form(player) != Form.HUMAN && ObserverView.target() == null
                && !(BodyManager.form(player) == Form.SPIRIT
                && (BodyManager.spiritSpear(player.getMainHandItem()) || BodyManager.spiritSpear(player.getOffhandItem())))) {
            callback.cancel();
        }
    }
}
