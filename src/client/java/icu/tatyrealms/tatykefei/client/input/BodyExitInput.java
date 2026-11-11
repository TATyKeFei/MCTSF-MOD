package icu.tatyrealms.tatykefei.client.input;

import icu.tatyrealms.tatykefei.client.TSFClient;
import icu.tatyrealms.tatykefei.network.Action;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class BodyExitInput {
    private static final int RETURN_HOLD_TICKS = 20;
    private static int heldTicks;
    private static boolean returned;
    private static boolean cancelled;

    private BodyExitInput() {
    }

    public static void tick(Minecraft client, KeyMapping key) {
        boolean clicked = false;
        while (key.consumeClick()) {
            clicked = true;
        }
        if (client.player == null || client.gui.screen() != null) {
            clear();
            cancelled = key.isDown();
            return;
        }
        if (cancelled) {
            cancelled = key.isDown();
            return;
        }
        if (key.isDown()) {
            if (!returned && ++heldTicks >= RETURN_HOLD_TICKS) {
                TSFClient.send(Action.RETURN, null);
                returned = true;
            }
        } else {
            if (!returned && (heldTicks > 0 || clicked)) {
                TSFClient.send(Action.EXIT, null);
            }
            clear();
        }
    }

    public static void clear() {
        heldTicks = 0;
        returned = false;
        cancelled = false;
    }
}
