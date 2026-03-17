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
import net.minecraft.util.math.MathHelper;
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
        .name("control speed")
        .description("look at name")
        .defaultValue(5d)
        .sliderRange(0d, 4d)
        .visible(() -> hMode.get() == hModes.CONTROL)
        .build()
    );

    private final Setting<Double> packetSpeed = sgHorizontal.add(new DoubleSetting.Builder()
        .name("packet speed")
        .description("look at name")
        .defaultValue(0.15d)
        .sliderRange(0d, 2d)
        .visible(() -> hMode.get() == hModes.PACKET)
        .build()
    );

    private final Setting<Double> accel = sgHorizontal.add(new DoubleSetting.Builder()
        .name("acceleration")
        .description("look at name")
        .defaultValue(3d)
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

    private final Setting<Double> uControlSpeed = sgUp.add(new DoubleSetting.Builder()
        .name("speed")
        .defaultValue(5d)
        .sliderRange(0d, 4d)
        .visible(() -> uMode.get() == uModes.CONTROL || uMode.get() == uModes.GLIDE)
        .build()
    );

    private final Setting<Double> uControlPitch = sgUp.add(new DoubleSetting.Builder()
        .name("pitch")
        .defaultValue(45d)
        .sliderRange(0d, 90d)
        .visible(() -> uMode.get() == uModes.CONTROL || uMode.get() == uModes.GLIDE)
        .build()
    );

    private final Setting<Integer> upTimer = sgUp.add(new IntSetting.Builder()
        .name("Up Timer")
        .defaultValue(10)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Integer> uBoostInterval = sgUp.add(new IntSetting.Builder()
        .name("Boost Interval")
        .defaultValue(50)
        .sliderRange(10, 100)
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
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<Double> afkSpeed = sgAfk.add(new DoubleSetting.Builder()
        .name("afk speed")
        .defaultValue(0.05)
        .sliderRange(0,2)
        .build()
    );

    private final Setting<Double> afkMinSpeed = sgAfk.add(new DoubleSetting.Builder()
        .name("min afk speed threshold")
        .defaultValue(0.05)
        .sliderRange(0,2)
        .build()
    );

    private final Setting<Double> afkAngle = sgAfk.add(new DoubleSetting.Builder()
        .name("afk angle")
        .defaultValue(3)
        .sliderRange(0, 180)
        .build()
    );

    private enum hModes {
        PACKET,
        CONTROL
    }

    private enum uModes {
        CONTROL,
        GLIDE
    }

    private int afkTick = 0;
    private double currentSpeed = 0;
    private double upTick = 0;
    private Vec3d oldVelocity;

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
        mc.player.getAbilities().flying = true;
        mc.player.getAbilities().allowFlying = true;
        mc.player.getAbilities().setFlySpeed((float) (double) packetSpeed.get());
        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
    }


    public void onDeactivate() {
        mc.player.getAbilities().flying = false;
        mc.player.getAbilities().allowFlying = false;
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
            mc.player.stopFallFlying();
            mc.player.getAbilities().flying = false;
            return;
        }

        if(!mc.options.jumpKey.isPressed() || (mc.options.sneakKey.isPressed() && mc.options.jumpKey.isPressed())) {
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
            oldVelocity = Vec3d.ZERO;
            if(mc.player.isOnGround() && !onGround.get()) return;
            switch (hMode.get()) {
                case PACKET -> {
                    mc.player.stopFallFlying();
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
        if(!mc.options.jumpKey.isPressed() || (mc.options.sneakKey.isPressed() && mc.options.jumpKey.isPressed())) {
            upTick = 0;

            switch (hMode.get()) {
                case CONTROL -> {
                    if(!mc.player.isFallFlying()) return;

                    if (getInputDirection().length() == 0) {
                        currentSpeed = 0;
                        if (mc.options.sneakKey.isPressed()) {
                            mc.player.setVelocity(0, -0.5, 0);
                            ((IVec3d) event.movement).set(0,-0.5,0);
                        } else {
                            mc.player.setVelocity(Vec3d.ZERO);
                            ((IVec3d) event.movement).set(0, 0, 0);
                        }
                        return;
                    }
                    currentSpeed += accel.get();
                    if (currentSpeed > controlSpeed.get()) currentSpeed = controlSpeed.get();
                    Vec3d mVec = getInputDirection().multiply(currentSpeed).add(0, mc.options.sneakKey.isPressed() ? -1 : 0, 0);
                    ((IVec3d) event.movement).set(mVec.x, mVec.y, mVec.z);
                }
            }
        } else {
            switch (uMode.get()) {
                case CONTROL -> {
                    if (event.movement.y < 0 || upTick > 0) {
                        Vec3d movementDir = new Vec3d(0, 0, 1).rotateY(-(float) Math.toRadians(mc.player.getYaw()));
                        Vec3d mVec = Vec3d.ZERO;
                        mc.player.getAbilities().allowFlying = false;
                        mc.player.getAbilities().flying = false;
                        if (!mc.player.isFallFlying()) {
                            mc.player.startFallFlying();
                            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                        }

                        if (upTick < upTimer.get()) {
                            ((IVec3d)event.movement).set(0,0,0);
                            upTick++;
                            return;
                        }

                        if (getInputDirection().length() != 0) movementDir = getInputDirection();
                        if (upTick - upTimer.get() > 1 && (upTick - upTimer.get()) % uBoostInterval.get() < 5) {
                            currentSpeed += accel.get();
                            if (currentSpeed > uControlSpeed.get()) currentSpeed = uControlSpeed.get();
                            mVec = movementDir.multiply(currentSpeed);
                            oldVelocity = new Vec3d(0,0,1).multiply(mVec.horizontalLength()).add(0,mVec.y,0);
                        } else {
                            mVec = calcGlideUpVel(oldVelocity, (float) -uControlPitch.get(), new Vec3d(0,0,1));
                            oldVelocity = mVec;
                        }

                        mVec = movementDir.multiply(mVec.horizontalLength()).add(0,mVec.y,0);
                        if (getInputDirection().length() == 0 && upTick % 2 == 0)  mVec = mVec.rotateY((float) Math.toRadians(180));

                        ((IVec3d) event.movement).set(mVec.x, mVec.y, mVec.z);

                        upTick++;
                    }
                }
                case GLIDE -> {
                    Vec3d movementDir = new Vec3d(0, 0, 1).rotateY(-(float) Math.toRadians(mc.player.getYaw()));
                    mc.player.getAbilities().allowFlying = false;
                    mc.player.getAbilities().flying = false;
                    if (!mc.player.isFallFlying()) {
                        mc.player.isFallFlying();
                        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    }

                    if (upTick < upTimer.get()) {
                        ((IVec3d)event.movement).set(0,0,0);
                        upTick++;
                        return;
                    }

                    if (upTick - upTimer.get() > 1 && (upTick - upTimer.get()) % uBoostInterval.get() < 10) {
                        currentSpeed += accel.get();
                        if (currentSpeed > uControlSpeed.get()) currentSpeed = uControlSpeed.get();
                        Vec3d mVec = movementDir.multiply(currentSpeed).add(0, mc.options.sneakKey.isPressed() ? -1 : 0, 0);
                        ((IVec3d) event.movement).set(mVec.x, 0, mVec.z);
                    }

                    upTick++;
                }
            }
        }
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!mc.options.jumpKey.isPressed()) {
            switch (hMode.get()) {
                case PACKET -> {
                    if (event.packet instanceof EntityTrackerUpdateS2CPacket && ((EntityTrackerUpdateS2CPacket) event.packet).id() == mc.player.getId()) {
                        event.cancel();
                    }
                }
            }
        }
    }

    private Vec3d calcGlideUpVel(Vec3d oldVelocity, float pitch, Vec3d inputDir) {
        Vec3d lookVec = inputDir.add(0, Math.cos(Math.toRadians(pitch)),0).normalize();
        System.out.println(lookVec);
        System.out.println(getInputDirection());
        System.out.println("\n");
        float piitch = pitch * (float) (Math.PI / 180.0);
        double d = lookVec.horizontalLength();
        double e = oldVelocity.horizontalLength();
        double g = mc.player.getFinalGravity();
        double h = MathHelper.square(Math.cos(piitch));
        oldVelocity = oldVelocity.add(0.0, g * (-1.0 + h * 0.75), 0.0);
        if (oldVelocity.y < 0.0 && d > 0.0) {
            double i = oldVelocity.y * -0.1 * h;
            oldVelocity = oldVelocity.add(lookVec.x * i / d, i, lookVec.z * i / d);
        }

        if (piitch < 0.0F && d > 0.0) {
            double i = e * -MathHelper.sin(piitch) * 0.04;
            oldVelocity = oldVelocity.add(-lookVec.x * i / d, i * 3.2, -lookVec.z * i / d);
        }

        if (d > 0.0) {
            oldVelocity = oldVelocity.add((lookVec.x / d * e - oldVelocity.x) * 0.1, 0.0, (lookVec.z / d * e - oldVelocity.z) * 0.1);
        }

        return oldVelocity.multiply(0.99F, 0.98F, 0.99F);
    }
}
