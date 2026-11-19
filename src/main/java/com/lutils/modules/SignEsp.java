package com.lutils.modules;

import com.lutils.LUtils;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

import java.util.List;

public class SignEsp extends Module {
    private static final Color BACKGROUND = new Color(0, 0, 0, 150);
    private static final double PADDING = 1;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> fade = sgGeneral.add(new BoolSetting.Builder()
        .name("fade")
        .description("Fades the text out with distance.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale")
        .description("The size of the text.")
        .defaultValue(1)
        .min(0.5)
        .sliderRange(0.5, 3)
        .build()
    );

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

    private final Vector3d pos = new Vector3d();
    private final Color textColor = new Color();
    private final Color backgroundColor = new Color();

    public SignEsp() {
        super(LUtils.CATEGORY, "sign-esp", "Renders sign contents through walls.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        TextRenderer textRenderer = TextRenderer.get();

        Renderer2D.COLOR.begin();
        textRenderer.begin(textScale.get());
        for (BlockEntity blockEntity : Utils.blockEntities()) {
            if (!(blockEntity instanceof SignBlockEntity sign)) continue;
            BlockPos blockPos = blockEntity.getPos();

            String front = getText(sign.getFrontText());
            String back = getText(sign.getBackText());
            if (isIgnored(front, back)) continue;

            double alpha = 1;
            if (fade.get()) {
                double distance = Math.sqrt(
                    mc.player.squaredDistanceTo(
                        blockPos.getX() + 0.5,
                        blockPos.getY() + 0.5,
                        blockPos.getZ() + 0.5
                    )
                );

                alpha = Math.max(0, Math.min(1, (distance - 10) / 5));
            }
            if (alpha <= 0) continue;

            textColor.set(255, 255, 255, (int) (255 * alpha));
            backgroundColor.set(BACKGROUND.r, BACKGROUND.g, BACKGROUND.b, (int) (BACKGROUND.a * alpha));

            pos.set(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
            if (!NametagUtils.to2D(pos, 1.0, false)) continue;

            StringBuilder label = new StringBuilder("front:");
            if (!front.isEmpty()) label.append('\n').append(front);
            label.append("\n\nback:");
            if (!back.isEmpty()) label.append('\n').append(back);

            String[] lines = label.toString().split("\n");
            double lineHeight = textRenderer.getHeight(true);

            double width = 0;
            for (String line : lines) width = Math.max(width, textRenderer.getWidth(line, true));

            Renderer2D.COLOR.quad(
                pos.x - PADDING,
                pos.y - PADDING,
                width + PADDING * 2,
                lineHeight * lines.length + PADDING * 2,
                backgroundColor);

            for (int i = 0; i < lines.length; i++) {
                textRenderer.render(lines[i], pos.x, pos.y + i * lineHeight, textColor, true);
            }
        }
        Renderer2D.COLOR.render();
        textRenderer.end();
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
            if (!text.isEmpty()) text.append('\n');
            text.append(lineText);
        }

        return text.toString();
    }
}
