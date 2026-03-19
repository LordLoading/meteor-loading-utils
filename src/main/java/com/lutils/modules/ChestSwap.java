package com.lutils.modules;

import com.lutils.LUtils;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;

public class ChestSwap extends Module {
    public ChestSwap() {
        super(LUtils.CATEGORY, "Working Chest Swap", "swaps chestplate and elytra on activation");
    }

    @Override
    public void onActivate() {
        boolean ely = mc.player.getInventory().getArmorStack(2).getItem() == Items.ELYTRA;
        int highestScore = 0;
        int bestSlot = -1;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack iStack = mc.player.getInventory().getStack(i);
            int binding = Utils.getEnchantmentLevel(iStack, Enchantments.BINDING_CURSE);
            if (binding != 0) continue;
            if (ely) {
                int currentScore = -1;
                int prot = Utils.getEnchantmentLevel(iStack, Enchantments.PROTECTION);

                if (isChestplate(iStack)) {
                    System.out.println(i);
                    int score = getScore(iStack);
                    if (score > highestScore) {
                        highestScore = score;
                        bestSlot = i;
                    }
                }
            } else if(iStack.getItem() == Items.ELYTRA) bestSlot = i;
        }

        if (bestSlot != -1) {
            System.out.println(bestSlot);
            InvUtils.move().from(bestSlot).toArmor(2);
            InvUtils.click().to(bestSlot);
        }
        this.toggle();
    }

    boolean isChestplate (ItemStack iStack) {
        return iStack.getItem() == Items.NETHERITE_CHESTPLATE || iStack.getItem() == Items.DIAMOND_CHESTPLATE ||
            iStack.getItem() == Items.IRON_CHESTPLATE || iStack.getItem() == Items.CHAINMAIL_CHESTPLATE ||
            iStack.getItem() == Items.GOLDEN_CHESTPLATE || iStack.getItem() == Items.LEATHER_CHESTPLATE;
    }

    int getScore(ItemStack itemStack) {
        if (itemStack.isEmpty()) return 0;

        // Score calculated based on enchantments, protection and toughness
        int score = 0;

        RegistryKey<Enchantment> protection = Enchantments.PROTECTION;

        score += 3 * Utils.getEnchantmentLevel(itemStack, protection);
        score += Utils.getEnchantmentLevel(itemStack, Enchantments.PROTECTION);
        score += Utils.getEnchantmentLevel(itemStack, Enchantments.BLAST_PROTECTION);
        score += Utils.getEnchantmentLevel(itemStack, Enchantments.FIRE_PROTECTION);
        score += Utils.getEnchantmentLevel(itemStack, Enchantments.PROJECTILE_PROTECTION);
        score += Utils.getEnchantmentLevel(itemStack, Enchantments.UNBREAKING);
        score += 2 * Utils.getEnchantmentLevel(itemStack, Enchantments.MENDING);

        if(itemStack.getItem() instanceof ArmorItem) score += (int) (((ArmorItem) itemStack.getItem()).getToughness() * 2);

        return score;
    }
}
