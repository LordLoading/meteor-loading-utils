package com.lutils.modules;

import com.lutils.LUtils;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class ChestSwap extends Module {
    public ChestSwap() {
        super(LUtils.CATEGORY, "Working Chest Swap", "swaps chestplate and elytra on activation");
    }

    @Override
    public void onActivate() {
        int highestScore = 0;
        int bestSlot = -1;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            int currentScore = 0;
            ItemStack iStack = mc.player.getInventory().getStack(i);
            if (iStack.getItem() == Items.NETHERITE_CHESTPLATE) {
                iStack.getItem().getComponents().
            };
            System.out.println(i);
            System.out.println(iStack.getItem());
        }
    }
}
