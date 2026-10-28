package com.lutils.modules;

import com.lutils.LUtils;
import com.lutils.utils.render.postprocess.BlockOutlineShader;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.ResolutionChangedEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.*;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.MeshBuilderVertexConsumerProvider;
import meteordevelopment.meteorclient.utils.render.SimpleBlockRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import meteordevelopment.orbit.listeners.ConsumerListener;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ColorHelper;

public class BlockOutline extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the outline is rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    public final Setting<Integer> fillOpacity = sgGeneral.add(new IntSetting.Builder()
        .name("fill-opacity")
        .description("Opacity of the fill.")
        .defaultValue(50)
        .range(0, 255)
        .sliderMax(255)
        .build()
    );

    public final Setting<Integer> outlineWidth = sgGeneral.add(new IntSetting.Builder()
        .name("outline-width")
        .description("Width of the shader outline.")
        .defaultValue(1)
        .range(1, 10)
        .sliderRange(1, 5)
        .build()
    );

    public final Setting<Double> glowMultiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("glow-multiplier")
        .description("Multiplier for glow effect.")
        .defaultValue(3.5)
        .min(0)
        .sliderMax(10)
        .decimalPlaces(3)
        .build()
    );

    public final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("The color of the outline.")
        .defaultValue(new SettingColor(0, 255, 200, 255))
        .build()
    );

    public final Setting<Boolean> hideInside = sgGeneral.add(new BoolSetting.Builder()
        .name("hide inside")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> breakProgress = sgGeneral.add(new BoolSetting.Builder()
        .name("break progress")
        .defaultValue(true)
        .build()
    );

    public final Setting<SettingColor> breakProgressColor = sgGeneral.add(new ColorSetting.Builder()
        .name("break progress color")
        .description("The color of the outline when the block is being broken.")
        .defaultValue(new SettingColor(255, 100, 100, 255))
        .visible(breakProgress::get)
        .build()
    );

    private final MeshBuilder mesh;
    private final MeshBuilderVertexConsumerProvider vertexConsumerProvider;
    private final BlockOutlineShader shader = new BlockOutlineShader();

    public BlockOutline() {
        super(LUtils.CATEGORY, "Block Outline", "Fancier block selection.");

        mesh = new MeshBuilder(MeteorRenderPipelines.WORLD_COLORED);
        vertexConsumerProvider = new MeshBuilderVertexConsumerProvider(mesh);

        MeteorClient.EVENT_BUS.subscribe(new ConsumerListener<>(ResolutionChangedEvent.class, event -> {
            shader.onResized(mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());
        }));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.crosshairTarget == null || !(mc.crosshairTarget instanceof BlockHitResult result) || result.getType() == HitResult.Type.MISS) return;

        if (hideInside.get() && result.isInsideBlock()) return;

        BlockPos selectedPos = result.getBlockPos();

        if (selectedPos == null) return;

        mesh.begin();
        
        if (!breakProgress.get()) vertexConsumerProvider.setColor(color.get());
        else {
            assert mc.interactionManager != null;
            vertexConsumerProvider.setColor(
                new Color(ColorHelper.lerp(
                    mc.interactionManager.getBlockBreakingProgress() == -1 ? 0 : (float) mc.interactionManager.getBlockBreakingProgress() /10,
                    color.get().getPacked(),
                    breakProgressColor.get().getPacked()
                )));
        }

        BlockState state = mc.world.getBlockState(selectedPos);
        BlockEntity blockEntity = mc.world.getBlockEntity(selectedPos);

        if (blockEntity != null) {
            SimpleBlockRenderer.renderWithBlockEntity(blockEntity, event.tickDelta, vertexConsumerProvider);
        }
        else {
            vertexConsumerProvider.setOffset(selectedPos.getX(), selectedPos.getY(), selectedPos.getZ());
            SimpleBlockRenderer.render(selectedPos, state, vertexConsumerProvider);
            vertexConsumerProvider.setOffset(0, 0, 0);
        }

        int indexCount = mesh.getIndicesCount();

        MeshRenderer.begin()
            .attachments(shader.framebuffer)
            .clearColor(Color.CLEAR)
            .pipeline(MeteorRenderPipelines.WORLD_COLORED)
            .mesh(mesh, event.matrices)
            .end();

        if (indexCount > 0) shader.render();

        if (mesh.isBuilding()) mesh.end();

    }
}
