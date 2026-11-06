package icu.tatyrealms.tatykefei.client.mixin;

import icu.tatyrealms.tatykefei.state.BodyManager;
import icu.tatyrealms.tatykefei.state.Form;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void tsf$hypnosis(CallbackInfo callback) {
        var player = Minecraft.getInstance().player;
        if (player != null && (BodyManager.hypnotized(player) || BodyManager.form(player) == Form.OBSERVER)) {
            keyPresses = Input.EMPTY;
            moveVector = Vec2.ZERO;
        }
    }
}
