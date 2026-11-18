package com.lutils.modules;

import com.lutils.LUtils;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class SignNotify extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> ignoreList = sgGeneral.add(new StringListSetting.Builder()
        .name("ignore-list")
        .description("Signs containing any of these texts are ignored.")
        .build()
    );

    private final Setting<Boolean> ignoreEmpty = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-empty")
        .description("Ignores signs with no text on either side.")
        .defaultValue(true)
        .build()
    );

    public SignNotify() {
        super(LUtils.CATEGORY, "sign-notify", "Notifies you about signs found in newly loaded chunks.");
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        for (BlockEntity blockEntity : event.chunk().getBlockEntities().values()) {
            if (!(blockEntity instanceof SignBlockEntity sign)) continue;

            String front = getText(sign.getFrontText());
            String back = getText(sign.getBackText());

            if (isIgnored(front, back)) continue;

            BlockPos pos = sign.getPos();
            info("Sign at (%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
            info("Front: %s", front.isEmpty() ? "(empty)" : front);
            info("Back: %s", back.isEmpty() ? "(empty)" : back);
        }
    }

    private boolean isIgnored(String front, String back) {
        if (ignoreEmpty.get() && front.isEmpty() && back.isEmpty()) return true;

        for (String ignored : ignoreList.get()) {
            if (ignored.isEmpty()) continue;
            if (front.contains(ignored) || back.contains(ignored)) return true;
        }

        return false;
    }

    private String getText(SignText signText) {
        StringBuilder text = new StringBuilder();

        for (Text line : signText.getMessages(false)) {
            String lineText = line.getString().trim();
            if (lineText.isEmpty()) continue;

            if (!text.isEmpty()) text.append(' ');
            text.append(lineText);
        }

        return text.toString();
    }
}
