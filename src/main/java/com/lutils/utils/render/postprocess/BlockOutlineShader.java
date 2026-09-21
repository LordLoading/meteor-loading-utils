package com.lutils.utils.render.postprocess;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.renderer.MeshBuilder;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import meteordevelopment.meteorclient.renderer.MeteorRenderPipelines;
import meteordevelopment.meteorclient.renderer.MeshUniforms;
import meteordevelopment.meteorclient.utils.render.postprocess.OutlineUniforms;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.util.math.MatrixStack;

import java.nio.ByteBuffer;
import java.util.OptionalInt;
import java.util.function.Supplier;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static org.lwjgl.glfw.GLFW.glfwGetTime;

public class BlockOutlineShader {
    public Framebuffer framebuffer;
    public final MeshBuilder meshBuilder;
    private final Supplier<Integer> widthSupplier;
    private final Supplier<Float> fillOpacitySupplier;
    private final Supplier<Integer> shapeModeSupplier;
    private final Supplier<Float> glowMultiplierSupplier;
    private final Supplier<Boolean> enabledSupplier;

    private static final int POST_UNIFORM_SIZE = new Std140SizeCalculator()
        .putVec2()
        .putFloat()
        .get();

    private static final DynamicUniformStorage<PostData> POST_UNIFORM_STORAGE = new DynamicUniformStorage<>("BlockOutline - Post UBO", POST_UNIFORM_SIZE, 16);

    private record PostData(float sizeX, float sizeY, float time) implements DynamicUniformStorage.Uploadable {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec2(sizeX, sizeY)
                .putFloat(time);
        }
    }

    public BlockOutlineShader(
        Supplier<Integer> widthSupplier,
        Supplier<Float> fillOpacitySupplier,
        Supplier<Integer> shapeModeSupplier,
        Supplier<Float> glowMultiplierSupplier,
        Supplier<Boolean> enabledSupplier
    ) {
        this.widthSupplier = widthSupplier;
        this.fillOpacitySupplier = fillOpacitySupplier;
        this.shapeModeSupplier = shapeModeSupplier;
        this.glowMultiplierSupplier = glowMultiplierSupplier;
        this.enabledSupplier = enabledSupplier;

        framebuffer = new SimpleFramebuffer(MeteorClient.NAME + " BlockOutlineShader", mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight(), true);
        meshBuilder = new MeshBuilder(MeteorRenderPipelines.WORLD_COLORED);
    }

    public void beginRender() {
        if (!enabledSupplier.get()) return;
        resizeIfNeeded();
        RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "BlockOutline Clear", framebuffer.getColorAttachmentView(), OptionalInt.of(0)).close();
    }

    public void endRender(MatrixStack matrices) {
        if (!enabledSupplier.get()) return;

        MeshRenderer.begin()
            .attachments(framebuffer)
            .pipeline(MeteorRenderPipelines.WORLD_COLORED)
            .mesh(meshBuilder, matrices)
            .end();

        MeshRenderer.begin()
            .attachments(mc.getFramebuffer())
            .pipeline(MeteorRenderPipelines.POST_OUTLINE)
            .fullscreen()
            .uniform("PostData", POST_UNIFORM_STORAGE.write(new PostData(
                (float) mc.getWindow().getFramebufferWidth(),
                (float) mc.getWindow().getFramebufferHeight(),
                (float) glfwGetTime()
            )))
            .sampler("u_Texture", framebuffer.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST))
            .uniform("OutlineData", OutlineUniforms.write(
                widthSupplier.get(),
                fillOpacitySupplier.get(),
                shapeModeSupplier.get(),
                glowMultiplierSupplier.get()
            ))
            .end();
    }

    public void meshBegin() {
        meshBuilder.begin();
    }

    public void meshRender(MatrixStack matrices) {
        MeshRenderer.begin()
            .attachments(mc.getFramebuffer())
            .pipeline(MeteorRenderPipelines.WORLD_COLORED)
            .mesh(meshBuilder, matrices)
            .end();
    }

    public void renderBox(double x1, double y1, double z1, double x2, double y2, double z2, Color color) {
        meshBuilder.ensureCapacity(24, 36);

        int i0 = meshBuilder.vec3(x1, y1, z2).color(color).next();
        int i1 = meshBuilder.vec3(x2, y1, z2).color(color).next();
        int i2 = meshBuilder.vec3(x2, y2, z2).color(color).next();
        int i3 = meshBuilder.vec3(x1, y2, z2).color(color).next();
        meshBuilder.quad(i0, i1, i2, i3);

        i0 = meshBuilder.vec3(x2, y1, z1).color(color).next();
        i1 = meshBuilder.vec3(x1, y1, z1).color(color).next();
        i2 = meshBuilder.vec3(x1, y2, z1).color(color).next();
        i3 = meshBuilder.vec3(x2, y2, z1).color(color).next();
        meshBuilder.quad(i0, i1, i2, i3);

        i0 = meshBuilder.vec3(x1, y2, z1).color(color).next();
        i1 = meshBuilder.vec3(x1, y2, z2).color(color).next();
        i2 = meshBuilder.vec3(x2, y2, z2).color(color).next();
        i3 = meshBuilder.vec3(x2, y2, z1).color(color).next();
        meshBuilder.quad(i0, i1, i2, i3);

        i0 = meshBuilder.vec3(x1, y1, z1).color(color).next();
        i1 = meshBuilder.vec3(x2, y1, z1).color(color).next();
        i2 = meshBuilder.vec3(x2, y1, z2).color(color).next();
        i3 = meshBuilder.vec3(x1, y1, z2).color(color).next();
        meshBuilder.quad(i0, i1, i2, i3);

        i0 = meshBuilder.vec3(x2, y1, z1).color(color).next();
        i1 = meshBuilder.vec3(x2, y2, z1).color(color).next();
        i2 = meshBuilder.vec3(x2, y2, z2).color(color).next();
        i3 = meshBuilder.vec3(x2, y1, z2).color(color).next();
        meshBuilder.quad(i0, i1, i2, i3);

        i0 = meshBuilder.vec3(x1, y1, z1).color(color).next();
        i1 = meshBuilder.vec3(x1, y1, z2).color(color).next();
        i2 = meshBuilder.vec3(x1, y2, z2).color(color).next();
        i3 = meshBuilder.vec3(x1, y2, z1).color(color).next();
        meshBuilder.quad(i0, i1, i2, i3);
    }

    private void resizeIfNeeded() {
        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();

        if (framebuffer.textureWidth == width && framebuffer.textureHeight == height) {
            return;
        }

        framebuffer.resize(width, height);
    }
}
