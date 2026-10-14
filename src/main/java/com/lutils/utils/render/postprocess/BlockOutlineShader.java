package com.lutils.utils.render.postprocess;

import com.lutils.modules.BlockOutline;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.postprocess.PostProcessShader;
import net.minecraft.entity.Entity;

public class BlockOutlineShader extends PostProcessShader {
    private static BlockOutline blockOutline;

    public BlockOutlineShader(BlockOutline blockOutline) {
        init("outline");
        BlockOutlineShader.blockOutline = blockOutline;
    }

    @Override
    protected void preDraw() {
        framebuffer.clear();
        framebuffer.beginWrite(false);
    }

    @Override
    protected boolean shouldDraw() {
        if (blockOutline == null) blockOutline = Modules.get().get(BlockOutline.class);
        return blockOutline.isActive();
    }

    @Override
    public boolean shouldDraw(Entity entity) {
        return true;
    }

    @Override
    protected void setUniforms() {
        shader.set("u_Width", blockOutline.outlineWidth.get());
        shader.set("u_FillOpacity", blockOutline.fillOpacity.get() / 255.0);
        shader.set("u_ShapeMode", blockOutline.shapeMode.get().ordinal());
        shader.set("u_GlowMultiplier", blockOutline.glowMultiplier.get());
    }
}
