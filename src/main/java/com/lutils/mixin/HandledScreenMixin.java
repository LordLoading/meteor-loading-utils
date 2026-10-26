package com.lutils.mixin;

import com.lutils.modules.NamedItemHighlight;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public class HandledScreenMixin {
    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void onDrawSlot(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (Modules.get().isActive(NamedItemHighlight.class)) {
            int color = Modules.get().get(NamedItemHighlight.class).color.get().getPacked();
            ItemStack stack = slot.getStack();
            if (stack.getCustomName() != null) {
                context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
            }
        }
    }
}
