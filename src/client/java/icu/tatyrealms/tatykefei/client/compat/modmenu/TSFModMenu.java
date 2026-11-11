package icu.tatyrealms.tatykefei.client.compat.modmenu;

import icu.tatyrealms.tatykefei.client.screen.TSFSettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class TSFModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return TSFSettingsScreen::new;
    }
}
