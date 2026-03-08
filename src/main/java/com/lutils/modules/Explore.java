package com.lutils.modules;

import com.lutils.LUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

public class Explore extends Module {
    private final SettingGroup sgPattern = this.settings.createGroup("Pattern");
    private final SettingGroup sgGeneral = this.settings.createGroup("General");

    private final Setting<Integer> spiralGap = sgPattern.add(new IntSetting.Builder()
        .name("Spiral Gap")
        .description("Number of blocks between spiral steps")
        .defaultValue(4)
        .range(1, 100)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Boolean> autoSprint = sgGeneral.add(new BoolSetting.Builder()
            .name("Auto Sprint")
            .defaultValue(true)
            .build()
    );

    private int step = 0;
    private Vec3d direction = new Vec3d(1, 0, 0);
    private Vec3d target;

    public Explore() {
        super(LUtils.CATEGORY, "Explore", "Explore the world in effective patterns from a starting point.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;
        step = 0;
        target = mc.player.getPos();
        direction = new Vec3d(1, 0, 0);

        getNextTarget();
        setDirectionToTarget(target);
    }

    @Override
    public void onDeactivate() {
        resetMovement();
    }

    private void getNextTarget() {
        int gap = spiralGap.get();

        direction = direction.rotateY((float) Math.toRadians(90));
        ((IVec3d) direction).meteor$set(Math.round(direction.x), 0, Math.round(direction.z));
        target = target.add(direction.multiply((step + 2) * gap));

        mc.player.setVelocity(Vec3d.ZERO);
        step++;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        if (direction.x == 1 && mc.player.getPos().x > target.x) getNextTarget();
        else if (direction.x == -1 && mc.player.getPos().x < target.x) getNextTarget();
        else if (direction.z == 1 && mc.player.getPos().z > target.z) getNextTarget();
        else if (direction.z == -1 && mc.player.getPos().z < target.z) getNextTarget();

        setDirectionToTarget(target);

        mc.options.forwardKey.setPressed(true);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        if(autoSprint.get()) mc.options.sprintKey.setPressed(true);
    }

    private void setDirectionToTarget(Vec3d targetPos) {
        if (mc.player == null) return;
        Vec3d playerPos = mc.player.getEyePos();

        Vec3d diff = targetPos.subtract(playerPos);

        float yaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));

        mc.player.setYaw(yaw);

        mc.player.setHeadYaw(yaw);
    }

    private void resetMovement() {
        if (mc.options != null) {
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
        }
    }
}
