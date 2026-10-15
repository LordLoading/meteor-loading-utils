package com.lutils.utils.render.postprocess;

import com.lutils.modules.BlockOutline;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import meteordevelopment.meteorclient.renderer.MeteorRenderPipelines;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.postprocess.OutlineUniforms;
import meteordevelopment.meteorclient.utils.render.postprocess.PostProcessShader;

public class BlockOutlineShader extends PostProcessShader {
    private static BlockOutline blockOutline;

    public BlockOutlineShader() {
        super(MeteorRenderPipelines.POST_OUTLINE);
    }

    @Override
    protected boolean shouldDraw() {
        if (blockOutline == null) blockOutline = Modules.get().get(BlockOutline.class);
        return blockOutline.isActive();
    }

    @Override
    protected void setupPass(MeshRenderer renderer) {
        renderer.uniform("OutlineData", OutlineUniforms.write(
            blockOutline.outlineWidth.get(),
            blockOutline.fillOpacity.get() / 255.0f,
            blockOutline.shapeMode.get().ordinal(),
            blockOutline.glowMultiplier.get().floatValue()
        ));
    }
}
