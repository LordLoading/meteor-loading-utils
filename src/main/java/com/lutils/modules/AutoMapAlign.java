package com.lutils.modules;

import com.lutils.LUtils;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.util.math.BlockPos;

public class AutoMapAlign extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> xOffset = sgGeneral.add(new IntSetting.Builder()
        .name("X offset")
        .description("X Offset from the map grid.")
        .range(-64, 64)
        .sliderRange(-64, 64)
        .defaultValue(0)
        .build()
    );

    private final Setting<Integer> zOffset = sgGeneral.add(new IntSetting.Builder()
        .name("Z offset")
        .description("Z Offset from the map grid.")
        .range(-64, 64)
        .sliderRange(-64, 64)
        .defaultValue(0)
        .build()
    );

    private final Setting<Boolean> useY = sgGeneral.add(new BoolSetting.Builder()
        .name("use Y")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> y = sgGeneral.add(new IntSetting.Builder()
        .name("y offset")
        .description("y level for placement.")
        .range(-64, 384)
        .sliderRange(-64, 384)
        .defaultValue(61)
        .build()
    );

    public AutoMapAlign() {
        super(LUtils.CATEGORY, "Auto Map Align", "Automatically aligns currnently selected schematic to the map grid.");
    }

    public void onActivate() {
        SchematicPlacement selected = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
        BlockPos pos = mc.player.getBlockPos();

        pos = pos.add(-64, 0, -64);
        // this just rounds x and z down to the nearest multiple of 128
        pos = new BlockPos(pos.getX()&0xFFFFFF80, pos.getY(),pos.getZ()&0xFFFFFF80);
        pos = pos.add(64, 0, 64);
        pos = pos.add(xOffset.get(), 0, zOffset.get());
        if (useY.get()) pos = new BlockPos(pos.getX(), y.get(), pos.getZ());

        selected.setOrigin(pos, s -> {});

        toggle();
    }
}
