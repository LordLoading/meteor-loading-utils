package com.lutils;

import com.lutils.modules.*;
import com.lutils.settings.LongSetting;
import com.lutils.utils.OtherModsUtils;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class LUtils extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("LUtils");

    @Override
    public void onInitialize() {
        LOG.info("Initializing LUtils");

        SettingsWidgetFactory.registerCustomFactory(LongSetting.class, theme -> (table, setting) -> {
            LongSetting longSetting = (LongSetting) setting;
            WTextBox textBox = table.add(theme.textBox(Long.toString(longSetting.get()), (text, c) -> c == '-' || (c >= '0' && c <= '9'))).expandX().widget();

            textBox.actionOnUnfocused = () -> {
                try {
                    if (!longSetting.set(Long.parseLong(textBox.get().trim()))) textBox.set(Long.toString(longSetting.get()));
                } catch (NumberFormatException ignored) {
                    textBox.set(Long.toString(longSetting.get()));
                }
            };
        });

        // Modules
        Modules.get().add(new Eloadra());
        Modules.get().add(new Explore());
        Modules.get().add(new AutoDrool());
        Modules.get().add(new SignAura());
        Modules.get().add(new AutoMapCopy());
        Modules.get().add(new BlockOutline());
        Modules.get().add(new NamedItemHighlight());
        Modules.get().add(new SlimeChunkHighlight());
        Modules.get().add(new NetherPath());
        if (OtherModsUtils.litematicaLoaded() && OtherModsUtils.malilibLoaded()) {
            Modules.get().add(new AutoMapAlign());
        };
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.lutils";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("Loading...", "LUtils");
    }
}
