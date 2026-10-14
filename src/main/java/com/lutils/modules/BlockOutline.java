package com.lutils.modules;

import com.lutils.LUtils;
import com.lutils.utils.render.postprocess.BlockOutlineShader;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.ResolutionChangedEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.*;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.MeshVertexConsumerProvider;
import meteordevelopment.meteorclient.utils.render.SimpleBlockRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.render.postprocess.PostProcessShaders;
import meteordevelopment.orbit.EventHandler;

import meteordevelopment.orbit.listeners.ConsumerListener;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
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
        .visible(() -> breakProgress.get())
        .build()
    );

    private final Mesh mesh;
    private final MeshVertexConsumerProvider vertexConsumerProvider;
    private final BlockOutlineShader shader = new BlockOutlineShader(this);

    public BlockOutline() {
        super(LUtils.CATEGORY, "Block Outline", "Fancier block selection.");

        mesh = new ShaderMesh(Shaders.POS_COLOR, DrawMode.Triangles, Mesh.Attrib.Vec3, Mesh.Attrib.Color);
        vertexConsumerProvider = new MeshVertexConsumerProvider(mesh);

        MeteorClient.EVENT_BUS.subscribe(new ConsumerListener<>(ResolutionChangedEvent.class, event -> {
            if (shader != null) {
                shader.onResized(mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());
            }
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

        SimpleBlockRenderer.render(selectedPos, state, vertexConsumerProvider);

        BlockEntity blockEntity = mc.world.getBlockEntity(selectedPos);
        if (blockEntity != null) {
            BlockEntityRenderer<BlockEntity> renderer = mc.getBlockEntityRenderDispatcher().get(blockEntity);
            if (renderer != null && blockEntity.hasWorld() && blockEntity.getType().supports(state)) {
                renderer.render(blockEntity, event.tickDelta, new MatrixStack(), vertexConsumerProvider,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);

            }
        }

        vertexConsumerProvider.setOffset(selectedPos.getX(), selectedPos.getY(), selectedPos.getZ());

        shader.endRender(() -> mesh.render(event.matrices));
    }
}
