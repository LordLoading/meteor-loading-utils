package com.lutils.utils.render.postprocess;

import meteordevelopment.meteorclient.renderer.GL;
import meteordevelopment.meteorclient.renderer.Mesh;
import meteordevelopment.meteorclient.utils.render.MeshVertexConsumerProvider;
import meteordevelopment.meteorclient.renderer.ShaderMesh;
import meteordevelopment.meteorclient.renderer.Shaders;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.apache.commons.io.IOUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL32C;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class BlockOutlineShader {
    public SimpleFramebuffer framebuffer;
    public final MeshVertexConsumerProvider vertexConsumerProvider;
    private final Mesh mesh;
    private int programId;
    private final Supplier<Integer> widthSupplier;
    private final Supplier<Float> fillOpacitySupplier;
    private final Supplier<Integer> shapeModeSupplier;
    private final Supplier<Float> glowMultiplierSupplier;
    private final Supplier<Boolean> enabledSupplier;

    private int uSize;
    private int uTexture;
    private int uTime;
    private int uWidth;
    private int uFillOpacity;
    private int uShapeMode;
    private int uGlowMultiplier;

    private int quadVao;
    private int quadVbo;
    private int quadIbo;

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

        mesh = new ShaderMesh(
            Shaders.POS_COLOR,
            meteordevelopment.meteorclient.renderer.DrawMode.Triangles,
            Mesh.Attrib.Vec3,
            Mesh.Attrib.Color
        );
        vertexConsumerProvider = new MeshVertexConsumerProvider(mesh);
        loadShaders();
        cacheUniforms();

        int w = mc.getWindow().getFramebufferWidth();
        int h = mc.getWindow().getFramebufferHeight();
        framebuffer = new SimpleFramebuffer(w, h, true);

        initQuad();
    }

    private void initQuad() {
        float[] vertices = {
            -1f, -1f, 0f, 1f,
             1f, -1f, 0f, 1f,
             1f,  1f, 0f, 1f,
            -1f,  1f, 0f, 1f
        };
        int[] indices = {
            0, 1, 2,
            0, 2, 3
        };

        ByteBuffer vertexBuffer = BufferUtils.createByteBuffer(vertices.length * 4);
        for (float v : vertices) vertexBuffer.putFloat(v);
        vertexBuffer.flip();

        ByteBuffer indexBuffer = BufferUtils.createByteBuffer(indices.length * 4);
        for (int i : indices) indexBuffer.putInt(i);
        indexBuffer.flip();

        quadVao = GL.genVertexArray();
        GL.bindVertexArray(quadVao);

        quadVbo = GL.genBuffer();
        GL.bindVertexBuffer(quadVbo);
        GL.bufferData(GL32C.GL_ARRAY_BUFFER, vertexBuffer, GL32C.GL_STATIC_DRAW);

        GL.enableVertexAttribute(0);
        GL.vertexAttribute(0, 4, GL32C.GL_FLOAT, false, 16, 0L);

        quadIbo = GL.genBuffer();
        GL.bindIndexBuffer(quadIbo);
        GL.bufferData(GL32C.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL32C.GL_STATIC_DRAW);

        GL.bindVertexArray(0);
    }

    private void drawQuad() {
        GL.bindVertexArray(quadVao);
        GL.drawElements(GL32C.GL_TRIANGLES, 6, GL32C.GL_UNSIGNED_INT);
        GL.bindVertexArray(0);
    }

    private void loadShaders() {
        try {
            String vertSource = readShader("post-process/base.vert");
            String fragSource = readShader("post-process/outline.frag");

            int vert = GL.createShader(GL32C.GL_VERTEX_SHADER);
            GL.shaderSource(vert, vertSource);
            String vertError = GL.compileShader(vert);
            if (vertError != null) {
                GL.deleteShader(vert);
                throw new RuntimeException("Failed to compile block outline vertex shader: " + vertError);
            }

            int frag = GL.createShader(GL32C.GL_FRAGMENT_SHADER);
            GL.shaderSource(frag, fragSource);
            String fragError = GL.compileShader(frag);
            if (fragError != null) {
                GL.deleteShader(frag);
                throw new RuntimeException("Failed to compile block outline fragment shader: " + fragError);
            }

            programId = GL.createProgram();
            GL32C.glAttachShader(programId, vert);
            GL32C.glAttachShader(programId, frag);
            GL32C.glLinkProgram(programId);

            String linkError = GL32C.glGetProgramInfoLog(programId, 512);
            if (linkError != null && !linkError.isEmpty()) {
                GL.deleteProgram(programId);
                throw new RuntimeException("Failed to link block outline shader program: " + linkError);
            }

            GL.deleteShader(vert);
            GL.deleteShader(frag);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load block outline shaders", e);
        }
    }

    private void cacheUniforms() {
        GL.useProgram(programId);
        uSize = GL.getUniformLocation(programId, "u_Size");
        uTexture = GL.getUniformLocation(programId, "u_Texture");
        uTime = GL.getUniformLocation(programId, "u_Time");
        uWidth = GL.getUniformLocation(programId, "u_Width");
        uFillOpacity = GL.getUniformLocation(programId, "u_FillOpacity");
        uShapeMode = GL.getUniformLocation(programId, "u_ShapeMode");
        uGlowMultiplier = GL.getUniformLocation(programId, "u_GlowMultiplier");
    }

    private String readShader(String path) throws IOException {
        return IOUtils.toString(
            mc.getResourceManager().getResource(Identifier.of("lutils", "shaders/" + path)).get().getInputStream(),
            StandardCharsets.UTF_8
        );
    }

    public void beginRender() {
        if (!enabledSupplier.get()) return;
        resizeIfNeeded();
        framebuffer.clear();
        mc.getFramebuffer().beginWrite(false);
    }

    public void endRender(Runnable drawMesh) {
        if (!enabledSupplier.get()) return;

        GL.saveState();
        GL.disableDepth();
        framebuffer.beginWrite(false);
        drawMesh.run();
        mc.getFramebuffer().beginWrite(false);
        GL.restoreState();

        GL.bindTexture(framebuffer.getColorAttachment(), 0);

        GL.useProgram(programId);

        GL.uniformFloat2(uSize, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());
        GL.uniformInt(uTexture, 0);
        GL.uniformFloat(uTime, (float) GLFW.glfwGetTime());
        GL.uniformInt(uWidth, widthSupplier.get());
        GL.uniformFloat(uFillOpacity, fillOpacitySupplier.get());
        GL.uniformInt(uShapeMode, shapeModeSupplier.get());
        GL.uniformFloat(uGlowMultiplier, glowMultiplierSupplier.get());

        GL.saveState();
        GL.disableDepth();
        GL.enableBlend();
        GL.disableCull();
        GL.enableLineSmooth();

        drawQuad();

        GL.restoreState();
    }

    private void resizeIfNeeded() {
        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();

        if (framebuffer.textureWidth == width && framebuffer.textureHeight == height) {
            return;
        }

        framebuffer.resize(width, height);
    }

    public void meshBegin() {
        mesh.begin();
    }

    public void meshRender(MatrixStack matrices) {
        mesh.render(matrices);
    }

    public void renderBox(double x1, double y1, double z1, double x2, double y2, double z2, meteordevelopment.meteorclient.utils.render.color.Color color) {
        int i0 = mesh.vec3(x1, y1, z2).color(color).next();
        int i1 = mesh.vec3(x2, y1, z2).color(color).next();
        int i2 = mesh.vec3(x2, y2, z2).color(color).next();
        int i3 = mesh.vec3(x1, y2, z2).color(color).next();
        mesh.quad(i0, i1, i2, i3);

        i0 = mesh.vec3(x2, y1, z1).color(color).next();
        i1 = mesh.vec3(x1, y1, z1).color(color).next();
        i2 = mesh.vec3(x1, y2, z1).color(color).next();
        i3 = mesh.vec3(x2, y2, z1).color(color).next();
        mesh.quad(i0, i1, i2, i3);

        i0 = mesh.vec3(x1, y2, z1).color(color).next();
        i1 = mesh.vec3(x1, y2, z2).color(color).next();
        i2 = mesh.vec3(x2, y2, z2).color(color).next();
        i3 = mesh.vec3(x2, y2, z1).color(color).next();
        mesh.quad(i0, i1, i2, i3);

        i0 = mesh.vec3(x1, y1, z1).color(color).next();
        i1 = mesh.vec3(x2, y1, z1).color(color).next();
        i2 = mesh.vec3(x2, y1, z2).color(color).next();
        i3 = mesh.vec3(x1, y1, z2).color(color).next();
        mesh.quad(i0, i1, i2, i3);

        i0 = mesh.vec3(x2, y1, z1).color(color).next();
        i1 = mesh.vec3(x2, y2, z1).color(color).next();
        i2 = mesh.vec3(x2, y2, z2).color(color).next();
        i3 = mesh.vec3(x2, y1, z2).color(color).next();
        mesh.quad(i0, i1, i2, i3);

        i0 = mesh.vec3(x1, y1, z1).color(color).next();
        i1 = mesh.vec3(x1, y1, z2).color(color).next();
        i2 = mesh.vec3(x1, y2, z2).color(color).next();
        i3 = mesh.vec3(x1, y2, z1).color(color).next();
        mesh.quad(i0, i1, i2, i3);
    }
}
