package com.lutils.modules;

import com.lutils.LUtils;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EntityPose;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.util.math.Vec3d;

public class Eloadra extends Module {
    private final SettingGroup sgHorizontal = this.settings.createGroup("Horizontal");
    private final SettingGroup sgUp = this.settings.createGroup("Up");
    private final SettingGroup sgAfk = this.settings.createGroup("Anti Afk");

    private final Setting<hModes> hMode = sgHorizontal.add(new EnumSetting.Builder<hModes>()
        .name("Horizontal Mode")
        .defaultValue(hModes.PACKET)
        .build()
    );

    private final Setting<Double> controlSpeed = sgHorizontal.add(new DoubleSetting.Builder()
        .name("speed")
        .description("look at name")
        .defaultValue(5d)
        .range(0d, 4d)
        .sliderRange(0d, 4d)
        .visible(() -> hMode.get() == hModes.CONTROL)
        .build()
    );

    private final Setting<Double> packetSpeed = sgHorizontal.add(new DoubleSetting.Builder()
        .name("speed")
        .description("look at name")
        .defaultValue(0.36d)
        .range(0d, 3.0d)
        .sliderRange(0d, 2d)
        .visible(() -> hMode.get() == hModes.PACKET)
        .build()
    );

    private final Setting<Double> accel = sgHorizontal.add(new DoubleSetting.Builder()
        .name("acceleration")
        .description("look at name")
        .defaultValue(3d)
        .range(0d, 10d)
        .sliderRange(0d, 10d)
        .build()
    );

    private final Setting<Boolean> onGround = sgHorizontal.add(new BoolSetting.Builder()
        .name("On Ground")
        .description("Whether to do all the efly stuff on ground.")
        .defaultValue(false)
        .build()
    );

    private final Setting<uModes> uMode = sgUp.add(new EnumSetting.Builder<uModes>()
        .name("Up Mode")
        .description("mode for flying upwards")
        .defaultValue(uModes.CONTROL)
        .build()
    );

