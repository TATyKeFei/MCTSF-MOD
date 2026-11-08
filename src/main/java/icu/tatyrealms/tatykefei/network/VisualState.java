package icu.tatyrealms.tatykefei.network;

import com.google.gson.JsonElement;
import icu.tatyrealms.tatykefei.state.Form;
import java.util.UUID;

public record VisualState(UUID player, Form form, UUID body, String bodyName, JsonElement profile,
                          boolean hypnotized, boolean wearingSkin, int skinLayers, boolean zipperControl,
                          int skinRecoverySeconds, Integer slimeColor, UUID hypnotist, int hypnosisRestrictions,
                          UUID appearanceOwner, UUID natureSkin) {
}
