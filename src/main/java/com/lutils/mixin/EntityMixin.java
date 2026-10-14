package com.lutils.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.lutils.modules.Eloadra;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public class EntityMixin {
    @ModifyReturnValue(method = "isCrawling", at = @At("RETURN"))
    public boolean modifyIsCrawling(boolean original) {
        if((Entity) (Object) this == MinecraftClient.getInstance().player && Modules.get().isActive(Eloadra.class) && ((Eloadra) (Modules.get().get("Eloadra"))).poseMode.get() == Eloadra.poseModes.WORM) {
            return false;
        } else return original;
    }
}