    private  final Setting<Boolean> resetVel = sgUp.add(new BoolSetting.Builder()
        .name("reset velocity")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> uControlSpeed = sgUp.add(new DoubleSetting.Builder()
        .name("speed")
        .defaultValue(5d)
        .range(0d, 4d)
        .sliderRange(0d, 4d)
        .visible(() -> uMode.get() == uModes.CONTROL)
        .build()
    );

    private final Setting<Integer> upTimer = sgUp.add(new IntSetting.Builder()
        .name("Up Timer")
        .defaultValue(10)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Boolean> antiAfk = sgAfk.add(new BoolSetting.Builder()
        .name("Anti Afk")
        .build()
    );

    private final Setting<Integer> afkTimer = sgAfk.add(new IntSetting.Builder()
        .name("afk timer")
        .description("How long to wait before anti afk kicks in. (ticks)")
        .defaultValue(20)
        .range(0,200)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<Double> afkSpeed = sgAfk.add(new DoubleSetting.Builder()
        .name("afk speed")
        .defaultValue(0.05)
        .range(0,2)
        .sliderRange(0,2)
        .build()
    );

    private final Setting<Double> afkMinSpeed = sgAfk.add(new DoubleSetting.Builder()
        .name("min afk speed threshold")
        .defaultValue(0.05)
        .range(0,2)
        .sliderRange(0,2)
        .build()
    );

    private final Setting<Double> afkAngle = sgAfk.add(new DoubleSetting.Builder()
        .name("afk angle")
        .defaultValue(3)
        .range(0, 180)
        .sliderRange(0, 180)
        .build()
    );

    private enum hModes {
        PACKET,
        CONTROL
    }

    private enum uModes {
        PACKET,
        CONTROL,
        GLIDE
    }

    private int afkTick = 0;
    private double currentSpeed = 0;
    private double pitch = 0;
    private double upTick = 0;
    private boolean moving;
    private float p;
    private double velocity;

    public Eloadra() {
        super(LUtils.CATEGORY, "Eloadra", "Elytra flight focused on QOL, made for ye olde frog.");
    }

    private Vec3d getInputDirection() {
        Vec3d dir = Vec3d.ZERO;

        if (mc.options.forwardKey.isPressed()) dir = dir.add(0, 0, 1);
        if (mc.options.backKey.isPressed()) dir = dir.add(0, 0, -1);
        if (mc.options.leftKey.isPressed()) dir = dir.add(1, 0, 0);
        if (mc.options.rightKey.isPressed()) dir = dir.add(-1, 0, 0);

        dir = dir.rotateY(-(float) Math.toRadians(mc.player.getYaw()));
        dir = dir.normalize();
        return dir;
    }

    private void postTickPacket() {
        currentSpeed = 0;
        mc.player.stopGliding();
        mc.player.getAbilities().flying = true;
        mc.player.getAbilities().allowFlying = true;
        mc.player.getAbilities().setFlySpeed((float) (double) packetSpeed.get());
        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
    }


    public void onDeactivate() {
        mc.player.getAbilities().flying = false;
        mc.player.getAbilities().allowFlying = false;
        mc.player.getAbilities().setFlySpeed(1);
        mc.player.stopGliding();
        afkTick = 0;
    }

    @EventHandler
    private void onPostTick(TickEvent.Post event) {
        if (antiAfk.get() && !mc.player.isOnGround()) {
            if(getInputDirection().length() == 0 && !mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed() && (mc.player.getVelocity().length() <= afkMinSpeed.get() || afkTick > 0)) afkTick++;
            else afkTick = 0;

            if (afkTick > afkTimer.get()) {
                mc.player.setVelocity(
                    Math.cos(Math.toRadians((afkTick%360) * afkAngle.get())) * afkSpeed.get(),
                    0,
                    Math.sin(Math.toRadians((afkTick%360) * afkAngle.get())) * afkSpeed.get()
                );
            }
        }

        if(mc.player.isOnGround() && !onGround.get()) {
            mc.player.stopGliding();
            mc.player.getAbilities().flying = false;
            return;
        }

        if(!mc.options.jumpKey.isPressed()) {
            switch (hMode.get()) {
                case PACKET -> {
                    postTickPacket();
                }
            }
        }
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        if(!mc.options.jumpKey.isPressed()) {
            if(mc.player.isOnGround() && !onGround.get()) return;
            switch (hMode.get()) {
                case PACKET -> {
                    mc.player.setPose(EntityPose.STANDING);
                }
            }
        } else {
            mc.player.getAbilities().allowFlying = false;
            mc.player.getAbilities().flying = false;
        }
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        if(!mc.options.jumpKey.isPressed()) {
            upTick = 0;
            pitch = 0;

            switch (hMode.get()) {
                case CONTROL -> {
                    if(!mc.player.isGliding()) return;

                    if (getInputDirection().length() == 0) {
                        currentSpeed = 0;
                        if (mc.options.sneakKey.isPressed()) {
                            mc.player.setVelocity(Vec3d.Z.multiply(-0.5));
                            ((IVec3d) event.movement).meteor$set(Vec3d.Y.multiply(-0.5));
                        } else {
                            mc.player.setVelocity(Vec3d.ZERO);
                            ((IVec3d) event.movement).meteor$set(Vec3d.ZERO);
                        }
                        return;
                    }
                    currentSpeed += accel.get();
                    if (currentSpeed > controlSpeed.get()) currentSpeed = controlSpeed.get();
                    ((IVec3d) event.movement).meteor$set(getInputDirection().multiply(currentSpeed).add(0, mc.options.sneakKey.isPressed() ? -1 : 0, 0));
                    pitch = -0.2;
                }
            }
        } else {
            switch (uMode.get()) {
                case CONTROL -> {
                    mc.player.getAbilities().allowFlying = false;
                    mc.player.getAbilities().flying = false;
                    upTick++;
                    if (mc.player.fallDistance > 0 && !mc.player.isGliding() && mc.player.getVelocity().y < 0) mc.player.startGliding();
                    if (!mc.player.isGliding()) {return;}
                    pitch = 0;

                    boolean movingUp = false;

                    if (!mc.options.sneakKey.isPressed() && upTick > upTimer.get() && velocity > uControlSpeed.get() * 0.4) {
                        p = (float) Math.min(p + 0.1 * (1 - p) * (1 - p) * (1 - p), 1f);

                        pitch = Math.max(Math.max(p, 0) * -90, -90);

                        movingUp = true;
                        moving = false;
                    } else {
                        velocity = uControlSpeed.get();
                        p = -0.2f;
                    }

                    velocity = moving ? uControlSpeed.get() : Math.min(velocity + Math.sin(Math.toRadians(pitch)) * 0.08, uControlSpeed.get());

                    Vec3d movementDir = getInputDirection();
                    if (getInputDirection().length() == 0) {
                        movementDir = Vec3d.Z.rotateY(-(float) Math.toRadians(mc.player.getYaw()));
                        if(upTick % 2 == 0) movementDir = movementDir.rotateY((float) Math.toRadians(180));
                    }
                    if (upTick <= upTimer.get()) {
                        movementDir = Vec3d.ZERO;
                        mc.player.setVelocity(Vec3d.ZERO);
                    }


                    double x = moving && !movingUp ? movementDir.x * uControlSpeed.get() : movingUp ? velocity * Math.cos(Math.toRadians(pitch)) * movementDir.x : 0;
                    double y = pitch < 0 ? velocity * 1.2 * -Math.sin(Math.toRadians(pitch)) * velocity : 0;
                    double z = moving && !movingUp ? movementDir.z * uControlSpeed.get() : movingUp ? velocity * Math.cos(Math.toRadians(pitch)) * movementDir.z : 0;

                    y *= Math.abs(Math.sin(Math.toRadians(movingUp ? pitch : mc.player.getPitch())));

                    ((IVec3d) event.movement).meteor$set(x, y, z);
                    if (resetVel.get()) {
                        mc.player.setVelocity(0, 0, 0);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {

        switch (hMode.get()) {
            case PACKET -> {
                if (event.packet instanceof EntityTrackerUpdateS2CPacket && ((EntityTrackerUpdateS2CPacket) event.packet).id() == mc.player.getId() && !mc.options.jumpKey.isPressed()) {
                    event.cancel();
                }
            }
        }
    }

    private void updateControlMovement() {
        float yaw = mc.player.getYaw();

        float forward = mc.player.input.getMovementInput().y;
        float sideways = mc.player.input.getMovementInput().x;

        if (forward > 0) {
            moving = true;
            yaw += sideways > 0 ? -45 : sideways < 0 ? 45 : 0;
        } else if (forward < 0) {
            moving = true;
            yaw += sideways > 0 ? -135 : sideways < 0 ? 135 : 180;
        } else {
            moving = sideways != 0;
            yaw += sideways > 0 ? -90 : sideways < 0 ? 90 : 0;
        }
    }
}
