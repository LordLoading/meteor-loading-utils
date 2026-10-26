package com.lutils.modules;

import com.lutils.LUtils;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public class NamedItemHighlight extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("The color of the outline.")
        .defaultValue(new SettingColor(0, 255, 200, 255))
        .build()
    );

    public NamedItemHighlight() {
        super(LUtils.CATEGORY, "Named Item Highlight", "Highlights renamed items in inventories.");
    }
}
