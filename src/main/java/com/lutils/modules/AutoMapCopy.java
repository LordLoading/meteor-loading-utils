package com.lutils.modules;

import com.lutils.LUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.EmptyMapItem;
import net.minecraft.item.FilledMapItem;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class AutoMapCopy extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> copyLimit = sgGeneral.add((new IntSetting.Builder()
        .name("Copy Limit")
        .description("How many copies of each map the module aims for.")
        .range(2, 64)
        .sliderRange(2, 64)
        .defaultValue(2)
        .build()
    ));

    public AutoMapCopy() {
        super(LUtils.CATEGORY, "Auto Map Copy", "Automatically copies every map in your inventory");
    }

    public void onActivate() {
        if(!(mc.player.currentScreenHandler instanceof PlayerScreenHandler)) {
            mc.setScreen(new InventoryScreen(mc.player));
        }
    }

    public void onDeactivate() {
        InvUtils.shiftClick().slotId(1);
        InvUtils.shiftClick().slotId(2);
        InvUtils.shiftClick().slotId(3);
        InvUtils.shiftClick().slotId(4);

    }

    @EventHandler
    private void onTick(TickEvent.Post tickEvent) {
        if(!(mc.player.currentScreenHandler instanceof PlayerScreenHandler)) {
            toggle();
        }

        if (mc.player.currentScreenHandler.getSlot(0).getStack().getItem() instanceof FilledMapItem) {
            InvUtils.shiftClick().slotId(0);
        } else if (!(mc.player.currentScreenHandler.getSlot(1).getStack().getItem() instanceof EmptyMapItem)) {
            InvUtils.click().slot(InvUtils.find(stack -> stack.getItem() instanceof EmptyMapItem).slot());
            InvUtils.click().slotId(1);
        } else {
            boolean slotFound = false;
            for(int i = 0; i <= 35; i++) {
                if(mc.player.getInventory().getStack(i).getItem() instanceof FilledMapItem && mc.player.getInventory().getStack(i).getCount() < copyLimit.get()) {
                    InvUtils.click().slot(i);
                    mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId,
                        2,
                        1,
                        SlotActionType.PICKUP,
                        mc.player
                    );
                    InvUtils.click().slot(i);
                    slotFound = true;
                    break;
                }
            }
            if (!slotFound) {
                toggle();
            }
        }

    }
}
