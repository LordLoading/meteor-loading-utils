//this module and everything to do with it is mostly vibecoded cos idk how to do the rendering stuff

package com.lutils.modules;

import com.lutils.LUtils;
import com.lutils.utils.render.postprocess.BlockOutlineShader;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.mixin.ClientPlayerInteractionManagerAccessor;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.shape.VoxelShape;

public class BlockOutline extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the outline is rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Integer> fillOpacity = sgGeneral.add(new IntSetting.Builder()
        .name("fill-opacity")
        .description("Opacity of the fill.")
        .defaultValue(50)
        .range(0, 255)
        .sliderMax(255)
        .build()
    );

    private final Setting<Integer> outlineWidth = sgGeneral.add(new IntSetting.Builder()
        .name("outline-width")
        .description("Width of the shader outline.")
        .defaultValue(1)
        .range(1, 10)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Double> glowMultiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("glow-multiplier")
        .description("Multiplier for glow effect.")
        .defaultValue(3.5)
        .min(0)
        .sliderMax(10)
        .decimalPlaces(3)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("The color of the outline.")
        .defaultValue(new SettingColor(0, 255, 200, 255))
        .build()
    );

    private final Setting<Boolean> breakProgress = sgGeneral.add(new BoolSetting.Builder()
        .name("break progress")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> breakProgressColor = sgGeneral.add(new ColorSetting.Builder()
        .name("break progress color")
        .description("The color of the outline when the block is being broken.")
        .defaultValue(new SettingColor(255, 100, 100, 255))
        .visible(() -> breakProgress.get())
        .build()
    );

    private final BlockOutlineShader blockOutlineShader;

    public BlockOutline() {
        super(LUtils.CATEGORY, "Shader Block Outline", "Fancier block selection.");
        blockOutlineShader = new BlockOutlineShader(
            () -> outlineWidth.get(),
            () -> fillOpacity.get() / 255.0f,
            () -> shapeMode.get().ordinal(),
            () -> glowMultiplier.get().floatValue(),
            this::isActive
        );
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.crosshairTarget == null || !(mc.crosshairTarget instanceof BlockHitResult result) || result.getType() == HitResult.Type.MISS) return;
        BlockPos bp = result.getBlockPos();
        VoxelShape shape = mc.world.getBlockState(bp).getOutlineShape(mc.world, bp);

        Color c = new Color(ColorHelper.lerp(
                ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).meteor$getBreakingProgress(),
                color.get().getPacked(),
                breakProgressColor.get().getPacked()));

        blockOutlineShader.beginRender();

        blockOutlineShader.meshBegin();

        for (Box b : shape.getBoundingBoxes()) {
            blockOutlineShader.renderBox(
                b.minX + bp.getX(), b.minY + bp.getY(), b.minZ + bp.getZ(),
                b.maxX + bp.getX(),  b.maxY + bp.getY(), b.maxZ + bp.getZ(),
                breakProgress.get() ? c : color.get()
            );
        }

        blockOutlineShader.endRender(event.matrices);
    }
}
