package com.lutils.mixin;

import com.lutils.modules.BlockOutline;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Inject(method = "drawBlockOutline", at = @At("HEAD"), cancellable = true)
    private void onDrawBlockOutline(net.minecraft.client.util.math.MatrixStack matrices, net.minecraft.client.render.VertexConsumer vertexConsumer, Entity entity, double cameraX, double cameraY, double cameraZ, BlockPos pos, net.minecraft.block.BlockState state, int i, CallbackInfo ci) {
        if (Modules.get().isActive(BlockOutline.class)) {
            ci.cancel();
        }
    }
}