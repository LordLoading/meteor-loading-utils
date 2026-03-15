package com.lutils.modules;

import com.lutils.LUtils;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Xray;
import meteordevelopment.meteorclient.systems.modules.world.InfinityMiner;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.*;
import net.minecraft.registry.tag.BlockTags;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Predicate;

public class AutoDrool extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWhitelist = settings.createGroup("Whitelist");

    private final Setting<EnchantPreference> prefer = sgGeneral.add(new EnumSetting.Builder<EnchantPreference>()
        .name("prefer")
        .description("Either to prefer Silk Touch, Fortune, or none.")
        .defaultValue(EnchantPreference.Fortune)
        .build()
    );

    private final Setting<Boolean> silkTouchForEnderChest = sgGeneral.add(new BoolSetting.Builder()
        .name("silk-touch-for-ender-chest")
        .description("Mines Ender Chests only with the Silk Touch enchantment.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fortuneForOresCrops = sgGeneral.add(new BoolSetting.Builder()
        .name("fortune-for-ores-and-crops")
        .description("Mines Ores and crops only with the Fortune enchantment.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> antiBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-break")
        .description("Stops you from breaking your tool.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> breakDurability = sgGeneral.add(new IntSetting.Builder()
        .name("anti-break-percentage")
        .description("The durability percentage to stop using a tool.")
        .defaultValue(10)
        .range(1, 100)
        .sliderRange(1, 100)
        .visible(antiBreak::get)
        .build()
    );

    private final Setting<Boolean> switchBack = sgGeneral.add(new BoolSetting.Builder()
        .name("switch-back")
        .description("Switches your hand to whatever was selected when releasing your attack key.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> switchDelay = sgGeneral.add((new IntSetting.Builder()
        .name("switch-delay")
        .description("Delay in ticks before switching tools.")
        .defaultValue(0)
        .build()
    ));

    private final Setting<ListMode> listMode = sgWhitelist.add(new EnumSetting.Builder<ListMode>()
        .name("list-mode")
        .description("Selection mode.")
        .defaultValue(ListMode.Blacklist)
        .build()
    );

    private final Setting<List<Item>> whitelist = sgWhitelist.add(new ItemListSetting.Builder()
        .name("whitelist")
        .description("The tools you want to use.")
        .visible(() -> listMode.get() == ListMode.Whitelist)
        .filter(AutoDrool::isTool)
        .build()
    );

    private final Setting<List<Item>> blacklist = sgWhitelist.add(new ItemListSetting.Builder()
        .name("blacklist")
        .description("The tools you don't want to use.")
        .visible(() -> listMode.get() == ListMode.Blacklist)
        .filter(AutoDrool::isTool)
        .build()
    );

    private boolean wasPressed;
    private boolean shouldSwitch;
    private int ticks;
    private int bestSlot;

    private final List<SwapInfo> swapHistory = new ArrayList<>();

    private static class SwapInfo {
        final int originalToolSlot;
        final int originalHotbarSlot;
        final ItemStack originalHotbarItem;
        final long swapTime;

        SwapInfo(int originalToolSlot, int originalHotbarSlot, ItemStack originalHotbarItem) {
            this.originalToolSlot = originalToolSlot;
            this.originalHotbarSlot = originalHotbarSlot;
            this.originalHotbarItem = originalHotbarItem != null ? originalHotbarItem.copy() : null;
            this.swapTime = System.currentTimeMillis();
        }
    }

    public AutoDrool() {
        super(LUtils.CATEGORY, "auto-drool", "Basically just the regular meteor autotool but it also takes tools from your inventory.");
    }

    @Override
    public void onDeactivate() {
        swapHistory.clear();
        super.onDeactivate();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (Modules.get().isActive(InfinityMiner.class)) return;

        if (switchBack.get() && !mc.options.attackKey.isPressed() && wasPressed && InvUtils.previousSlot != -1) {
            InvUtils.swapBack();
            wasPressed = false;
            return;
        }

        if (!swapHistory.isEmpty() && !mc.options.attackKey.isPressed() && wasPressed) {
            restoreAllSwaps();
            wasPressed = false;
            return;
        }

        cleanupOldSwaps();

        if (ticks <= 0 && shouldSwitch && bestSlot != -1) {
            if (bestSlot < 9) {
                InvUtils.swap(bestSlot, switchBack.get());
            } else {
                assert mc.player != null;
                int hotbarSlot = mc.player.getInventory().selectedSlot;
                ItemStack originalItem = mc.player.getInventory().getStack(hotbarSlot).copy();

                swapHistory.add(new SwapInfo(bestSlot, hotbarSlot, originalItem));

                InvUtils.move().from(bestSlot).toHotbar(hotbarSlot);
            }
            shouldSwitch = false;
        } else {
            ticks--;
        }

        wasPressed = mc.options.attackKey.isPressed();
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (Modules.get().isActive(InfinityMiner.class)) return;

        assert mc.world != null;
        BlockState blockState = mc.world.getBlockState(event.blockPos);
        if (!BlockUtils.canBreak(event.blockPos, blockState)) return;

        assert mc.player != null;
        ItemStack currentStack = mc.player.getMainHandStack();

        double bestScore = -1;
        bestSlot = -1;

        for (int i = 0; i < 36; i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);

            if (listMode.get() == ListMode.Whitelist && !whitelist.get().contains(itemStack.getItem())) continue;
            if (listMode.get() == ListMode.Blacklist && blacklist.get().contains(itemStack.getItem())) continue;

            double score = getScore(itemStack, blockState, silkTouchForEnderChest.get(), fortuneForOresCrops.get(), prefer.get(), itemStack2 -> !shouldStopUsing(itemStack2));
            if (score < 0) continue;

            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        if ((bestSlot != -1 && (bestScore > getScore(currentStack, blockState, silkTouchForEnderChest.get(), fortuneForOresCrops.get(), prefer.get(), itemStack -> !shouldStopUsing(itemStack))) || shouldStopUsing(currentStack) || !isTool(currentStack))) {
            ticks = switchDelay.get();

            if (ticks == 0) {
                if (bestSlot < 9) {
                    InvUtils.swap(bestSlot, true);
                } else {
                    int hotbarSlot = mc.player.getInventory().selectedSlot;
                    ItemStack originalItem = mc.player.getInventory().getStack(hotbarSlot).copy();

                    swapHistory.add(new SwapInfo(bestSlot, hotbarSlot, originalItem));

                    InvUtils.move().from(bestSlot).toHotbar(hotbarSlot);
                }
            } else {
                shouldSwitch = true;
            }
        }

        currentStack = mc.player.getMainHandStack();

        if (shouldStopUsing(currentStack) && isTool(currentStack)) {
            mc.options.attackKey.setPressed(false);
            event.cancel();
        }
    }

    private boolean shouldStopUsing(ItemStack itemStack) {
        return antiBreak.get() && (itemStack.getMaxDamage() - itemStack.getDamage()) < (itemStack.getMaxDamage() * breakDurability.get() / 100);
    }

    private void restoreAllSwaps() {
        for (int i = swapHistory.size() - 1; i >= 0; i--) {
            SwapInfo swap = swapHistory.get(i);
            restoreSwap(swap);
        }
        swapHistory.clear();
    }

    private void restoreSwap(SwapInfo swap) {
        if (swap.originalHotbarItem != null && !swap.originalHotbarItem.isEmpty()) {
            mc.player.getInventory().setStack(swap.originalHotbarSlot, swap.originalHotbarItem);
            InvUtils.move().from(swap.originalHotbarSlot).to(swap.originalToolSlot);
        } else {
            InvUtils.move().from(swap.originalHotbarSlot).to(swap.originalToolSlot);
        }
    }

    private void cleanupOldSwaps() {
        long currentTime = System.currentTimeMillis();
        swapHistory.removeIf(swap -> currentTime - swap.swapTime > 5000); // Remove swaps older than 5 seconds
    }

    public static double getScore(ItemStack itemStack, BlockState state, boolean silkTouchEnderChest, boolean fortuneOre, EnchantPreference enchantPreference, Predicate<ItemStack> good) {
        if (!good.test(itemStack) || !isTool(itemStack)) return -1;
        if (!itemStack.isSuitableFor(state) && !(itemStack.getItem() instanceof SwordItem && (state.getBlock() instanceof BambooBlock || state.getBlock() instanceof BambooShootBlock)) && !(itemStack.getItem() instanceof ShearsItem && state.getBlock() instanceof LeavesBlock || state.isIn(BlockTags.WOOL))) return -1;

        if (silkTouchEnderChest
            && state.getBlock() == Blocks.ENDER_CHEST
            && !Utils.hasEnchantments(itemStack, Enchantments.SILK_TOUCH)) {
            return -1;
        }

        if (fortuneOre
            && isFortunable(state.getBlock())
            && !Utils.hasEnchantments(itemStack, Enchantments.FORTUNE)) {
            return -1;
        }

        double score = 0;

        score += itemStack.getMiningSpeedMultiplier(state) * 1000;
        score += Utils.getEnchantmentLevel(itemStack, Enchantments.UNBREAKING);
        score += Utils.getEnchantmentLevel(itemStack, Enchantments.EFFICIENCY);
        score += Utils.getEnchantmentLevel(itemStack, Enchantments.MENDING);

        if (enchantPreference == EnchantPreference.Fortune) score += Utils.getEnchantmentLevel(itemStack, Enchantments.FORTUNE);
        if (enchantPreference == EnchantPreference.SilkTouch) score += Utils.getEnchantmentLevel(itemStack, Enchantments.SILK_TOUCH);

        if (itemStack.getItem() instanceof SwordItem item && (state.getBlock() instanceof BambooBlock || state.getBlock() instanceof BambooShootBlock))
            score += 9000 + (item.getComponents().get(DataComponentTypes.TOOL).getSpeed(state) * 1000);

        return score;
    }

    public static boolean isTool(Item item) {
        return item instanceof MiningToolItem || item instanceof ShearsItem;
    }

    public static boolean isTool(ItemStack itemStack) {
        return isTool(itemStack.getItem());
    }

    private static boolean isFortunable(Block block) {
        if (block == Blocks.ANCIENT_DEBRIS) return false;
        return Xray.ORES.contains(block) || block instanceof CropBlock;
    }

    public enum EnchantPreference {
        None,
        Fortune,
        SilkTouch
    }

    public enum ListMode {
        Blacklist,
        Whitelist
    }
}
