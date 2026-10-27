package com.lutils.modules;

import com.lutils.LUtils;
import com.lutils.settings.LongSetting;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.ChunkRandom;

public class SlimeChunkHighlight extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .range(2, 100)
        .sliderRange(2, 100)
        .name("Range")
        .description("Slimechunk rendering range in chunks")
        .build());

    private final Setting<Long> seed = sgGeneral.add(new LongSetting.Builder()
        .name("seed")
        .description("The world seed.")
        .defaultValue(-1985258164L)
        .build());

    private final Setting<Long> scrambler = sgGeneral.add(new LongSetting.Builder()
        .name("scrambler")
        .description("The slime chunk scrambler.")
        .defaultValue(987234911L)
        .build());

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("The color of the outline.")
        .defaultValue(new SettingColor(0, 255, 200, 100))
        .build()
    );

    private final Setting<ShapeMode> shapeModeBox = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("Shape mode")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    public SlimeChunkHighlight() {
        super(LUtils.CATEGORY, "Slimechunk Highlight", "Highlights slimechunks with a given worldseed and scrambler");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        ChunkPos playerChunk = mc.player.getChunkPos();
        for(int i = -range.get(); i <= range.get(); i++) for(int j = -range.get(); j <= range.get(); j++) {
            ChunkPos pos = new ChunkPos(playerChunk.x + i, playerChunk.z + j);
            boolean isSlimeChunk = ChunkRandom.getSlimeRandom(pos.x, pos.z, seed.get(), scrambler.get()).nextInt(10) == 0;
            if(!isSlimeChunk) continue;

            event.renderer.box(
                pos.x * 16, -64, pos.z * 16,
                pos.x * 16 + 16, -64, pos.z * 16 + 16,
                color.get(),
                color.get(),
                shapeModeBox.get(),
                0
            );
        }
    }
}

