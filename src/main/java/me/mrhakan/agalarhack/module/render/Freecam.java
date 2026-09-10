package me.mrhakan.agalarhack.module.render;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/**
 * Detaches the camera onto a client-only armor-stand entity while keeping the real
 * player anchored. Position packets are not spoofed or replaced.
 */
public class Freecam extends Module {
    private ArmorStand camera;
    private Entity previousCamera;
    private Vec3 playerAnchor;
    private Vec3 cameraTarget;
    private Vec3 cameraVelocity = Vec3.ZERO;

    public Freecam() {
        super("Freecam", Category.RENDER, "Detaches the camera with smooth accelerated movement without moving the real player");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("speed", 0.65, 0.05, 3.0, "Maximum camera movement speed per tick");
        addNumberSetting("sprintMultiplier", 2.5, 1.0, 8.0, "Maximum-speed multiplier while the sprint key is held");
        addNumberSetting("acceleration", 0.22, 0.01, 1.0, "How quickly camera velocity approaches movement input");
        addNumberSetting("deceleration", 0.30, 0.01, 1.0, "How quickly camera velocity eases to a stop after input is released");
        addNumberSetting("smoothing", 0.45, 0.05, 1.0, "Final position response; 1 follows the eased movement target immediately");
        addBooleanSetting("freezePlayer", true, "Keep the real player's position anchored while freecam is active");
        addBooleanSetting("bodyMarker", true, "Draw a marker around the real player body while the camera is detached");
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.level == null) {
            return;
        }
        previousCamera = mc.getCameraEntity();
        playerAnchor = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        cameraTarget = playerAnchor;
        cameraVelocity = Vec3.ZERO;
        camera = new ArmorStand(mc.level, mc.player.getX(), mc.player.getY(), mc.player.getZ());
        camera.setInvisible(true);
        camera.setNoGravity(true);
        camera.setYRot(mc.player.getYRot());
        camera.setXRot(mc.player.getXRot());
        mc.setCameraEntity(camera);
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.level == null || camera == null) {
            return;
        }

        camera.setYRot(mc.player.getYRot());
        camera.setXRot(mc.player.getXRot());

        if (getBooleanSetting("freezePlayer", true) && playerAnchor != null) {
            mc.player.setDeltaMovement(Vec3.ZERO);
            mc.player.setPos(playerAnchor.x, playerAnchor.y, playerAnchor.z);
        }

        double forward = (mc.options.keyUp.isDown() ? 1.0 : 0.0) - (mc.options.keyDown.isDown() ? 1.0 : 0.0);
        double strafe = (mc.options.keyRight.isDown() ? 1.0 : 0.0) - (mc.options.keyLeft.isDown() ? 1.0 : 0.0);
        double vertical = (mc.options.keyJump.isDown() ? 1.0 : 0.0) - (mc.options.keyShift.isDown() ? 1.0 : 0.0);

        double yaw = Math.toRadians(camera.getYRot());
        Vec3 input = new Vec3(
                -Math.sin(yaw) * forward + Math.cos(yaw) * strafe,
                vertical,
                Math.cos(yaw) * forward + Math.sin(yaw) * strafe);
        if (input.lengthSqr() > 1.0) {
            input = input.normalize();
        }

        double maxSpeed = getNumberSetting("speed", 0.65);
        if (mc.options.keySprint.isDown()) {
            maxSpeed *= getNumberSetting("sprintMultiplier", 2.5);
        }
        Vec3 desiredVelocity = input.scale(maxSpeed);
        boolean hasInput = input.lengthSqr() > 0.000001;
        double response = hasInput
                ? getNumberSetting("acceleration", 0.22)
                : getNumberSetting("deceleration", 0.30);
        cameraVelocity = cameraVelocity.lerp(desiredVelocity, response);
        if (!hasInput && cameraVelocity.lengthSqr() < 0.00001) {
            cameraVelocity = Vec3.ZERO;
        }

        Vec3 current = new Vec3(camera.getX(), camera.getY(), camera.getZ());
        if (cameraTarget == null) {
            cameraTarget = current;
        }
        cameraTarget = cameraTarget.add(cameraVelocity);
        double smoothing = getNumberSetting("smoothing", 0.45);
        Vec3 next = current.lerp(cameraTarget, smoothing);
        camera.setPos(next.x, next.y, next.z);
    }

    public Vec3 getBodyAnchor() {
        return playerAnchor;
    }

    public boolean shouldRenderBodyMarker() {
        return isToggled() && getBooleanSetting("bodyMarker", true) && playerAnchor != null;
    }

    public double getCurrentCameraSpeed() {
        return cameraVelocity.length();
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            mc.setCameraEntity(previousCamera != null ? previousCamera : mc.player);
        }
        camera = null;
        previousCamera = null;
        playerAnchor = null;
        cameraTarget = null;
        cameraVelocity = Vec3.ZERO;
    }

    @Override
    public void onDisconnect() {
        onDisable();
    }
}
