package com.lutils.modules;

import com.lutils.LUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

public class Explore extends Module {
    private final SettingGroup sgPattern = this.settings.createGroup("Pattern");

    private final Setting<Integer> spiralGap = sgPattern.add(new IntSetting.Builder()
        .name("Spiral Gap")
        .description("Number of chunks between spiral steps")
        .defaultValue(4)
        .range(1, 100)
        .sliderRange(1, 100)
        .build()
    );

    private ChunkPos startChunk;
    private ChunkPos targetChunk;
    private int spiralX = 0;
    private int spiralZ = 0;
    private int steps = 0;
    private int maxSteps = 1;
    private int direction = 0; // 0=east, 1=south, 2=west, 3=north
    private int ticksWaitRemaining = 0;
    private double lastDistance = Double.MAX_VALUE;

    public Explore() {
        super(LUtils.CATEGORY, "Explore", "Explore the world in effective patterns from a starting point.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;

        startChunk = new ChunkPos(mc.player.getBlockPos());
        spiralX = 0;
        spiralZ = 0;
        steps = 0;
        maxSteps = 1;
        direction = 0;

        getNextTarget();
        setDirectionToTarget();
    }

    @Override
    public void onDeactivate() {
        resetMovement();
    }

    private void getNextTarget() {
        int gap = spiralGap.get();

        switch (direction) {
            case 0 -> spiralX += gap; // east
            case 1 -> spiralZ += gap; // south
            case 2 -> spiralX -= gap; // west
            case 3 -> spiralZ -= gap; // north
        }

        steps++;

        if (steps >= maxSteps) {
            steps = 0;
            direction = (direction + 1) % 4;

            if (direction % 2 == 0) {
                maxSteps++;
            }
        }

        targetChunk = new ChunkPos(startChunk.x + spiralX, startChunk.z + spiralZ);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || targetChunk == null) return;

        if (ticksWaitRemaining > 0) {
            mc.player.setVelocity(0, 0, 0);
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.isOnGround(), mc.player.horizontalCollision));
            setDirectionToTarget();
            ticksWaitRemaining--;
            return;
        }

        Vec3d playerPos = mc.player.getEyePos();
        Vec3d targetPos = getChunkCenter(targetChunk);

        Vec3d toTarget = targetPos.subtract(playerPos);
        double distance = toTarget.length();

        if (distance < 3 || distance > lastDistance) {
            resetMovement();
            getNextTarget();
            ticksWaitRemaining = 10;
            lastDistance = Double.MAX_VALUE;
            return;
        }

        lastDistance = distance;

        setDirectionToTarget();

        mc.options.forwardKey.setPressed(true);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
    }

    private void setDirectionToTarget() {
        if (mc.player == null || targetChunk == null) return;

        Vec3d playerPos = mc.player.getEyePos();
        Vec3d targetPos = getChunkCenter(targetChunk);

        Vec3d diff = targetPos.subtract(playerPos);

        float yaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));

        mc.player.setYaw(yaw);

        mc.player.setHeadYaw(yaw);
    }

    private Vec3d getChunkCenter(ChunkPos chunk) {
        double x = (chunk.x << 4) + 8;
        double z = (chunk.z << 4) + 8;
        double y = mc.player != null ? mc.player.getY() : 64;

        return new Vec3d(x, y, z);
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
